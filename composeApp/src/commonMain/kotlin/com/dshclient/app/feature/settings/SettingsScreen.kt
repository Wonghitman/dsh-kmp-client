package com.dshclient.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dshclient.app.AppViewModel
import com.dshclient.app.data.DshStore
import kotlinx.coroutines.launch
import com.dshclient.app.core.network.ConnectionStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: AppViewModel, store: DshStore, modifier: Modifier = Modifier) {
    val hostInfo by store.hostInfo.collectAsState()
    val connection by vm.connection.collectAsState()
    val connState by (connection?.state ?: remember { kotlinx.coroutines.flow.MutableStateFlow(com.dshclient.app.core.network.ConnectionState()) }).collectAsState()
    var autoConnect by remember { mutableStateOf(vm.appSettings.autoConnect) }

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text("设置") }) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 连接状态
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("连接状态", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (connState.status == ConnectionStatus.CONNECTED) Icons.Default.CheckCircle
                            else Icons.Default.CloudOff,
                            contentDescription = null,
                            tint = if (connState.status == ConnectionStatus.CONNECTED)
                                MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            when (connState.status) {
                                ConnectionStatus.CONNECTED -> "已连接"
                                ConnectionStatus.CONNECTING -> "连接中…"
                                ConnectionStatus.RECONNECTING -> "重连中（第 " + connState.retryCount + " 次）"
                                ConnectionStatus.FAILED -> "连接失败"
                                ConnectionStatus.DISCONNECTED -> "未连接"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    if (connState.message != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(connState.message.orEmpty(), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    hostInfo?.let { info ->
                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(8.dp))
                        Text("服务器版本: " + info.version, style = MaterialTheme.typography.bodySmall)
                        Text("工作目录: " + info.cwd, style = MaterialTheme.typography.bodySmall)
                        info.model?.let {
                            Text("当前模型: " + it, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { vm.disconnect() }) {
                        Text("断开连接")
                    }
                }
            }

            // 模型列表
            ModelListCard(vm, store)

            // 偏好
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("偏好", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("启动时自动连接", style = MaterialTheme.typography.bodyMedium)
                            Text("打开应用时自动连接上次的服务器", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = autoConnect,
                            onCheckedChange = {
                                autoConnect = it
                                vm.appSettings.autoConnect = it
                            },
                        )
                    }
                }
            }

            // 关于
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, contentDescription = null, Modifier.width(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("关于", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("DSH Mobile — DeepSeek Harness 移动客户端", style = MaterialTheme.typography.bodySmall)
                    Text("协议版本: dsh-web-app 0.1.0-rc.6", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("特权功能（设置/凭据/目录选择）仅限服务器本机回环访问",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ModelListCard(vm: AppViewModel, store: DshStore) {
    var models by remember { mutableStateOf<List<com.dshclient.app.core.model.ModelProviderGroup>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    fun load() {
        scope.launch {
            try {
                val value = store.connection.call(
                    com.dshclient.app.core.model.ApiMethods.LLM_MODELS,
                    kotlinx.serialization.json.JsonObject(emptyMap()),
                    com.dshclient.app.core.model.LlmModelsValue.serializer(),
                )
                models = value.groups
                loaded = true
            } catch (e: Exception) {
                loaded = false
            }
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("模型目录", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f))
                TextButton(onClick = { load() }) {
                    Text(if (loaded) "刷新" else "加载")
                }
            }
            if (models.isEmpty() && !loaded) {
                Text("点击加载可用模型（由服务器 llm.providers / llm.models 提供）",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            models.forEach { group ->
                Spacer(Modifier.height(6.dp))
                Text(group.name, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                group.models.forEach { m ->
                    Text("· " + m.name, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}