package com.dshclient.app.feature.workspaces

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dshclient.app.AppViewModel
import com.dshclient.app.core.model.ApiMethods
import com.dshclient.app.core.model.WorkspaceCreateValue
import com.dshclient.app.core.model.WorkspaceRenameValue
import com.dshclient.app.data.DshStore
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceScreen(vm: AppViewModel, store: DshStore, modifier: Modifier = Modifier) {
    val workspaces by store.workspaces.collectAsStateWithLifecycle()
    val archived by store.archivedSessionIds.collectAsStateWithLifecycle()
    var showCreate by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("工作区", style = MaterialTheme.typography.titleLarge) },
                actions = {
                    IconButton(onClick = { scope.launch { store.refreshWorkspaces() } }) {
                        Icon(Icons.Default.FolderOpen, contentDescription = "刷新")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreate = true },
                shape = MaterialTheme.shapes.large,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Icon(Icons.Default.Add, contentDescription = "新建工作区")
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (archived.isNotEmpty()) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Archive,
                            contentDescription = null,
                            modifier = Modifier.width(18.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "已归档会话 (" + archived.size + ")",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            if (workspaces.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) {
                        Text(
                            "暂无工作区",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
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
            shape = MaterialTheme.shapes.large,
            title = { Text("新建工作区", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column {
                    Text("输入 PC 上的现有目录路径（如 D:/Projects）", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = path,
                        onValueChange = { path = it },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
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
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable { /* 展开会话列表 */ },
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { renaming = true }) {
                    Text("改名", style = MaterialTheme.typography.labelMedium)
                }
            }
            Text(path, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(
                sessionCount.toString() + " 个会话",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }

    if (renaming) {
        AlertDialog(
            onDismissRequest = { renaming = false },
            shape = MaterialTheme.shapes.large,
            title = { Text("重命名工作区", style = MaterialTheme.typography.titleLarge) },
            text = {
                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
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
