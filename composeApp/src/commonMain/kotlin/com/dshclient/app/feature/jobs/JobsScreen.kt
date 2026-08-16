package com.dshclient.app.feature.jobs

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dshclient.app.AppViewModel
import com.dshclient.app.data.DshStore
import com.dshclient.app.core.model.JobView
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobsScreen(vm: AppViewModel, store: DshStore, modifier: Modifier = Modifier) {
    val sessions by store.sessions.collectAsState()
    val scope = rememberCoroutineScope()

    // 聚合每个会话的 jobs（从 ChatState 读取）
    val jobsBySession: List<Pair<String, List<JobView>>> = sessions.mapNotNull { item ->
        val chat = store.chatStateOrNull(item.summary.sessionId)
        val jobs: List<JobView> = chat?.jobs?.value ?: emptyList()
        if (jobs.isEmpty()) null else item.summary.sessionId to jobs
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("后台任务") },
                actions = {
                    IconButton(onClick = {
                        scope.launch {
                            store.refreshSessions()
                            for (s in store.sessions.value) {
                                store.loadHistory(s.summary.sessionId, maxMessages = 1)
                            }
                        }
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                },
            )
        },
    ) { padding ->
        if (jobsBySession.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("暂无后台任务", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(jobsBySession, key = { it.first }) { (sessionId, jobs) ->
                    Column(Modifier.fillMaxWidth()) {
                        Text(
                            sessionId.take(8),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(6.dp))
                        jobs.forEach { job -> JobRow(job) }
                    }
                }
            }
        }
    }
}

@Composable
private fun JobRow(job: JobView) {
    val statusColor = when (job.status) {
        "running" -> Color(0xFF4CAF50)
        "stopping" -> Color(0xFFFF9800)
        "completed" -> Color(0xFF2196F3)
        "killed" -> Color(0xFF9E9E9E)
        "failed" -> Color(0xFFF44336)
        else -> Color(0xFF9E9E9E)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), MaterialTheme.shapes.medium)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (job.status == "running") {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        } else {
            Box(Modifier.size(10.dp).background(statusColor, CircleShape))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(job.label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (job.detail != null) {
                Text(
                    job.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            job.status,
            style = MaterialTheme.typography.labelSmall,
            color = statusColor,
        )
    }
}