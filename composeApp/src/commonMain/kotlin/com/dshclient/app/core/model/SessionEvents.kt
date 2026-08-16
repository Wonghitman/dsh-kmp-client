package com.dshclient.app.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/**
 * 会话事件模型与 surface fold（与 @deepseek-ai/dsh-session 的客户端契约一致）。
 *
 * 事件信封：{ type, seq, time, data, sourceEventSeqs?, surfaceOp?, ignorable? }
 * seq 从 0 起连续（seq = log.length）。
 *
 * surface 折叠：只有 user/message、assistant/message、tool/result 三类事件
 * 是 surface-eligible，必须携带 surfaceOp：
 *   - "append"：追加到 surface 尾部
 *   - { op: "replace", start, end }：替换 surface 中 [start..end] 区间
 * 客户端渲染必须按 seq 顺序重放整个日志做同样的折叠。
 */
@Serializable
data class SessionEvent(
    val type: String,
    val seq: Long,
    val time: Long,
    val data: JsonObject = JsonObject(emptyMap()),
    val sourceEventSeqs: List<Long>? = null,
    @SerialName("surfaceOp") val surfaceOpRaw: JsonElement? = null,
    val ignorable: Boolean? = null,
)

/** surface 操作 */
sealed interface SurfaceOp {
    data object Append : SurfaceOp
    data class Replace(val start: Long, val end: Long) : SurfaceOp
}

/** surface-eligible 事件类型（消息产生的三种） */
val SURFACE_EVENT_TYPES = setOf("user/message", "assistant/message", "tool/result")

fun SessionEvent.surfaceOp(): SurfaceOp? {
    val raw = surfaceOpRaw ?: return null
    if (raw is JsonPrimitive && raw.contentOrNull == "append") return SurfaceOp.Append
    val obj = raw as? JsonObject ?: return null
    if (obj.str("op") != "replace") return null
    val start = obj.long("start") ?: return null
    val end = obj.long("end") ?: return null
    return SurfaceOp.Replace(start, end)
}

/** 内容块（type 判别 + 宽 payload，见 dsh-llm 的 BlockAssembler） */
data class ContentBlock(val raw: JsonObject) {
    val type: String get() = raw.str("type") ?: ""
    val text: String? get() = raw.str("text")

    // tool-call 块
    val id: String? get() = raw.str("id")
    val name: String? get() = raw.str("name")
    val arguments: String? get() = raw.str("arguments")
    val argumentsParsed: JsonElement?
        get() = arguments?.let {
            runCatching { kotlinx.serialization.json.Json.parseToJsonElement(it) }.getOrNull()
        }

    // tool-result 块
    val toolCallId: String? get() = raw.str("toolCallId")
    val isError: Boolean get() = raw.bool("isError") ?: false
    val content: List<ContentBlock>
        get() = raw.arr("content")
            ?.mapNotNull { (it as? JsonObject)?.let { b -> ContentBlock(b) } }
            ?: emptyList()

    // image 块
    val attachment: ImageAttachmentRef?
        get() = raw.obj("attachment")?.let { obj ->
            ImageAttachmentRef(
                attachmentId = obj.str("attachmentId") ?: "",
                mediaType = obj.str("mediaType") ?: "",
                bytes = obj.long("bytes") ?: 0,
                width = obj.int("width") ?: 0,
                height = obj.int("height") ?: 0,
                name = obj.str("name"),
            )
        }
}

@Serializable
data class ImageAttachmentRef(
    val attachmentId: String,
    val mediaType: String,
    val bytes: Long,
    val width: Int,
    val height: Int,
    val name: String? = null,
)

/** 消息：{ id, role, source, content } */
data class Message(
    val id: String,
    val role: String,
    val source: JsonObject,
    val content: List<ContentBlock>,
) {
    val sourceKind: String get() = source.str("kind") ?: ""
}

/** 从事件 data 或消息对象解析消息（容忍缺失字段） */
fun JsonObject.toMessage(): Message? {
    val id = str("id") ?: return null
    val role = str("role") ?: return null
    val source = obj("source") ?: JsonObject(emptyMap())
    val content = arr("content")
        ?.mapNotNull { (it as? JsonObject)?.let { b -> ContentBlock(b) } }
        ?: emptyList()
    return Message(id, role, source, content)
}

/**
 * 事件 -> 消息投影（对应 dsh-session 的 deriveEventMessage）：
 *   user/message   -> event.data 即消息
 *   assistant/message -> event.data.message（空 content 的返回 null —— 只承载 usage）
 *   tool/result    -> event.data.message
 */
fun SessionEvent.deriveMessage(): Message? = when (type) {
    "user/message" -> data.toMessage()
    "assistant/message" -> {
        val m = data.obj("message")?.toMessage()
        if (m == null || m.content.isEmpty()) null else m
    }
    "tool/result" -> data.obj("message")?.toMessage()
    else -> null
}

/** assistant/chunk 的 delta 结构 */
data class ChunkDelta(
    val type: String,          // text-delta | reasoning-delta | tool-call-delta
    val index: Int,
    val text: String?,         // text-delta / reasoning-delta
    val id: String?,           // tool-call-delta
    val name: String?,         // tool-call-delta
    val argumentsDelta: String?, // tool-call-delta
)

fun SessionEvent.parseChunkDelta(): ChunkDelta? {
    if (type != "assistant/chunk") return null
    val chunk = data.obj("chunk") ?: return null
    val chunkType = chunk.str("type") ?: return null
    return ChunkDelta(
        type = chunkType,
        index = chunk.int("index") ?: 0,
        text = chunk.str("text"),
        id = chunk.str("id"),
        name = chunk.str("name"),
        argumentsDelta = chunk.str("argumentsDelta"),
    )
}

/**
 * surface fold：按 seq 顺序重放事件，维护活跃 surface 序列。
 * 与 dsh-session 的 foldSurface 一致：append 追加、replace 原位替换。
 */
class SurfaceFolder {
    private val nodes = mutableListOf<Long>()
    private var minLoadedSeq: Long = Long.MAX_VALUE
    private var maxLoadedSeq: Long = -1

    /** 当前活跃 surface 上的事件 seq（按序） */
    val activeSeqs: List<Long> get() = nodes.toList()

    val size: Int get() = nodes.size

    /** 是否已加载 [0..maxLoadedSeq] 的完整事件（无缺口） */
    fun isCompleteFromZero(): Boolean = minLoadedSeq == 0L

    /** 重放一个事件；要求按 seq 递增或与已有范围相邻 */
    fun fold(events: List<SessionEvent>) {
        for (event in events) foldOne(event)
    }

    fun foldOne(event: SessionEvent) {
        if (event.seq < minLoadedSeq) minLoadedSeq = event.seq
        if (event.seq > maxLoadedSeq) maxLoadedSeq = event.seq
        val op = event.surfaceOp()
        if (op == null) return // 非 surface 事件不参与折叠
        when (op) {
            is SurfaceOp.Append -> {
                if (nodes.contains(event.seq)) return // 重复事件防御：已入列的 seq 不再追加
                if (nodes.isNotEmpty() && event.seq < nodes.last()) {
                    // 理论上 append 总是尾部；防御性处理
                    nodes.add(event.seq)
                    nodes.sort()
                } else {
                    nodes.add(event.seq)
                }
            }
            is SurfaceOp.Replace -> {
                val startIdx = nodes.indexOf(op.start)
                val endIdx = nodes.indexOf(op.end)
                if (startIdx != -1 && endIdx != -1 && startIdx <= endIdx) {
                    nodes.subList(startIdx, endIdx + 1).clear()
                }
                // 防御：窗口截断时 replace 范围可能不在已加载 nodes 中；
                // 避免重复添加同一 seq 导致渲染 key 冲突
                if (!nodes.contains(event.seq)) {
                    nodes.add(event.seq)
                    nodes.sort()
                }
            }
        }
    }

    /** 带折叠重放：清空后重新折叠（用于加载更早分页后重建） */
    fun resetAndFold(events: List<SessionEvent>) {
        nodes.clear()
        minLoadedSeq = Long.MAX_VALUE
        maxLoadedSeq = -1
        fold(events)
    }
}

/** 一次 surface 折叠的结果视图：seq -> 派生消息 */
fun buildMessageSeqMap(events: List<SessionEvent>): Map<Long, Message> {
    val folder = SurfaceFolder()
    folder.fold(events)
    val bySeq = events.associateBy { it.seq }
    return folder.activeSeqs.mapNotNull { seq ->
        val event = bySeq[seq] ?: return@mapNotNull null
        val message = event.deriveMessage() ?: return@mapNotNull null
        seq to message
    }.toMap()
}