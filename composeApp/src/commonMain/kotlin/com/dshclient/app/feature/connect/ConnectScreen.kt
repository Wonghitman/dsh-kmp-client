package com.dshclient.app.feature.connect

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectScreen(vm: AppViewModel) {
    val connecting by vm.connecting.collectAsState()
    val error by vm.connectError.collectAsState()
    var url by remember { mutableStateOf(vm.appSettings.baseUrl) }
    val recents = remember { vm.appSettings.recentServers() }
    var showGuide by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("连接 DSH 服务器") })
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
                .imePadding(),
        ) {
            Spacer(Modifier.height(24.dp))
            Icon(
                Icons.Default.Cloud,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(12.dp))
            Text("DSH Mobile", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "连接局域网或 Tailscale 中的 DeepSeek Harness",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))

            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("服务器地址") },
                placeholder = { Text("例如 http://100.x.x.x:3080 或 192.168.1.10:3080") },
                supportingText = { Text("格式：host:port，可省略 http:// 前缀") },
                singleLine = true,
            )
            Spacer(Modifier.height(16.dp))
            TextButton(
                onClick = { vm.launchConnect(url) },
                enabled = !connecting && url.isNotBlank(),
                modifier = Modifier.align(Alignment.End),
            ) {
                if (connecting) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.size(8.dp))
                }
                Text(if (connecting) "连接中…" else "连接")
            }

            if (error != null) {
                Spacer(Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text("连接失败", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                        Text(error.orEmpty(), style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { showGuide = !showGuide }) {
                            Text(if (showGuide) "收起排查指南" else "排查指南")
                        }
                    }
                }
            }

            if (showGuide || error != null) {
                Spacer(Modifier.height(12.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("排查指南", fontWeight = FontWeight.Bold)
                        GuideRow("1", "服务器需监听 0.0.0.0 并允许局域网", "桌面端以 DSH_PKG_ALLOW_LAN=1 环境变量启动 dsh web，例如：\n  DSH_PKG_ALLOW_LAN=1 dsh web --host 0.0.0.0 --port 3080")
                        GuideRow("2", "信任围栏需包含当前地址", "DSH 默认只信任本机回环地址。远程访问需声明：\n  dsh web --host 0.0.0.0 --trusted-host <本机局域网IP或Tailscale IP>")
                        GuideRow("3", "403 表示被信任围栏拒绝", "把手机访问所用的 IP 加入 --trusted-host（可多次声明）后重启 dsh web")
                        GuideRow("4", "提示", "Tailscale 场景：--trusted-host 100.x.x.x（你的 Tailscale IP）")
                    }
                }
            }

            if (recents.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text("最近连接", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    recents.forEach { r ->
                        AssistChip(
                            onClick = {
                                url = r
                                vm.launchConnect(r)
                            },
                            label = { Text(r, maxLines = 1) },
                            leadingIcon = { Icon(Icons.Default.Wifi, contentDescription = null, Modifier.size(16.dp)) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun GuideRow(num: String, title: String, detail: String) {
    Column {
        Text("$num. $title", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
        Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}