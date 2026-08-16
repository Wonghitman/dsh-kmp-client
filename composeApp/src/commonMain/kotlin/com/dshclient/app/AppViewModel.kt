package com.dshclient.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dshclient.app.data.AppSettings
import com.dshclient.app.data.DshStore
import com.dshclient.app.core.network.DshConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 导航目标 */
sealed interface Screen {
    data object Connect : Screen
    data object Home : Screen
    data class Chat(val sessionId: String) : Screen
}

/** 主页底部 tab（工作区操作并入会话分组，任务并入聊天左滑面板） */
enum class HomeTab(val label: String) { SESSIONS("会话"), SETTINGS("设置") }

class AppViewModel : ViewModel() {
    val appSettings = AppSettings(createSettings())

    private val _screen = MutableStateFlow<Screen>(Screen.Connect)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    private val _homeTab = MutableStateFlow(HomeTab.SESSIONS)
    val homeTab: StateFlow<HomeTab> = _homeTab.asStateFlow()

    private val _connection = MutableStateFlow<DshConnection?>(null)
    val connection: StateFlow<DshConnection?> = _connection.asStateFlow()

    private val _store = MutableStateFlow<DshStore?>(null)
    val store: StateFlow<DshStore?> = _store.asStateFlow()

    private val _connecting = MutableStateFlow(false)
    val connecting: StateFlow<Boolean> = _connecting.asStateFlow()

    private val _connectError = MutableStateFlow<String?>(null)
    val connectError: StateFlow<String?> = _connectError.asStateFlow()

    private val _currentSessionId = MutableStateFlow<String?>(null)
    val currentSessionId: StateFlow<String?> = _currentSessionId.asStateFlow()

    /** 后台作用域（连接存活独立于 UI） */
    private val connectionScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        val last = appSettings.lastConnectedUrl
        if (last.isNotBlank() && appSettings.autoConnect) {
            launchConnect(last)
        }
    }

    /** 建立连接 */
    fun launchConnect(rawUrl: String) {
        val url = normalizeUrl(rawUrl)
        if (url.isEmpty()) {
            _connectError.value = "请输入服务器地址"
            return
        }
        if (_connecting.value) return
        _connecting.value = true
        _connectError.value = null
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val conn = DshConnection(url, connectionScope, createHttpEngine())
                conn.start()
                val store = DshStore(conn, connectionScope)
                store.start()
                _connection.value = conn
                _store.value = store
                appSettings.baseUrl = url
                appSettings.lastConnectedUrl = url
                appSettings.addRecent(url)
                val debugSession = appSettings.debugAutoOpenSession
                if (debugSession != null) {
                    openChat(debugSession)
                } else {
                    _screen.value = Screen.Home
                }
            } catch (e: Exception) {
                _connectError.value = "连接失败: " + (e.message ?: e::class.simpleName)
            } finally {
                _connecting.value = false
            }
        }
    }

    private fun normalizeUrl(raw: String): String {
        var u = raw.trim()
        if (u.isEmpty()) return ""
        if (!u.startsWith("http://") && !u.startsWith("https://")) u = "http://" + u
        return u.trimEnd('/')
    }

    /** 诊断连接错误并给出引导 */
    fun diagnose(error: Throwable): String = when (error) {
        is com.dshclient.app.core.model.TrustFenceRejectedException -> error.message ?: ""
        is com.dshclient.app.core.model.RpcException -> "服务器返回错误: " + error.message
        else -> "无法连接服务器: " + (error.message ?: error::class.simpleName)
    }

    fun disconnect() {
        _connection.value?.close()
        _connection.value = null
        _store.value = null
        _screen.value = Screen.Connect
    }

    fun openChat(sessionId: String) {
        _currentSessionId.value = sessionId
        val store = _store.value ?: return
        // 先切页面保证 UI 立即响应；历史加载与折叠重建放 IO
        // （流式会话高频事件可能让事件锁繁忙，页面切换不应等待历史）
        _screen.value = Screen.Chat(sessionId)
        viewModelScope.launch(Dispatchers.IO) {
            store.loadHistory(sessionId, maxMessages = 15)
        }
    }

    fun backToHome() {
        _screen.value = Screen.Home
    }


    fun setTab(tab: HomeTab) {
        _homeTab.value = tab
    }

    override fun onCleared() {
        connectionScope.cancel()
        super.onCleared()
    }
}