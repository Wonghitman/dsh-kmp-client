package com.dshclient.app

import com.dshclient.app.core.model.ClientRequest
import com.dshclient.app.core.model.Rpc
import com.dshclient.app.core.model.RpcError
import com.dshclient.app.core.model.RpcResult
import com.dshclient.app.core.model.ServerRequest
import com.dshclient.app.core.model.ServerResponse
import com.dshclient.app.core.model.SessionEvent
import com.dshclient.app.core.model.SurfaceFolder
import com.dshclient.app.core.model.SurfaceOp
import com.dshclient.app.core.model.buildMessageSeqMap
import com.dshclient.app.core.model.deriveMessage
import com.dshclient.app.core.model.kind
import com.dshclient.app.core.model.surfaceOp
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 与服务端契约一致的 JSON 配置 */
private val json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
}

class ProtocolTest {

    @Test
    fun clientRequestWireFormat() {
        val request = ClientRequest(
            rpcId = "abc-123",
            method = "session.list",
            payload = JsonObject(emptyMap()),
        )
        val wire = json.encodeToString(ClientRequest.serializer(), request)
        // 服务端要求完整信封：type/rpcId/method/payload
        assertTrue(wire.contains("\"type\":\"client-request\""), wire)
        assertTrue(wire.contains("\"rpcId\":\"abc-123\""), wire)
        assertTrue(wire.contains("\"method\":\"session.list\""), wire)
        assertTrue(wire.contains("\"payload\":{}"), wire)
    }

    @Test
    fun serverResponseOkParsing() {
        val wire = """{"type":"server-response","rpcId":"abc","result":{"ok":true,"value":{"items":[]}}}"""
        val response = json.decodeFromString(ServerResponse.serializer(), wire)
        assertEquals("abc", response.rpcId)
        assertTrue(response.result.ok)
        assertNotNull(response.result.value)
        assertNull(response.result.error)
    }

    @Test
    fun serverResponseErrorParsing() {
        val wire = """{"type":"server-response","rpcId":"abc","result":{"ok":false,"error":{"code":"session-not-found","message":"no such session","details":{"sessionId":"s1"}}}}"""
        val response = json.decodeFromString(ServerResponse.serializer(), wire)
        assertFalse(response.result.ok)
        assertEquals("session-not-found", response.result.error?.code)
    }

    @Test
    fun serverRequestFrameParsing() {
        val wire = """{"type":"server-request","rpcId":"f1","method":"session/subscribed","payload":{"sessionId":"s1","lastSeq":41}}"""
        val req = json.decodeFromString(ServerRequest.serializer(), wire)
        assertEquals("f1", req.rpcId)
        assertEquals("session/subscribed", req.method)
        val payload = req.payload as JsonObject
        assertEquals("s1", payload["sessionId"]?.let { (it as JsonPrimitive).content })
    }

    @Test
    fun unknownFieldsIgnored() {
        // 服务端可能增加字段；客户端必须容忍
        val wire = """{"type":"server-response","rpcId":"abc","result":{"ok":true,"value":{}},"extra":"future"}"""
        val response = json.decodeFromString(ServerResponse.serializer(), wire)
        assertTrue(response.result.ok)
    }

    @Test
    fun sessionEventEnvelopeParsing() {
        val wire = """{"type":"user/message","seq":5,"time":1700000000000,"data":{"id":"m1","role":"user","source":{"kind":"user"},"content":[{"type":"text","text":"hi"}]},"surfaceOp":"append"}"""
        val event = json.decodeFromString(SessionEvent.serializer(), wire)
        assertEquals("user/message", event.type)
        assertEquals(5L, event.seq)
        assertEquals(SurfaceOp.Append, event.surfaceOp())
        val message = event.deriveMessage()
        assertNotNull(message)
        assertEquals("m1", message.id)
        assertEquals("user", message.role)
        assertEquals("hi", message.content.first().text)
    }

    @Test
    fun replaceSurfaceOpParsing() {
        val wire = """{"type":"tool/result","seq":9,"time":1,"data":{},"surfaceOp":{"op":"replace","start":3,"end":4},"sourceEventSeqs":[3,4]}"""
        val event = json.decodeFromString(SessionEvent.serializer(), wire)
        val op = event.surfaceOp()
        assertTrue(op is SurfaceOp.Replace)
        assertEquals(3L, (op as SurfaceOp.Replace).start)
        assertEquals(4L, op.end)
    }

    @Test
    fun rpcErrorKindMapping() {
        assertEquals(com.dshclient.app.core.model.RpcErrorKind.SESSION_NOT_FOUND,
            RpcError("session-not-found", "x").kind())
        assertEquals(com.dshclient.app.core.model.RpcErrorKind.INTERNAL,
            RpcError("internal", "x").kind())
        assertEquals(com.dshclient.app.core.model.RpcErrorKind.UNKNOWN,
            RpcError("brand-new-code", "x").kind())
    }
}

class SurfaceFoldTest {

    private fun event(seq: Long, type: String, surfaceOp: Any?, data: JsonObject = buildJsonObject {}) =
        SessionEvent(
            type = type,
            seq = seq,
            time = seq,
            data = data,
            surfaceOpRaw = when (surfaceOp) {
                "append" -> JsonPrimitive("append")
                is Pair<*, *> -> buildJsonObject {
                    put("op", "replace")
                    put("start", (surfaceOp.first as Long))
                    put("end", (surfaceOp.second as Long))
                }
                else -> null
            },
        )

    @Test
    fun appendOnly() {
        val folder = SurfaceFolder()
        folder.fold(
            listOf(
                event(0, "user/message", "append"),
                event(1, "assistant/message", "append"),
                event(2, "tool/result", "append"),
            ),
        )
        assertEquals(listOf(0L, 1L, 2L), folder.activeSeqs)
    }

    @Test
    fun nonSurfaceEventsIgnored() {
        val folder = SurfaceFolder()
        folder.fold(
            listOf(
                event(0, "turn/start", null),
                event(1, "assistant/chunk", null),
                event(2, "user/message", "append"),
            ),
        )
        assertEquals(listOf(2L), folder.activeSeqs)
    }

    @Test
    fun replaceShadowsRange() {
        val folder = SurfaceFolder()
        folder.fold(
            listOf(
                event(0, "user/message", "append"),
                event(1, "assistant/message", "append"),
                event(2, "tool/result", "append"),
                event(3, "tool/result", "append"),
                // compaction：新消息替换旧范围
                event(4, "assistant/message", Pair(0L, 3L)),
            ),
        )
        assertEquals(listOf(4L), folder.activeSeqs)
    }

    @Test
    fun resetAndFoldRebuilds() {
        val folder = SurfaceFolder()
        folder.fold(listOf(event(0, "user/message", "append")))
        folder.resetAndFold(
            listOf(
                event(0, "user/message", "append"),
                event(1, "assistant/message", "append"),
            ),
        )
        assertEquals(listOf(0L, 1L), folder.activeSeqs)
    }

    @Test
    fun buildMessageSeqMapProjectsMessages() {
        val events = listOf(
            event(0, "user/message", "append", buildJsonObject {
                put("id", "m0")
                put("role", "user")
                put("source", buildJsonObject { put("kind", "user") })
                put("content", kotlinx.serialization.json.buildJsonArray {
                    add(buildJsonObject {
                        put("type", "text")
                        put("text", "hello")
                    })
                })
            }),
            event(1, "assistant/message", "append", buildJsonObject {
                put("turn", 0)
                put("message", buildJsonObject {
                    put("id", "m1")
                    put("role", "assistant")
                    put("source", buildJsonObject { put("kind", "model") })
                    put("content", kotlinx.serialization.json.buildJsonArray {
                        add(buildJsonObject {
                            put("type", "text")
                            put("text", "world")
                        })
                    })
                })
            }),
        )
        val map = buildMessageSeqMap(events)
        assertEquals(setOf(0L, 1L), map.keys)
        assertEquals("hello", map[0L]?.content?.first()?.text)
        assertEquals("world", map[1L]?.content?.first()?.text)
    }

    @Test
    fun emptyAssistantMessageDrops() {
        // 空 content 的 assistant/message 只承载 usage，不投影
        val empty = event(1, "assistant/message", "append", buildJsonObject {
            put("turn", 0)
            put("message", buildJsonObject {
                put("id", "m1")
                put("role", "assistant")
                put("source", buildJsonObject { put("kind", "model") })
                put("content", kotlinx.serialization.json.buildJsonArray {})
            })
        })
        assertNull(empty.deriveMessage())
    }
}