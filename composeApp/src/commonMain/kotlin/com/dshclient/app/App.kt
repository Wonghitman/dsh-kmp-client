package com.dshclient.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dshclient.app.designsystem.DshTheme
import com.dshclient.app.feature.chat.ChatScreen
import com.dshclient.app.feature.connect.ConnectScreen
import com.dshclient.app.feature.home.HomeScreen

/**
 * 应用根：M3 Expressive 风格主题 + 导航。
 * Now in Android 规范：单一 AppViewModel 持有应用级状态，feature 屏通过 UiState 渲染。
 */
@Composable
fun App() {
    DshTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            val vm: AppViewModel = viewModel { AppViewModel() }
            val screen by vm.screen.collectAsStateWithLifecycle()
            when (val s = screen) {
                is Screen.Connect -> ConnectScreen(vm)
                is Screen.Home -> HomeScreen(vm)
                is Screen.Chat -> ChatScreen(vm, s.sessionId)
            }
        }
    }
}
