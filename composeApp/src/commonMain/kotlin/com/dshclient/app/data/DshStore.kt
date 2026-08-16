package com.dshclient.app.data

import com.dshclient.app.core.network.DshConnection
import com.dshclient.app.core.network.HostEvent
import com.dshclient.app.core.network.MuxEvent
import com.dshclient.app.core.model.ApiMethods
import com.dshclient.app.core.model.HostDescribeValue
import com.dshclient.app.core.model.QuestionAnswer
import com.dshclient.app.core.model.SessionListValue
import com.dshclient.app.core.model.SessionSummary
import com.dshclient.app.core.model.WorkspaceListValue
import com.dshclient.app.core.model.WorkspaceView
import com.dshclient.app.core.model.long
import com.dshclient.app.core.model.obj
import com.dshclient.app.core.model.str
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.put

/**
 * 全局仓库：聚合连接、会话列表、工作区、审批/问题、任务。
 * 由 UI 层通过 AppViewModel 访问。
 */
class DshStore(
    override val connection: DshConnection,
    private val scope: CoroutineScope,
) : DshRepository {
    private val _sessions = MutableStateFlow<List<SessionListItem>>(emptyList())
    override val sessions: StateFlow<List<SessionListItem>> = _sessions.asStateFlow()

    private val _workspaces = MutableStateFlow<List<WorkspaceView>>(emptyList())
    override val workspaces: StateFlow<List<WorkspaceView>> = _workspaces.asStateFlow()

    private val _archivedSessionIds = MutableStateFlow<List<String>>(emptyList())
    override val archivedSessionIds: StateFlow<List<String>> = _archivedSessionIds.asStateFlow()

    private val _hostInfo = MutableStateFlow<HostDescribeValue?>(null)
    override val hostInfo: StateFlow<HostDescribeValue?> = _hostInfo.asStateFlow()

    private val _pendingQuestions = MutableStateFlow<List<PendingQuestion>>(emptyList())
    override val pendingQuestions: StateFlow<List<PendingQuestion>> = _pendingQuestions.asStateFlow()

    private val _pendingApprovals = MutableStateFlow<List<PendingApproval>>(emptyList())
    override val pendingApprovals: StateFlow<List<PendingApproval>> = _pendingApprovals.asStateFlow()

    /** sessionId -> 最近任务状态（会话列表徽章用） */
    private val _sessionJobs = MutableStateFlow<Map<String, List<com.dshclient.app.core.model.JobView>>>(emptyMap())
    val sessionJobs: StateFlow<Map<String, List<com.dshclient.app.core.model.JobView>>> = _sessionJobs.asStateFlow()

    /** sessionId -> ChatState（懒创建） */
    private val chatStates = HashMap<String, ChatState>()
    private val sessionTitles = HashMap<String, String>()

    private var collectJob: Job? = null
    private var loadedWorkspaces = false

    override fun start() {
        if (collectJob != null) return
        collectJob = scope.launch {
            connection.muxEvents.collect { handleMux(it) }
        }
        scope.launch {
            connection.hostEvents.collect { handleHost(it) }
        }
        scope.launch {
            refreshSessions()
            refreshWorkspaces()
            refreshHostInfo()
        }
    }

    override fun chatState(sessionId: String): ChatState =
        chatStates.getOrPut(sessionId) { ChatState(sessionId, scope) }

    override fun chatStateOrNull(sessionId: String): ChatState? = chatStates[sessionId]

    // ───────────────────── 刷新 ─────────────────────

    override suspend fun refreshSessions() {
        try {
            val value = connection.call(
                ApiMethods.SESSION_LIST,
                JsonObject(emptyMap()),
                SessionListValue.serializer(),
            )
            val items = value.items.map { summary ->
                val projectedTitle = summary.projections
                    ?.obj("values")
                    ?.str("title")
                SessionListItem(summary, sessionTitles[summary.sessionId] ?: projectedTitle)
            }
            _sessions.value = items
        } catch (e: Exception) {
            // 连接问题由连接层状态呈现
        }
    }

    override suspend fun refreshWorkspaces() {
        try {
            val value = connection.call(
                ApiMethods.WORKSPACE_LIST,
                JsonObject(emptyMap()),
                WorkspaceListValue.serializer(),
            )
            _workspaces.value = value.items
            _archivedSessionIds.value = value.archivedSessionIds
            loadedWorkspaces = true
        } catch (e: Exception) {
        }
    }

    override suspend fun refreshHostInfo() {
        try {
            val value = connection.call(
                ApiMethods.HOST_DESCRIBE,
                JsonObject(emptyMap()),
                HostDescribeValue.serializer(),
            )
            _hostInfo.value = value
        } catch (e: Exception) {
        }
    }

    /** 加载一个会话的历史（首次/分页）。beforeSeq 为 null 时加载最新页。 */
    /** 首次加载最多保留的事件数（防超大会话全量返回卡死进入） */
    private companion object {
        const val MAX_INITIAL_EVENTS = 1500
    }

    override suspend fun loadHistory(sessionId: String, beforeSeq: Long?, maxMessages: Int) {
        val chat = chatState(sessionId)
        val payload = buildJsonObject {
            put("sessionId", sessionId)
            if (beforeSeq != null) put("beforeSeq", beforeSeq)
            put("maxMessages", maxMessages)
        }
        try {
            val value = connection.call(
                ApiMethods.SESSION_HISTORY,
                payload,
                com.dshclient.app.core.model.SessionHistoryValue.serializer(),
            )
            var events = value.events.map { it.event }
            var hasMore = value.hasMore
            // 首次加载：消息数不足配额时服务端会返回整个日志（chunk 逐 token 事件极多），
            // 截断到尾部窗口，进入即快；上滑自动翻页会继续补齐更早部分。
            if (beforeSeq == null && events.size > MAX_INITIAL_EVENTS) {
                events = events.takeLast(MAX_INITIAL_EVENTS)
                hasMore = true
            }
            chat.ingestEvents(events)
            chat.setHasMore(hasMore)
            // 尾页带 projections 快照：标题、权限预设、上下文等全部写入 ChatState
            value.projections?.let { proj ->
                proj.obj("values")?.forEach { (key, v) ->
                    chat.setProjection(key, v, proj.long("asOfSeq") ?: 0L)
                }
                proj.obj("values")?.str("title")?.let { t ->
                    chat.setTitle(t)
                    sessionTitles[sessionId] = t
                    updateSessionTitle(sessionId, t)
                }
            }
            // 尝试从事件里找 session/title
            val titleEvent = value.events.map { it.event }
                .filter { it.type == "session/title" }
                .lastOrNull()
            titleEvent?.data?.str("title")?.let { t ->
                sessionTitles[sessionId] = t
                updateSessionTitle(sessionId, t)
            }
        } catch (e: Exception) {
            // 错误由 UI 呈现
        }
    }

    /** 加载更早的分页：按已加载事件的最小 seq 继续往前翻 */
    override suspend fun loadMore(sessionId: String) {
        val chat = chatState(sessionId)
        val minSeq = chat.minEventSeq() ?: return
        loadHistory(sessionId, beforeSeq = minSeq, maxMessages = 30)
    }

    private fun updateSessionTitle(sessionId: String, title: String) {
        _sessions.value = _sessions.value.map {
            if (it.summary.sessionId == sessionId) it.copy(title = title) else it
        }
    }

    // ───────────────────── 事件处理 ─────────────────────

    private suspend fun handleMux(event: MuxEvent) {
        when (event) {
            is MuxEvent.Subscribed -> {
                // 基线帧：该会话 lastSeq 已是最新；无操作（历史从 history 拉取）
            }
            is MuxEvent.SessionEvent -> {
                val chat = chatState(event.frame.sessionId)
                chat.onLiveEvent(event.frame.event)
            }
            is MuxEvent.ApprovalRequested -> {
                val f = event.frame
                val existing = _pendingApprovals.value.filterNot {
                    it.sessionId == f.sessionId && it.approvalId == f.approvalId
                }
                _pendingApprovals.value = existing + PendingApproval(
                    rpcId = event.rpcId,
                    sessionId = f.sessionId,
                    approvalId = f.approvalId,
                    toolName = f.toolName,
                    callId = f.callId,
                    reason = f.reason,
                )
            }
            is MuxEvent.ApprovalResolved -> {
                val f = event.frame
                _pendingApprovals.value = _pendingApprovals.value.filterNot {
                    it.sessionId == f.sessionId && it.approvalId == f.approvalId
                }
            }
            is MuxEvent.QuestionRequested -> {
                val f = event.frame
                _pendingQuestions.value = _pendingQuestions.value.filterNot {
                    it.sessionId == f.sessionId
                } + PendingQuestion(
                    rpcId = event.rpcId,
                    sessionId = f.sessionId,
                    questions = f.questions,
                )
            }
            is MuxEvent.QuestionResolved -> {
                val f = event.frame
                _pendingQuestions.value = _pendingQuestions.value.filterNot {
                    it.sessionId == f.sessionId
                }
            }
            is MuxEvent.Queue -> {
                chatState(event.frame.sessionId).setQueue(event.frame.items)
            }
            is MuxEvent.Jobs -> {
                chatState(event.frame.sessionId).setJobs(event.frame.jobs)
                _sessionJobs.value = _sessionJobs.value + (event.frame.sessionId to event.frame.jobs)
            }
            is MuxEvent.Projection -> {
                chatState(event.frame.sessionId)
                    .setProjection(event.frame.key, event.frame.value, event.frame.seq)
            }
            is MuxEvent.StreamError -> Unit
            is MuxEvent.Unknown -> Unit
        }
    }

    private fun handleHost(event: HostEvent) {
        when (event) {
            is HostEvent.SessionAdded -> {
                val f = event.frame
                val summary = SessionSummary(
                    sessionId = f.sessionId,
                    updatedAt = 0L,
                    running = false,
                    blank = f.blank,
                    parentSessionId = f.parentSessionId,
                    origin = f.origin,
                    cwd = f.cwd,
                    agentPreset = f.agentPreset,
                )
                _sessions.value = listOf(SessionListItem(summary, sessionTitles[f.sessionId])) +
                    _sessions.value.filterNot { it.summary.sessionId == f.sessionId }
            }
            is HostEvent.SessionRemoved -> {
                _sessions.value = _sessions.value.filterNot {
                    it.summary.sessionId == event.frame.sessionId
                }
                chatStates.remove(event.frame.sessionId)
            }
            is HostEvent.SessionStatus -> {
                val f = event.frame
                _sessions.value = _sessions.value.map {
                    if (it.summary.sessionId == f.sessionId) {
                        it.copy(summary = it.summary.copy(running = f.running))
                    } else it
                }
                chatStateOrNull(f.sessionId)?.setRunning(f.running)
            }
            is HostEvent.AgentError -> Unit
            is HostEvent.WorkspaceChanged -> {
                val f = event.frame
                _workspaces.value = _workspaces.value
                    .filterNot { it.workspaceId == f.workspace.workspaceId } + f.workspace
            }
            is HostEvent.WorkspaceRemoved -> {
                _workspaces.value = _workspaces.value.filterNot {
                    it.workspaceId == event.frame.workspaceId
                }
            }
            is HostEvent.WorkspaceOrderChanged -> {
                val ids = event.frame.workspaceIds
                val byId = _workspaces.value.associateBy { it.workspaceId }
                _workspaces.value = ids.mapNotNull { byId[it] }
            }
            is HostEvent.ArchivedSessionsChanged -> {
                _archivedSessionIds.value = event.frame.archivedSessionIds
            }
            is HostEvent.RemoteEvent -> Unit
            is HostEvent.StreamError -> Unit
            is HostEvent.Unknown -> Unit
        }
    }

    override suspend fun renameWorkspace(workspaceId: String, title: String): Boolean = try {
        connection.call(
            ApiMethods.WORKSPACE_RENAME,
            buildJsonObject {
                put("workspaceId", workspaceId)
                put("title", title)
            },
            com.dshclient.app.core.model.WorkspaceRenameValue.serializer(),
        )
        refreshWorkspaces()
        true
    } catch (e: Exception) {
        false
    }

    override suspend fun deleteWorkspace(workspaceId: String): Boolean = try {
        connection.callVoid(
            ApiMethods.WORKSPACE_DELETE,
            buildJsonObject { put("workspaceId", workspaceId) },
        )
        refreshWorkspaces()
        true
    } catch (e: Exception) {
        false
    }

    override suspend fun createSessionInWorkspace(workspaceId: String): String? = try {
        val value = connection.call(
            ApiMethods.SESSION_CREATE,
            buildJsonObject { put("workspaceId", workspaceId) },
            com.dshclient.app.core.model.SessionCreateValue.serializer(),
        )
        refreshSessions()
        value.sessionId
    } catch (e: Exception) {
        null
    }

    override suspend fun prompt(sessionId: String, text: String): Boolean {
        val payload = buildJsonObject {
            put("sessionId", sessionId)
            put("mode", "queue")
            put("content", kotlinx.serialization.json.buildJsonArray {
                add(kotlinx.serialization.json.buildJsonObject {
                    put("type", "text")
                    put("text", text)
                })
            })
        }
        try {
            connection.callVoid(ApiMethods.SESSION_PROMPT, payload)
            return true
        } catch (e: Exception) {
            return false
        }
    }

    override suspend fun cancelTurn(sessionId: String): Boolean {
        return try {
            connection.callVoid(
                ApiMethods.SESSION_CANCEL,
                buildJsonObject { put("sessionId", sessionId) },
            )
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun answerQuestion(question: PendingQuestion, answer: QuestionAnswer): Boolean {
        return try {
            val payload = buildJsonObject {
                put("sessionId", question.sessionId)
                put("answer", buildJsonObject {
                    put("answers", kotlinx.serialization.json.buildJsonArray {
                        add(buildJsonObject {
                            put("id", answer.id)
                            put("selected", kotlinx.serialization.json.buildJsonArray {
                                answer.selected.forEach { add(it) }
                            })
                            answer.custom?.let { put("custom", it) }
                        })
                    })
                })
            }
            connection.respond(question.rpcId, true, payload)
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun answerApproval(approval: PendingApproval, outcome: String): Boolean {
        return try {
            val payload = buildJsonObject {
                put("sessionId", approval.sessionId)
                put("approvalId", approval.approvalId)
                put("outcome", outcome)
            }
            connection.respond(approval.rpcId, true, payload)
        } catch (e: Exception) {
            false
        }
    }
}