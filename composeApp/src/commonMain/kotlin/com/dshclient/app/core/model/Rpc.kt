package com.dshclient.app.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/**
 * DSH 四象限 RPC 消息模型（摘自 @deepseek-ai/dsh-host-apiproxy 的 api/contract 层）：
 *   client-request  -> server-response   HTTP POST /api/<method>
 *   server-request  -> client-response   WebSocket 事件帧 + POST /api/respond
 */
object Rpc {
    const val CLIENT_REQUEST = "client-request"
    const val SERVER_RESPONSE = "server-response"
    const val SERVER_REQUEST = "server-request"
    const val CLIENT_RESPONSE = "client-response"
}

@Serializable
data class ClientRequest(
    val type: String = Rpc.CLIENT_REQUEST,
    val rpcId: String,
    val method: String,
    val payload: JsonElement? = null,
)

@Serializable
data class ServerResponse(
    val type: String = Rpc.SERVER_RESPONSE,
    val rpcId: String,
    val result: RpcResult,
)

@Serializable
data class ClientResponse(
    val type: String = Rpc.CLIENT_RESPONSE,
    val rpcId: String,
    val result: RpcResult,
)

/** 服务器发往客户端的请求（事件流帧的完整形式） */
@Serializable
data class ServerRequest(
    val type: String = Rpc.SERVER_REQUEST,
    val rpcId: String,
    val method: String,
    val payload: JsonElement,
)

@Serializable
data class RpcResult(
    val ok: Boolean,
    val value: JsonElement? = null,
    val error: RpcError? = null,
)

@Serializable
data class RpcError(
    val code: String,
    val message: String,
    val details: JsonElement = JsonNull,
)

/** 业务错误类别 */
enum class RpcErrorKind {
    BAD_REQUEST, CANCELLED, SESSION_NOT_FOUND, SESSION_CONFLICT, AGENT_BUSY,
    AGENT_PRESET_READ_ONLY, AGENT_PRESET_LOCKED, AGENT_PRESET_CONFLICT,
    AGENT_PRESET_NOT_FOUND, AGENT_PRESET_INVALID, MODEL_UNAVAILABLE,
    MODEL_DISCOVERY_FAILED, WORKSPACE_NOT_FOUND, WORKSPACE_ATTACH_FAILED,
    WORKSPACE_NAME_CONFLICT, SETTINGS_REJECTED, SETTINGS_NOT_EXPOSED,
    SETTINGS_CONFLICT, CREDENTIAL_REJECTED, SUBAGENT_NOT_FOUND,
    SUBAGENT_NOT_RESUMABLE, SUBAGENT_UNAUTHORIZED, UNKNOWN_COMMAND,
    COMMAND_ERROR, QUEUE_ITEM_NOT_FOUND, STEER_UNAVAILABLE, INTERNAL, UNKNOWN,
}

fun RpcError.kind(): RpcErrorKind = when (code) {
    "bad-request" -> RpcErrorKind.BAD_REQUEST
    "cancelled" -> RpcErrorKind.CANCELLED
    "session-not-found" -> RpcErrorKind.SESSION_NOT_FOUND
    "session-conflict" -> RpcErrorKind.SESSION_CONFLICT
    "agent-busy" -> RpcErrorKind.AGENT_BUSY
    "agent-preset-read-only" -> RpcErrorKind.AGENT_PRESET_READ_ONLY
    "agent-preset-locked" -> RpcErrorKind.AGENT_PRESET_LOCKED
    "agent-preset-conflict" -> RpcErrorKind.AGENT_PRESET_CONFLICT
    "agent-preset-not-found" -> RpcErrorKind.AGENT_PRESET_NOT_FOUND
    "agent-preset-invalid" -> RpcErrorKind.AGENT_PRESET_INVALID
    "model-unavailable" -> RpcErrorKind.MODEL_UNAVAILABLE
    "model-discovery-failed" -> RpcErrorKind.MODEL_DISCOVERY_FAILED
    "workspace-not-found" -> RpcErrorKind.WORKSPACE_NOT_FOUND
    "workspace-attach-failed" -> RpcErrorKind.WORKSPACE_ATTACH_FAILED
    "workspace-name-conflict" -> RpcErrorKind.WORKSPACE_NAME_CONFLICT
    "settings-rejected" -> RpcErrorKind.SETTINGS_REJECTED
    "settings-not-exposed" -> RpcErrorKind.SETTINGS_NOT_EXPOSED
    "settings-conflict" -> RpcErrorKind.SETTINGS_CONFLICT
    "credential-rejected" -> RpcErrorKind.CREDENTIAL_REJECTED
    "subagent-not-found" -> RpcErrorKind.SUBAGENT_NOT_FOUND
    "subagent-not-resumable" -> RpcErrorKind.SUBAGENT_NOT_RESUMABLE
    "subagent-unauthorized" -> RpcErrorKind.SUBAGENT_UNAUTHORIZED
    "unknown-command" -> RpcErrorKind.UNKNOWN_COMMAND
    "command-error" -> RpcErrorKind.COMMAND_ERROR
    "queue-item-not-found" -> RpcErrorKind.QUEUE_ITEM_NOT_FOUND
    "steer-unavailable" -> RpcErrorKind.STEER_UNAVAILABLE
    "internal" -> RpcErrorKind.INTERNAL
    else -> RpcErrorKind.UNKNOWN
}

/** RPC 业务失败异常 */
class RpcException(val rpcError: RpcError) : Exception(
    "[" + rpcError.code + "] " + rpcError.message
) {
    val kind: RpcErrorKind get() = rpcError.kind()
}

/** 传输层失败（网络、HTTP、JSON 解析） */
class TransportException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** 信任围栏拒绝（403）：Host 头不在 loopback 或 trustedHosts 中 */
class TrustFenceRejectedException(
    message: String = "请求被 DSH 信任围栏拒绝（403）：请确认服务器以 --trusted-host 包含当前访问地址启动",
) : Exception(message)

/** JsonObject 便捷访问器 */
fun JsonObject.str(name: String): String? =
    (this[name] as? JsonPrimitive)?.contentOrNull

fun JsonObject.bool(name: String): Boolean? =
    (this[name] as? JsonPrimitive)?.booleanOrNull

fun JsonObject.long(name: String): Long? =
    (this[name] as? JsonPrimitive)?.content?.toLongOrNull()

fun JsonObject.int(name: String): Int? =
    (this[name] as? JsonPrimitive)?.content?.toIntOrNull()

fun JsonObject.obj(name: String): JsonObject? =
    this[name] as? JsonObject

fun JsonObject.arr(name: String): List<JsonElement>? =
    (this[name] as? kotlinx.serialization.json.JsonArray)?.toList()

fun JsonObject.strArray(name: String): List<String> =
    arr(name)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull } ?: emptyList()