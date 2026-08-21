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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dshclient.app.AppViewModel
import com.dshclient.app.core.network.ConnectionStatus
import com.dshclient.app.data.DshStore
import com.dshclient.app.designsystem.extendedColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: AppViewModel, store: DshStore, modifier: Modifier = Modifier) {
    val hostInfo by store.hostInfo.collectAsStateWithLifecycle()
    val connection by vm.connection.collectAsStateWithLifecycle()
    val connState by (connection?.state ?: remember { kotlinx.coroutines.flow.MutableStateFlow(com.dshclient.app.core.network.ConnectionState()) }).collectAsStateWithLifecycle()
    var autoConnect by remember { mutableStateOf(vm.appSettings.autoConnect) }

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text("设置", style = MaterialTheme.typography.titleLarge) }) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // 连接状态
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("连接状态", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(10.dp))
                    val statusColor = when (connState.status) {
                        ConnectionStatus.CONNECTED -> MaterialTheme.extendedColors.success
                        ConnectionStatus.CONNECTING, ConnectionStatus.RECONNECTING -> MaterialTheme.extendedColors.warning
                        ConnectionStatus.FAILED -> MaterialTheme.colorScheme.error
                        ConnectionStatus.DISCONNECTED -> MaterialTheme.colorScheme.outline
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (connState.status == ConnectionStatus.CONNECTED) Icons.Default.CheckCircle
                            else Icons.Default.CloudOff,
                            contentDescription = null,
                            tint = statusColor,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            when (connState.status) {
                                ConnectionStatus.CONNECTED -> "已连接"
                                ConnectionStatus.CONNECTING -> "连接中…"
                                ConnectionStatus.RECONNECTING -> "重连中（第 " + connState.retryCount + " 次）"
                                ConnectionStatus.FAILED -> "连接失败"
                                ConnectionStatus.DISCONNECTED -> "未连接"
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    if (connState.message != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            connState.message.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    hostInfo?.let { info ->
                        Spacer(Modifier.height(10.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        Spacer(Modifier.height(10.dp))
                        Text("服务器版本: " + info.version, style = MaterialTheme.typography.bodySmall)
                        Text("工作目录: " + info.cwd, style = MaterialTheme.typography.bodySmall)
                        info.model?.let {
                            Text("当前模型: " + it, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    TextButton(
                        onClick = { vm.disconnect() },
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        Text("断开连接", color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            // 模型列表
            ModelListCard(vm, store)

            // 偏好
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("偏好", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("启动时自动连接", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text(
                                "打开应用时自动连接上次的服务器",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
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
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            modifier = Modifier.width(20.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("关于", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("DSH Mobile — DeepSeek Harness 移动客户端", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "协议版本: dsh-web-app 0.1.0-rc.6",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "特权功能（设置/凭据/目录选择）仅限服务器本机回环访问",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "模型目录",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { load() }) {
                    Text(if (loaded) "刷新" else "加载")
                }
            }
            if (models.isEmpty() && !loaded) {
                Text(
                    "点击加载可用模型（由服务器 llm.providers / llm.models 提供）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            models.forEach { group ->
                Spacer(Modifier.height(8.dp))
                Text(
                    group.name,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                group.models.forEach { m ->
                    Text("· " + m.name, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 1.dp))
                }
            }
        }
    }
}