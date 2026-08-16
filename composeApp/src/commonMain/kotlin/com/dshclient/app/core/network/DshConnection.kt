package com.dshclient.app.core.network

import com.dshclient.app.core.model.ApprovalRequestedFrame
import com.dshclient.app.core.model.ApprovalResolvedFrame
import com.dshclient.app.core.model.HostAgentErrorFrame
import com.dshclient.app.core.model.HostArchivedSessionsChangedFrame
import com.dshclient.app.core.model.HostRemoteEventFrame
import com.dshclient.app.core.model.HostSessionAddedFrame
import com.dshclient.app.core.model.HostSessionRemovedFrame
import com.dshclient.app.core.model.HostSessionStatusFrame
import com.dshclient.app.core.model.HostWorkspaceChangedFrame
import com.dshclient.app.core.model.HostWorkspaceOrderChangedFrame
import com.dshclient.app.core.model.HostWorkspaceRemovedFrame
import com.dshclient.app.core.model.QuestionRequestedFrame
import com.dshclient.app.core.model.QuestionResolvedFrame
import com.dshclient.app.core.model.Rpc
import com.dshclient.app.core.model.RpcError
import com.dshclient.app.core.model.RpcException
import com.dshclient.app.core.model.ServerRequest
import com.dshclient.app.core.model.ServerResponse
import com.dshclient.app.core.model.SessionEventFrame
import com.dshclient.app.core.model.SessionJobsFrame
import com.dshclient.app.core.model.SessionProjectionFrame
import com.dshclient.app.core.model.SessionQueueFrame
import com.dshclient.app.core.model.SessionSubscribedFrame
import com.dshclient.app.core.model.StreamErrorFrame
import com.dshclient.app.core.model.TransportException
import com.dshclient.app.core.model.TrustFenceRejectedException
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encodeToString
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** 连接状态 */
enum class ConnectionStatus { DISCONNECTED, CONNECTING, CONNECTED, RECONNECTING, FAILED }

data class ConnectionState(
    val status: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val message: String? = null,
    val retryCount: Int = 0,
)

/** 解析后的 mux 事件（sealed 便于 UI 分派） */
sealed interface MuxEvent {
    data class Subscribed(val frame: SessionSubscribedFrame, val rpcId: String = "") : MuxEvent
    data class SessionEvent(val frame: SessionEventFrame, val rpcId: String = "") : MuxEvent
    data class ApprovalRequested(val frame: ApprovalRequestedFrame, val rpcId: String = "") : MuxEvent
    data class ApprovalResolved(val frame: ApprovalResolvedFrame, val rpcId: String = "") : MuxEvent
    data class QuestionRequested(val frame: QuestionRequestedFrame, val rpcId: String = "") : MuxEvent
    data class QuestionResolved(val frame: QuestionResolvedFrame, val rpcId: String = "") : MuxEvent
    data class Queue(val frame: SessionQueueFrame) : MuxEvent
    data class Jobs(val frame: SessionJobsFrame) : MuxEvent
    data class Projection(val frame: SessionProjectionFrame) : MuxEvent
    data class StreamError(val frame: StreamErrorFrame) : MuxEvent
    data class Unknown(val method: String, val payload: JsonElement) : MuxEvent
}

/** 解析后的 host 事件 */
sealed interface HostEvent {
    data class SessionAdded(val frame: HostSessionAddedFrame) : HostEvent
    data class SessionRemoved(val frame: HostSessionRemovedFrame) : HostEvent
    data class SessionStatus(val frame: HostSessionStatusFrame) : HostEvent
    data class AgentError(val frame: HostAgentErrorFrame) : HostEvent
    data class WorkspaceChanged(val frame: HostWorkspaceChangedFrame) : HostEvent
    data class WorkspaceRemoved(val frame: HostWorkspaceRemovedFrame) : HostEvent
    data class WorkspaceOrderChanged(val frame: HostWorkspaceOrderChangedFrame) : HostEvent
    data class ArchivedSessionsChanged(val frame: HostArchivedSessionsChangedFrame) : HostEvent
    data class RemoteEvent(val frame: HostRemoteEventFrame) : HostEvent
    data class StreamError(val frame: StreamErrorFrame) : HostEvent
    data class Unknown(val method: String, val payload: JsonElement) : HostEvent
}

/** rpcId 生成（UUID） */
@OptIn(kotlin.uuid.ExperimentalUuidApi::class)
private object RpcIdGen {
    fun next(): String = kotlin.uuid.Uuid.random().toString()
}

/**
 * DSH 连接核心：HTTP RPC + WebSocket 双事件流。
 *
 * 协议要点（@deepseek-ai/dsh-client-connection）：
 *  - POST /api/<method>：client-request -> server-response，Content-Type 必须 application/json
 *  - POST /api/respond：client-response（审批/问题应答）
 *  - WS /api/events.mux：会话事件流（打开即推 session/subscribed 基线）
 *  - WS /api/events.host：主机事件流
 *  - 信任围栏：Host 头必须是 loopback 或 --trusted-host 声明的权威；403 时给引导
 */
class DshConnection(
    val baseUrl: String,
    private val scope: CoroutineScope,
    private val engine: HttpClientEngine,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    },
) {
    private val http = HttpClient(engine) {
        install(ContentNegotiation) { json(this@DshConnection.json) }
        install(WebSockets)
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 30_000
        }
    }

    private val _state = MutableStateFlow(ConnectionState())
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val _muxEvents = MutableSharedFlow<MuxEvent>(extraBufferCapacity = 256)
    val muxEvents: SharedFlow<MuxEvent> = _muxEvents.asSharedFlow()

    private val _hostEvents = MutableSharedFlow<HostEvent>(extraBufferCapacity = 64)
    val hostEvents: SharedFlow<HostEvent> = _hostEvents.asSharedFlow()

    private var muxJob: Job? = null
    private var hostJob: Job? = null
    private var active = false
    private val callMutex = Mutex()

    /** 规范化 baseUrl：http://host:port */
    fun normalizedBase(): String {
        var u = baseUrl.trim()
        if (u.isEmpty()) return u
        if (!u.startsWith("http://") && !u.startsWith("https://")) u = "http://" + u
        return u.trimEnd('/')
    }

    private fun wsUrl(path: String): String {
        val base = normalizedBase()
        val scheme = if (base.startsWith("https://")) "wss" else "ws"
        val host = base.removePrefix(if (scheme == "wss") "https://" else "http://")
        return scheme + "://" + host + path
    }

    // ─────────────────────── HTTP RPC ───────────────────────

    /** 通用调用：payload 必须是非 null 的 JsonObject（服务端 schema 要求对象 payload） */
    suspend fun callRaw(method: String, payload: JsonObject? = null): JsonElement? {
        val rpcId = RpcIdGen.next()
        val body = json.encodeToString(
            com.dshclient.app.core.model.ClientRequest.serializer(),
            com.dshclient.app.core.model.ClientRequest(
                rpcId = rpcId,
                method = method,
                payload = payload,
            ),
        )
        val response: HttpResponse = http.post(normalizedBase() + "/api/" + method) {
            contentType(ContentType.Application.Json)
            setBody(body)
            header("Accept", "application/json")
        }
        when {
            response.status == HttpStatusCode.Forbidden -> throw TrustFenceRejectedException()
            response.status != HttpStatusCode.OK -> throw TransportException(
                "HTTP " + response.status.value + ": " + response.bodyAsText().take(200),
            )
        }
        val parsed = try {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                val text = response.bodyAsText()
                json.decodeFromString(ServerResponse.serializer(), text)
            }
        } catch (e: Exception) {
            throw TransportException("响应解析失败: " + e.message, e)
        }
        if (!parsed.result.ok) {
            val err = parsed.result.error ?: RpcError("internal", "未知错误")
            throw RpcException(err)
        }
        return parsed.result.value
    }

    /** 类型化调用 */
    suspend fun <T> call(
        method: String,
        payload: JsonObject? = null,
        deserializer: KSerializer<T>,
    ): T {
        val value = callRaw(method, payload) ?: throw TransportException("响应缺少 value")
        return json.decodeFromJsonElement(deserializer, value)
    }

    /** void 调用（只检查 ok） */
    suspend fun callVoid(method: String, payload: JsonObject? = null) {
        callRaw(method, payload)
    }

    /** 应答（client-response）：审批 / 问题 */
    suspend fun respond(rpcId: String, resultOk: Boolean, value: JsonElement? = null, error: RpcError? = null): Boolean {
        val body = json.encodeToString(
            com.dshclient.app.core.model.ClientResponse.serializer(),
            com.dshclient.app.core.model.ClientResponse(
                rpcId = rpcId,
                result = com.dshclient.app.core.model.RpcResult(
                    ok = resultOk,
                    value = value,
                    error = error,
                ),
            ),
        )
        val response: HttpResponse = http.post(normalizedBase() + "/api/respond") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        if (response.status == HttpStatusCode.Forbidden) throw TrustFenceRejectedException()
        if (response.status != HttpStatusCode.OK) {
            throw TransportException("respond HTTP " + response.status.value)
        }
        val parsed = try {
            json.parseToJsonElement(response.bodyAsText()).jsonObject
        } catch (e: Exception) {
            return false
        }
        return parsed["accepted"]?.jsonPrimitive?.content == "true"
    }

    // ─────────────────────── 事件流 ───────────────────────

    /** 启动连接：打开两个 WS 并自动重连 */
    fun start() {
        if (active) return
        active = true
        muxJob = scope.launch(Dispatchers.IO) { runMuxLoop() }
        hostJob = scope.launch(Dispatchers.IO) { runHostLoop() }
    }

    fun stop() {
        active = false
        muxJob?.cancel()
        hostJob?.cancel()
        muxJob = null
        hostJob = null
        _state.value = ConnectionState(ConnectionStatus.DISCONNECTED)
    }

    private suspend fun runMuxLoop() {
        var attempt = 0
        while (active && scope.isActive) {
            try {
                _state.value = ConnectionState(
                    status = if (attempt == 0) ConnectionStatus.CONNECTING else ConnectionStatus.RECONNECTING,
                    retryCount = attempt,
                )
                http.webSocket(wsUrl("/api/events.mux")) {
                    _state.value = ConnectionState(ConnectionStatus.CONNECTED)
                    attempt = 0
                    for (frame in incoming) {
                        if (frame is Frame.Text) {
                            val text = frame.readText()
                            handleMuxText(text)
                        }
                    }
                }
            } catch (e: Exception) {
                if (!active) break
                _state.value = ConnectionState(
                    status = ConnectionStatus.RECONNECTING,
                    message = e.message,
                    retryCount = attempt,
                )
            }
            if (!active) break
            attempt++
            delay(backoffDelay(attempt))
        }
    }

    private suspend fun runHostLoop() {
        var attempt = 0
        while (active && scope.isActive) {
            try {
                http.webSocket(wsUrl("/api/events.host")) {
                    attempt = 0
                    for (frame in incoming) {
                        if (frame is Frame.Text) {
                            handleHostText(frame.readText())
                        }
                    }
                }
            } catch (e: Exception) {
                if (!active) break
            }
            if (!active) break
            attempt++
            delay(backoffDelay(attempt))
        }
    }

    private fun backoffDelay(attempt: Int): Long {
        // 指数退避 + 抖动：1s, 2s, 4s ... 上限 30s
        val base = 1000L * (1L shl minOf(attempt, 5))
        val jitter = (Math.random() * 500).toLong()
        return minOf(base, 30_000L) + jitter
    }

    private fun handleMuxText(text: String) {
        val req = try {
            json.decodeFromString(ServerRequest.serializer(), text)
        } catch (e: Exception) {
            return
        }
        val payload = req.payload
        val event: MuxEvent = try {
            when (req.method) {
                "session/subscribed" -> MuxEvent.Subscribed(
                    json.decodeFromJsonElement(SessionSubscribedFrame.serializer(), payload),
                )
                "session/event" -> MuxEvent.SessionEvent(
                    json.decodeFromJsonElement(SessionEventFrame.serializer(), payload),
                )
                "approval/requested" -> MuxEvent.ApprovalRequested(
                    json.decodeFromJsonElement(ApprovalRequestedFrame.serializer(), payload),
                    req.rpcId,
                )
                "approval/resolved" -> MuxEvent.ApprovalResolved(
                    json.decodeFromJsonElement(ApprovalResolvedFrame.serializer(), payload),
                )
                "question/requested" -> MuxEvent.QuestionRequested(
                    json.decodeFromJsonElement(QuestionRequestedFrame.serializer(), payload),
                    req.rpcId,
                )
                "question/resolved" -> MuxEvent.QuestionResolved(
                    json.decodeFromJsonElement(QuestionResolvedFrame.serializer(), payload),
                )
                "session/queue" -> MuxEvent.Queue(
                    json.decodeFromJsonElement(SessionQueueFrame.serializer(), payload),
                )
                "session/jobs" -> MuxEvent.Jobs(
                    json.decodeFromJsonElement(SessionJobsFrame.serializer(), payload),
                )
                "session/projection" -> MuxEvent.Projection(
                    json.decodeFromJsonElement(SessionProjectionFrame.serializer(), payload),
                )
                "stream/error" -> MuxEvent.StreamError(
                    json.decodeFromJsonElement(StreamErrorFrame.serializer(), payload),
                )
                else -> MuxEvent.Unknown(req.method, payload)
            }
        } catch (e: Exception) {
            MuxEvent.Unknown(req.method, payload)
        }
        _muxEvents.tryEmit(event)
    }

    private fun handleHostText(text: String) {
        val req = try {
            json.decodeFromString(ServerRequest.serializer(), text)
        } catch (e: Exception) {
            return
        }
        val payload = req.payload
        val event: HostEvent = try {
            when (req.method) {
                "host/session-added" -> HostEvent.SessionAdded(
                    json.decodeFromJsonElement(HostSessionAddedFrame.serializer(), payload),
                )
                "host/session-removed" -> HostEvent.SessionRemoved(
                    json.decodeFromJsonElement(HostSessionRemovedFrame.serializer(), payload),
                )
                "host/session-status" -> HostEvent.SessionStatus(
                    json.decodeFromJsonElement(HostSessionStatusFrame.serializer(), payload),
                )
                "host/agent-error" -> HostEvent.AgentError(
                    json.decodeFromJsonElement(HostAgentErrorFrame.serializer(), payload),
                )
                "host/workspace-changed" -> HostEvent.WorkspaceChanged(
                    json.decodeFromJsonElement(HostWorkspaceChangedFrame.serializer(), payload),
                )
                "host/workspace-removed" -> HostEvent.WorkspaceRemoved(
                    json.decodeFromJsonElement(HostWorkspaceRemovedFrame.serializer(), payload),
                )
                "host/workspace-order-changed" -> HostEvent.WorkspaceOrderChanged(
                    json.decodeFromJsonElement(HostWorkspaceOrderChangedFrame.serializer(), payload),
                )
                "host/archived-sessions-changed" -> HostEvent.ArchivedSessionsChanged(
                    json.decodeFromJsonElement(HostArchivedSessionsChangedFrame.serializer(), payload),
                )
                "host/remote-event" -> HostEvent.RemoteEvent(
                    json.decodeFromJsonElement(HostRemoteEventFrame.serializer(), payload),
                )
                "stream/error" -> HostEvent.StreamError(
                    json.decodeFromJsonElement(StreamErrorFrame.serializer(), payload),
                )
                else -> HostEvent.Unknown(req.method, payload)
            }
        } catch (e: Exception) {
            HostEvent.Unknown(req.method, payload)
        }
        _hostEvents.tryEmit(event)
    }

    fun close() {
        stop()
        http.close()
    }
}