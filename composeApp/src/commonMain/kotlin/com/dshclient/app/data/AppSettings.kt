package com.dshclient.app.data

import com.russhwolf.settings.Settings

/** 应用配置持久化 */
class AppSettings(private val settings: Settings) {
    var baseUrl: String
        get() = settings.getString(KEY_BASE_URL, "")
        set(value) = settings.putString(KEY_BASE_URL, value)

    var lastConnectedUrl: String
        get() = settings.getString(KEY_LAST_URL, "")
        set(value) = settings.putString(KEY_LAST_URL, value)

    var autoConnect: Boolean
        get() = settings.getBoolean(KEY_AUTO_CONNECT, true)
        set(value) = settings.putBoolean(KEY_AUTO_CONNECT, value)

    /** 调试钩子：连接成功后自动打开的会话（测试用） */
    var debugAutoOpenSession: String?
        get() = settings.getStringOrNull(KEY_DEBUG_SESSION)
        set(value) = if (value == null) settings.remove(KEY_DEBUG_SESSION) else settings.putString(KEY_DEBUG_SESSION, value)

    /** 最近连接过的服务器列表（换行分隔） */
    fun recentServers(): List<String> =
        settings.getString(KEY_RECENT, "").split("\n").filter { it.isNotBlank() }

    fun addRecent(url: String) {
        val list = (listOf(url) + recentServers()).distinct().take(8)
        settings.putString(KEY_RECENT, list.joinToString("\n"))
    }

    private companion object {
        const val KEY_BASE_URL = "base_url"
        const val KEY_LAST_URL = "last_url"
        const val KEY_AUTO_CONNECT = "auto_connect"
        const val KEY_RECENT = "recent"
        const val KEY_DEBUG_SESSION = "debug_auto_open_session"
    }
}