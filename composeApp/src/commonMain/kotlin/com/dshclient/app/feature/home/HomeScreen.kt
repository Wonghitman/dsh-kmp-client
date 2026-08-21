package com.dshclient.app.feature.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dshclient.app.AppViewModel
import com.dshclient.app.HomeTab
import com.dshclient.app.feature.sessions.SessionListScreen
import com.dshclient.app.feature.settings.SettingsScreen

@Composable
fun HomeScreen(vm: AppViewModel) {
    val tab by vm.homeTab.collectAsStateWithLifecycle()
    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == HomeTab.SESSIONS,
                    onClick = { vm.setTab(HomeTab.SESSIONS) },
                    icon = { Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null) },
                    label = { Text(HomeTab.SESSIONS.label) },
                )

                NavigationBarItem(
                    selected = tab == HomeTab.SETTINGS,
                    onClick = { vm.setTab(HomeTab.SETTINGS) },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text(HomeTab.SETTINGS.label) },
                )
            }
        },
    ) { padding ->
        val store = vm.store.value
        if (store == null) {
            Text("未连接", modifier = Modifier.padding(padding))
            return@Scaffold
        }
        Crossfade(targetState = tab, animationSpec = tween(250)) { t ->
            when (t) {
                HomeTab.SESSIONS -> SessionListScreen(vm, store, Modifier.padding(padding))
                HomeTab.SETTINGS -> SettingsScreen(vm, store, Modifier.padding(padding))
            }
        }
    }
}