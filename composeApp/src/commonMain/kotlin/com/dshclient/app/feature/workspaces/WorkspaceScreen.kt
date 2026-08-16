package com.dshclient.app.feature.workspaces

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dshclient.app.AppViewModel
import com.dshclient.app.data.DshStore
import com.dshclient.app.core.model.ApiMethods
import com.dshclient.app.core.model.WorkspaceCreateValue
import com.dshclient.app.core.model.WorkspaceRenameValue
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceScreen(vm: AppViewModel, store: DshStore, modifier: Modifier = Modifier) {
    val workspaces by store.workspaces.collectAsState()
    val archived by store.archivedSessionIds.collectAsState()
    var showCreate by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("工作区") },
                actions = {
                    IconButton(onClick = { scope.launch { store.refreshWorkspaces() } }) {
                        Icon(Icons.Default.FolderOpen, contentDescription = "刷新")
                    }
                },
            )
        },
        floatingActionButton = {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
                androidx.compose.material3.FloatingActionButton(onClick = { showCreate = true }) {
                    Icon(Icons.Default.Add, contentDescription = "新建工作区")
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (archived.isNotEmpty()) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Archive, contentDescription = null, Modifier.width(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("已归档会话 (" + archived.size + ")", style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
            if (workspaces.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) {
                        Text("暂无工作区", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            items(workspaces, key = { it.workspaceId }) { ws ->
                WorkspaceCard(vm, store, ws.title, ws.path, ws.sessionIds.size)
            }
        }
    }

    if (showCreate) {
        var path by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("新建工作区") },
            text = {
                Column {
                    Text("输入 PC 上的现有目录路径（如 D:/Projects）", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = path,
                        onValueChange = { path = it },
                        singleLine = true,
                        label = { Text("目录路径") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = path.isNotBlank(),
                    onClick = {
                        showCreate = false
                        scope.launch {
                            try {
                                store.connection.call(
                                    ApiMethods.WORKSPACE_CREATE,
                                    buildJsonObject { put("path", path.trim()) },
                                    WorkspaceCreateValue.serializer(),
                                )
                                store.refreshWorkspaces()
                            } catch (e: Exception) {
                            }
                        }
                    },
                ) { Text("创建") }
            },
            dismissButton = {
                TextButton(onClick = { showCreate = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun WorkspaceCard(
    vm: AppViewModel,
    store: DshStore,
    title: String,
    path: String,
    sessionCount: Int,
) {
    var renaming by remember { mutableStateOf(false) }
    var newTitle by remember { mutableStateOf(title) }
    val scope = rememberCoroutineScope()

    Card(
        modifier = Modifier.fillMaxWidth().clickable { /* 展开会话列表 */ },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { renaming = true }) {
                    Text("改名", style = MaterialTheme.typography.labelMedium)
                }
            }
            Text(path, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                sessionCount.toString() + " 个会话",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (renaming) {
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("重命名工作区") },
            text = {
                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = newTitle.isNotBlank(),
                    onClick = {
                        renaming = false
                        scope.launch {
                            try {
                                store.connection.call(
                                    ApiMethods.WORKSPACE_RENAME,
                                    buildJsonObject { put("title", newTitle.trim()) },
                                    WorkspaceRenameValue.serializer(),
                                )
                                store.refreshWorkspaces()
                            } catch (e: Exception) {
                            }
                        }
                    },
                ) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("取消") } },
        )
    }
}
