package com.dshclient.app

import com.dshclient.app.core.model.ApiMethods
import com.dshclient.app.core.model.HostDescribeValue
import com.dshclient.app.core.model.SessionListValue
import com.dshclient.app.core.model.WorkspaceListValue
import com.dshclient.app.core.network.DshConnection
import com.dshclient.app.core.network.MuxEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlinx.coroutines.flow.first
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 集成测试：连接本机运行的 dsh web（127.0.0.1:3080）验证协议闭环。
 * 前置条件：本地已启动 dsh web（dsh web --port 3080）。
 * 运行：./gradlew :composeApp:testAndroidHostTest
 */
class DshIntegrationTest {

    private fun newConnection(): DshConnection =
        DshConnection("http://127.0.0.1:3080", CoroutineScope(Dispatchers.Default), createHttpEngine())

    @Test
    fun sessionListRoundTrip() {
        runBlocking {
            val conn = newConnection()
            try {
                val value = conn.call(ApiMethods.SESSION_LIST, JsonObject(emptyMap()), SessionListValue.serializer())
                assertNotNull(value)
            } finally {
                conn.close()
            }
        }
    }

    @Test
    fun hostDescribeRoundTrip() {
        runBlocking {
            val conn = newConnection()
            try {
                val value = conn.call(ApiMethods.HOST_DESCRIBE, JsonObject(emptyMap()), HostDescribeValue.serializer())
                assertTrue(value.version.isNotBlank())
                assertTrue(value.cwd.isNotBlank())
            } finally {
                conn.close()
            }
        }
    }

    @Test
    fun workspaceListRoundTrip() {
        runBlocking {
            val conn = newConnection()
            try {
                val value = conn.call(ApiMethods.WORKSPACE_LIST, JsonObject(emptyMap()), WorkspaceListValue.serializer())
                assertNotNull(value)
            } finally {
                conn.close()
            }
        }
    }

    @Test
    fun muxWebSocketReceivesBaselineFrames() {
        runBlocking {
            val conn = newConnection()
            try {
                conn.start()
                // 打开 mux 后服务器应立即推送 session/subscribed 基线帧
                val frame = withTimeoutOrNull(15_000) {
                    conn.muxEvents.first { it is MuxEvent.Subscribed || it is MuxEvent.Unknown }
                }
                assertNotNull(frame)
            } finally {
                conn.close()
            }
        }
    }

    @Test
    fun unknownMethodReturnsTransportError() {
        runBlocking {
            val conn = newConnection()
            try {
                val result = kotlin.runCatching {
                    conn.callVoid("no.such.method", JsonObject(emptyMap()))
                }
                assertTrue(result.isFailure)
            } finally {
                conn.close()
            }
        }
    }
}