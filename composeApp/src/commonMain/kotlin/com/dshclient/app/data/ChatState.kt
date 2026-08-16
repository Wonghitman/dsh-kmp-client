package com.dshclient.app.data

import com.dshclient.app.core.model.ContentBlock
import com.dshclient.app.core.model.ImageAttachmentRef
import com.dshclient.app.core.model.JobView
import com.dshclient.app.core.model.Message
import com.dshclient.app.core.model.QueueItem
import com.dshclient.app.core.model.SessionEvent
import com.dshclient.app.core.model.SurfaceFolder
import com.dshclient.app.core.model.deriveMessage
import com.dshclient.app.core.model.long
import com.dshclient.app.core.model.obj
import com.dshclient.app.core.model.parseChunkDelta
import com.dshclient.app.core.model.str
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement

/** 渲染用消息块 */
data class RenderedBlock(
    val type: String,        // text | reasoning | tool-call | tool-result | image
    val text: String? = null,
    val toolName: String? = null,
    val toolId: String? = null,
    val arguments: String? = null,
    val argumentsParsed: JsonElement? = null,
    val isError: Boolean = false,
    val image: ImageAttachmentRef? = null,
    val toolResultText: String? = null,
) {
    companion object {
        fun fromBlock(block: ContentBlock): RenderedBlock = when (block.type) {
            "text" -> RenderedBlock(type = "text", text = block.text)
            "reasoning" -> RenderedBlock(type = "reasoning", text = block.text)
            "tool-call" -> RenderedBlock(
                type = "tool-call",
                toolName = block.name,
                toolId = block.id,
                arguments = block.arguments,
                argumentsParsed = block.argumentsParsed,
            )
            "tool-result" -> RenderedBlock(
                type = "tool-result",
                toolId = block.toolCallId,
                isError = block.isError,
                toolResultText = block.content.joinToString("\n") { it.text ?: "" },
            )
            "image" -> RenderedBlock(
                type = "image",
                image = block.attachment,
            )
            else -> RenderedBlock(type = "text", text = block.text)
        }
    }
}

/** 渲染用消息 */
data class RenderedMessage(
    val seq: Long,
    val role: String,
    val sourceKind: String,
    val blocks: List<RenderedBlock>,
    val time: Long,
) {
    val isToolResult: Boolean get() = sourceKind == "tool"
    val textContent: String
        get() = blocks.filter { it.type == "text" }.joinToString("") { it.text ?: "" }
}

/** 进行中的流式块（assistant/chunk 累积） */
private data class PendingBlock(
    val blockType: String,   // text | reasoning | tool-call
    var text: String = "",
    var toolId: String? = null,
    var toolName: String? = null,
)

/**
 * 单个会话的聊天状态：事件缓冲 + surface fold + 流式渲染。
 *
 * 事件来源两种：历史分页（session.history）与实时流（session/event 帧）。
 * 分页加载更早页后需要整体重放（resetAndFold）。
 */
@OptIn(kotlin.time.ExperimentalTime::class)
class ChatState(
    val sessionId: String,
    private val scope: CoroutineScope,
) {
    private val _messages = MutableStateFlow<List<RenderedMessage>>(emptyList())
    val messages: StateFlow<List<RenderedMessage>> = _messages.asStateFlow()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val _hasMore = MutableStateFlow(false)
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()

    private val _queue = MutableStateFlow<List<QueueItem>>(emptyList())
    val queue: StateFlow<List<QueueItem>> = _queue.asStateFlow()

    private val _jobs = MutableStateFlow<List<JobView>>(emptyList())
    val jobs: StateFlow<List<JobView>> = _jobs.asStateFlow()

    private val _projections = MutableStateFlow<Map<String, JsonElement>>(emptyMap())
    val projections: StateFlow<Map<String, JsonElement>> = _projections.asStateFlow()

    private val _title = MutableStateFlow<String?>(null)
    val title: StateFlow<String?> = _title.asStateFlow()

    private val _currentTurn = MutableStateFlow<Long?>(null)
    val currentTurn: StateFlow<Long?> = _currentTurn.asStateFlow()

    private val _currentStep = MutableStateFlow<Long?>(null)
    val currentStep: StateFlow<Long?> = _currentStep.asStateFlow()

    /** 当前 open turn 的开始时间（Deep diving 计时） */
    private val _turnStartTime = MutableStateFlow<Long?>(null)
    val turnStartTime: StateFlow<Long?> = _turnStartTime.asStateFlow()

    /** 已加载事件（按 seq 升序） */
    private val events = mutableListOf<SessionEvent>()

    /** 事件/折叠状态互斥：历史分页协程与 mux 实时流协程并发注入 */
    private val mutex = kotlinx.coroutines.sync.Mutex()

    /** 全部已加载事件（轨迹视图） */
    suspend fun allEvents(): List<SessionEvent> = mutex.withLock { events.toList() }

    /** 已加载事件的最小 seq（分页 beforeSeq 用） */
    suspend fun minEventSeq(): Long? = mutex.withLock { events.firstOrNull()?.seq }

    /** 事件版本号：每次注入/折叠后自增，轨迹视图据此刷新 */
    private val _eventsVersion = MutableStateFlow(0)
    val eventsVersion: StateFlow<Int> = _eventsVersion.asStateFlow()
    private val bySeq = HashMap<Long, SessionEvent>()
    private val folder = SurfaceFolder()

    /** 进行中的块：turn/step 组合键 → index → PendingBlock */
    private val pendingBlocks = HashMap<String, HashMap<Int, PendingBlock>>()
    /** 当前流式 turn（无 final 消息的 turn 编号） */
    private var streamingTurn: Long? = null

    /** 流式合并重建任务：chunk 逐 token 高频到达，合并窗口内只重建一次 */
    private var pendingRebuild: Job? = null
    private var streamingDirty = false

    /** 重置（重新连接后清空重建） */
    suspend fun reset() = mutex.withLock {
        resetLocked()
    }

    private fun resetLocked() {
        pendingRebuild?.cancel()
        pendingRebuild = null
        streamingDirty = false
        events.clear()
        bySeq.clear()
        folder.resetAndFold(emptyList())
        pendingBlocks.clear()
        streamingTurn = null
        _messages.value = emptyList()
        _running.value = false
        _hasMore.value = false
        _queue.value = emptyList()
        _jobs.value = emptyList()
        _projections.value = emptyMap()
        _currentTurn.value = null
        _currentStep.value = null
        _turnStartTime.value = null
        _eventsVersion.value += 1
    }

    /** 注入事件：必须按 seq 升序连续（历史分页 + 实时流都满足） */
    suspend fun ingestEvents(newEvents: List<SessionEvent>) {
        mutex.withLock { ingestLocked(newEvents) }
    }

    private fun ingestLocked(newEvents: List<SessionEvent>) {
        if (newEvents.isEmpty()) return
        // 如果新事件 seq 全部小于已有最小 seq（加载更早页），整体重放
        val minNew = newEvents.minOf { it.seq }
        val minOld = events.firstOrNull()?.seq
        val replaceAll = minOld != null && minNew < minOld
        if (replaceAll) {
            // 分页返回的尾部可能与实时流已加载事件重叠：按 seq 去重后再合并
            val merged = (newEvents + events).sortedBy { it.seq }.distinctBy { it.seq }
            events.clear()
            bySeq.clear()
            for (e in merged) {
                events.add(e)
                bySeq[e.seq] = e
            }
            folder.resetAndFold(events)
            // 分页回填后流式块状态不可靠，清空 pending
            pendingBlocks.clear()
            streamingTurn = null
            // 全量重放后立即重建，避免与旧流式状态叠加
            streamingDirty = false
        } else {
            for (e in newEvents) {
                if (bySeq.containsKey(e.seq)) continue
                events.add(e)
                bySeq[e.seq] = e
                folder.foldOne(e)
            }
        }
        _eventsVersion.value += 1
        rebuild()
    }

    /** 流式事件合并：chunk 逐 token 高频到达时，延迟统一重建，避免每次全量 rebuild 占死锁 */
    private fun scheduleStreamingRebuild() {
        streamingDirty = true
        if (pendingRebuild?.isActive == true) return
        pendingRebuild = scope.launch {
            delay(120)
            mutex.withLock {
                streamingDirty = false
                _eventsVersion.value += 1
                rebuild()
            }
        }
    }

    /** 处理单个实时事件 */
    suspend fun onLiveEvent(event: SessionEvent) {
        mutex.withLock { onLiveEventLocked(event) }
    }

    private fun onLiveEventLocked(event: SessionEvent) {
        when (event.type) {
            "user/message", "assistant/message", "tool/result", "turn/start", "turn/end",
            "assistant/chunk", "tool/call", "step/start", "step/end", "command/run",
            "command/done", "session/title", "agent-preset/selected",
            -> Unit
            else -> Unit
        }
        if (event.type == "turn/start") {
            _running.value = true
        } else if (event.type == "turn/end") {
            _running.value = false
            // turn 结束：清掉该 turn 的 pending（final 消息已落地）
            val turn = event.data.long("turn")
            if (turn != null) {
                pendingBlocks.remove("t" + turn)
                if (streamingTurn == turn) streamingTurn = null
            }
        } else if (event.type == "turn/start") {
            event.data.long("turn")?.let { _currentTurn.value = it }
            _turnStartTime.value = event.time
        } else if (event.type == "turn/end") {
            _turnStartTime.value = null
        } else if (event.type == "step/start") {
            event.data.long("step")?.let { _currentStep.value = it }
        } else if (event.type == "assistant/chunk") {
            val delta = event.parseChunkDelta() ?: return
            val turn = event.data.long("turn")
            if (turn == null) return
            streamingTurn = turn
            val key = "t" + turn
            val turnBlocks = pendingBlocks.getOrPut(key) { HashMap() }
            when (delta.type) {
                "text-delta" -> {
                    val b = turnBlocks.getOrPut(delta.index) { PendingBlock("text") }
                    b.text += (delta.text ?: "")
                }
                "reasoning-delta" -> {
                    val b = turnBlocks.getOrPut(delta.index) { PendingBlock("reasoning") }
                    b.text += (delta.text ?: "")
                }
                "tool-call-delta" -> {
                    val b = turnBlocks.getOrPut(delta.index) { PendingBlock("tool-call") }
                    if (delta.id != null) b.toolId = delta.id
                    if (delta.name != null) b.toolName = delta.name
                    b.text += (delta.argumentsDelta ?: "")
                }
            }
            // chunk 入 events（轨迹视图需要），但合并窗口内不逐 token rebuild
            if (!bySeq.containsKey(event.seq)) {
                events.add(event)
                bySeq[event.seq] = event
            }
            scheduleStreamingRebuild()
            return
        } else if (event.type == "assistant/message") {
            // final 消息落地：移除对应 turn 的 pending（相同 turn 的块不再叠加）
            val turn = event.data.long("turn")
            if (turn != null) {
                pendingBlocks.remove("t" + turn)
                if (streamingTurn == turn) streamingTurn = null
            }
        } else if (event.type == "session/title") {
            val title = event.data.str("title")
            if (title != null) _title.value = title
        }
        ingestLocked(listOf(event))
    }

    private fun rebuild() {
        // surface 节点消息（按序）
        val rendered = mutableListOf<RenderedMessage>()
        val seqs = folder.activeSeqs
        for (seq in seqs) {
            val event = bySeq[seq] ?: continue
            val message = event.deriveMessage() ?: continue
            rendered.add(message.toRendered(seq))
        }
        // 流式块叠加到尾部最后一条 assistant 消息（无 final 时）
        val streamingTurn = streamingTurn
        if (streamingTurn != null) {
            val turnBlocks = pendingBlocks["t" + streamingTurn]
            if (turnBlocks != null && turnBlocks.isNotEmpty()) {
                val blocks = turnBlocks.entries.sortedBy { it.key }.map { (_, pb) ->
                    when (pb.blockType) {
                        "text" -> RenderedBlock(type = "text", text = pb.text)
                        "reasoning" -> RenderedBlock(type = "reasoning", text = pb.text)
                        "tool-call" -> RenderedBlock(
                            type = "tool-call",
                            toolName = pb.toolName,
                            toolId = pb.toolId,
                            arguments = pb.text,
                            argumentsParsed = runCatching {
                                kotlinx.serialization.json.Json.parseToJsonElement(pb.text)
                            }.getOrNull(),
                        )
                        else -> RenderedBlock(type = "text", text = pb.text)
                    }
                }
                // 找到最后一条 assistant 消息并追加（若无则新建一条流式消息）
                val lastAssistantIdx = rendered.indexOfLast { it.role == "assistant" }
                if (lastAssistantIdx >= 0) {
                    val last = rendered[lastAssistantIdx]
                    if (last.seq == bySeq.keys.maxOrNull()) {
                        rendered[lastAssistantIdx] = last.copy(
                            blocks = last.blocks + blocks,
                        )
                    } else {
                        rendered.add(
                            RenderedMessage(
                                seq = Long.MAX_VALUE,
                                role = "assistant",
                                sourceKind = "model",
                                blocks = blocks,
                                time = kotlin.time.Clock.System.now().toEpochMilliseconds(),
                            ),
                        )
                    }
                } else {
                    rendered.add(
                        RenderedMessage(
                            seq = Long.MAX_VALUE,
                            role = "assistant",
                            sourceKind = "model",
                            blocks = blocks,
                            time = kotlin.time.Clock.System.now().toEpochMilliseconds(),
                        ),
                    )
                }
            }
        }
        // 防御：seq 必须唯一（LazyColumn key）；重复时保留最后一次出现
        _messages.value = rendered.reversed().distinctBy { it.seq }.reversed()
    }

    private fun Message.toRendered(seq: Long): RenderedMessage = RenderedMessage(
        seq = seq,
        role = role,
        sourceKind = sourceKind,
        blocks = content.map { RenderedBlock.fromBlock(it) },
        time = 0L,
    )

    fun setQueue(items: List<QueueItem>) {
        _queue.value = items
    }

    fun setJobs(jobs: List<JobView>) {
        _jobs.value = jobs
    }

    fun setProjection(key: String, value: JsonElement, seq: Long) {
        _projections.value = _projections.value + (key to value)
    }

    fun setTitle(t: String) {
        _title.value = t
    }

    fun setHasMore(v: Boolean) {
        _hasMore.value = v
    }

    fun setRunning(v: Boolean) {
        _running.value = v
    }
}