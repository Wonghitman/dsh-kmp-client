package com.dshclient.app.feature.sessions

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dshclient.app.AppViewModel
import com.dshclient.app.data.DshStore
import com.dshclient.app.data.SessionListItem
import com.dshclient.app.core.model.ApiMethods
import com.dshclient.app.core.model.SessionCreateValue
import com.dshclient.app.core.model.SessionSearchValue
import com.dshclient.app.core.model.WorkspaceCreateValue
import com.dshclient.app.core.model.WorkspaceView
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** 一个工作区分组 */
private data class GroupData(
    val title: String,
    val sessions: List<SessionListItem>,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionListScreen(vm: AppViewModel, store: DshStore, modifier: Modifier = Modifier) {
    val sessions by store.sessions.collectAsState()
    val workspaces by store.workspaces.collectAsState()
    val archivedIds by store.archivedSessionIds.collectAsState()
    val sessionJobs by store.sessionJobs.collectAsState()
    var refreshing by remember { mutableStateOf(false) }
    var searchActive by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    // 已折叠的分组标题
    var collapsedGroups by remember { mutableStateOf<Set<String>>(emptySet()) }

    // 按工作区分组
    val byId = sessions.associateBy { it.summary.sessionId }
    val archivedSet = archivedIds.toSet()
    val groups = buildList {
        workspaces.forEach { ws ->
            val wsSessions = ws.sessionIds.mapNotNull { byId[it] }
            if (wsSessions.isNotEmpty()) {
                add(GroupData(ws.title, wsSessions))
            }
        }
        val ungrouped = sessions.filter {
            val inWs = workspaces.any { ws -> it.summary.sessionId in ws.sessionIds }
            !inWs && it.summary.sessionId !in archivedSet
        }
        if (ungrouped.isNotEmpty()) {
            add(GroupData("其他会话", ungrouped))
        }
        val archived = sessions.filter { it.summary.sessionId in archivedSet }
        if (archived.isNotEmpty()) {
            add(GroupData("已归档", archived))
        }
    }

    fun refresh() {
        refreshing = true
        scope.launch {
            store.refreshSessions()
            store.refreshWorkspaces()
            refreshing = false
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (searchActive) {
            SessionSearchView(vm, store, query, onQueryChange = { query = it }, onClose = { searchActive = false })
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "会话 (" + sessions.size + ")",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { refresh() }) {
                            if (refreshing) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = "刷新")
                            }
                        }
                        IconButton(onClick = { searchActive = true }) {
                            Icon(Icons.Default.Search, contentDescription = "搜索")
                        }
                    }
                }
                if (groups.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(top = 80.dp), contentAlignment = Alignment.Center) {
                            Text("暂无会话，点击右下角新建", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                groups.forEach { group ->
                    val collapsed = group.title in collapsedGroups
                    item(key = "header_" + group.title) {
                        val ws = workspaces.firstOrNull { it.title == group.title }
                        GroupHeader(
                            title = group.title,
                            collapsed = collapsed,
                            workspaceId = ws?.workspaceId,
                            isWorkspace = ws != null,
                            onToggle = {
                                collapsedGroups = if (collapsed) collapsedGroups - group.title
                                else collapsedGroups + group.title
                            },
                            onRename = { newTitle ->
                                if (ws != null) {
                                    scope.launch { store.renameWorkspace(ws.workspaceId, newTitle) }
                                }
                            },
                            onDelete = {
                                if (ws != null) {
                                    scope.launch { store.deleteWorkspace(ws.workspaceId) }
                                }
                            },
                            onCreateSession = {
                                scope.launch {
                                    val id = if (ws != null) {
                                        store.createSessionInWorkspace(ws.workspaceId)
                                    } else {
                                        val cwd = store.hostInfo.value?.cwd
                                        val value = store.connection.call(
                                            ApiMethods.SESSION_CREATE,
                                            buildJsonObject {
                                                if (cwd != null) put("cwd", JsonPrimitive(cwd))
                                            },
                                            SessionCreateValue.serializer(),
                                        )
                                        value.sessionId
                                    }
                                    if (id != null) vm.openChat(id)
                                }
                            },
                        )
                    }
                    if (!collapsed) {
                        items(group.sessions, key = { "s_" + it.summary.sessionId }) { item ->
                            SessionRow(
                                item = item,
                                jobs = sessionJobs[item.summary.sessionId].orEmpty(),
                                onClick = { vm.openChat(item.summary.sessionId) },
                            )
                        }
                    }
                }
            }
            NewSessionFab(vm, store, workspaces, Modifier.align(Alignment.BottomEnd).padding(24.dp))
        }
    }
}

/** 分组标题栏：工作区可操作 */
@Composable
private fun GroupHeader(
    title: String,
    collapsed: Boolean,
    workspaceId: String?,
    isWorkspace: Boolean,
    onToggle: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onCreateSession: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var newTitle by remember { mutableStateOf(title) }
    var confirmDelete by remember { mutableStateOf(false) }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 2.dp)
            .clip(MaterialTheme.shapes.small)
            .clickable { onToggle() }
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (collapsed) "▸ " + title else "▾ " + title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        Text(
            "⋯",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clip(MaterialTheme.shapes.small)
                .clickable { menuOpen = true },
        )
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text("在该工作区新建会话") },
                onClick = {
                    menuOpen = false
                    onCreateSession()
                },
            )
            if (isWorkspace) {
                DropdownMenuItem(
                    text = { Text("重命名工作区") },
                    onClick = {
                        menuOpen = false
                        renaming = true
                    },
                )
                DropdownMenuItem(
                    text = { Text("删除工作区") },
                    onClick = {
                        menuOpen = false
                        confirmDelete = true
                    },
                )
            }
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
                        onRename(newTitle.trim())
                    },
                ) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("取消") } },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除工作区") },
            text = { Text("删除工作区「" + title + "」？其中的会话将变为未分组。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } },
        )
    }
}

/** 新建 FAB：选工作区建会话 / 直接新建 / 新建工作区 */
@Composable
private fun NewSessionFab(
    vm: AppViewModel,
    store: DshStore,
    workspaces: List<WorkspaceView>,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var showNewWorkspace by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Box(modifier) {
        FloatingActionButton(onClick = { menuOpen = true }) {
            Icon(Icons.Default.Add, contentDescription = "新建")
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            workspaces.forEach { ws ->
                DropdownMenuItem(
                    text = { Text("新会话 · " + ws.title, maxLines = 1) },
                    onClick = {
                        menuOpen = false
                        scope.launch {
                            val id = store.createSessionInWorkspace(ws.workspaceId)
                            if (id != null) vm.openChat(id)
                        }
                    },
                )
            }
            DropdownMenuItem(
                text = { Text("直接新建会话") },
                onClick = {
                    menuOpen = false
                    scope.launch {
                        val cwd = store.hostInfo.value?.cwd
                        val value = store.connection.call(
                            ApiMethods.SESSION_CREATE,
                            buildJsonObject {
                                if (cwd != null) put("cwd", JsonPrimitive(cwd))
                            },
                            SessionCreateValue.serializer(),
                        )
                        vm.openChat(value.sessionId)
                    }
                },
            )
            DropdownMenuItem(
                text = { Text("新建工作区…") },
                onClick = {
                    menuOpen = false
                    showNewWorkspace = true
                },
            )
        }
    }

    if (showNewWorkspace) {
        var path by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewWorkspace = false },
            title = { Text("新建工作区") },
            text = {
                Column {
                    Text("输入 PC 上的现有目录路径", style = MaterialTheme.typography.bodySmall)
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
                        showNewWorkspace = false
                        scope.launch {
                            store.connection.call(
                                ApiMethods.WORKSPACE_CREATE,
                                buildJsonObject {
                                    put("path", JsonPrimitive(path.trim()))
                                },
                                WorkspaceCreateValue.serializer(),
                            )
                            store.refreshWorkspaces()
                        }
                    },
                ) { Text("创建") }
            },
            dismissButton = { TextButton(onClick = { showNewWorkspace = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun SessionRow(
    item: SessionListItem,
    jobs: List<com.dshclient.app.core.model.JobView>,
    onClick: () -> Unit,
) {
    val s = item.summary
    val hasRunningJob = jobs.any { it.status == "running" || it.status == "stopping" }
    val hasDoneJob = jobs.isNotEmpty() && !hasRunningJob
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                MaterialTheme.shapes.medium,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                item.title ?: sessionDisplayName(s),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            // 状态徽章：工作中 / 任务完成
            if (s.running || hasRunningJob) {
                Row(
                    Modifier
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(10.dp), strokeWidth = 1.5.dp)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "工作中",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            } else if (hasDoneJob) {
                Row(
                    Modifier
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "✓",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        "任务完成",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(6.dp))
            Text(
                formatTime(s.updatedAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            s.cwd ?: (s.agentPreset?.let { "预设: " + it } ?: ""),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun sessionDisplayName(s: com.dshclient.app.core.model.SessionSummary): String {
    val base = s.cwd?.substringAfterLast('\\') ?: s.cwd
    return base ?: s.sessionId.take(8)
}

private fun formatTime(epochMs: Long): String {
    if (epochMs <= 0) return ""
    return try {
        val dt = Instant.fromEpochMilliseconds(epochMs)
            .toLocalDateTime(TimeZone.currentSystemDefault())
        dt.hour.toString().padStart(2, '0') + ":" + dt.minute.toString().padStart(2, '0')
    } catch (e: Exception) {
        ""
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionSearchView(
    vm: AppViewModel,
    store: DshStore,
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
) {
    var results by remember { mutableStateOf<List<com.dshclient.app.core.model.SessionSearchItem>>(emptyList()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(query) {
        if (query.isBlank()) {
            results = emptyList()
            return@LaunchedEffect
        }
        kotlinx.coroutines.delay(300)
        try {
            val value = store.connection.call(
                ApiMethods.SESSION_SEARCH,
                buildJsonObject { put("query", JsonPrimitive(query)) },
                SessionSearchValue.serializer(),
            )
            results = value.items
        } catch (e: Exception) {
            results = emptyList()
        }
    }

    Column(Modifier.fillMaxSize()) {
        SearchBar(
            query = query,
            onQueryChange = onQueryChange,
            onSearch = {},
            active = true,
            onActiveChange = { if (!it) onClose() },
            placeholder = { Text("搜索会话内容") },
            modifier = Modifier.fillMaxWidth().padding(8.dp),
        ) {
            LazyColumn {
                items(results, key = { it.sessionId }) { r ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable { vm.openChat(r.sessionId) }
                            .padding(12.dp),
                    ) {
                        Text(r.snippet, maxLines = 3, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            r.sessionId.take(8),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}