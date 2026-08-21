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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dshclient.app.AppViewModel
import com.dshclient.app.core.model.JobView
import com.dshclient.app.data.DshStore
import com.dshclient.app.designsystem.extendedColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobsScreen(vm: AppViewModel, store: DshStore, modifier: Modifier = Modifier) {
    val sessions by store.sessions.collectAsStateWithLifecycle()
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
                title = { Text("后台任务", style = MaterialTheme.typography.titleLarge) },
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
                Text(
                    "暂无后台任务",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(jobsBySession, key = { it.first }) { (sessionId, jobs) ->
                    Column(Modifier.fillMaxWidth()) {
                        Text(
                            sessionId.take(8),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(6.dp))
                        jobs.forEach { job ->
                            JobRow(job)
                            Spacer(Modifier.height(6.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun JobRow(job: JobView) {
    val extended = MaterialTheme.extendedColors
    val statusColor = when (job.status) {
        "running" -> extended.running
        "stopping" -> extended.warning
        "completed" -> extended.success
        "failed" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.outline
    }

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (job.status == "running") {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = extended.running,
                )
            } else {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(statusColor),
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    job.label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
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
                fontWeight = FontWeight.Medium,
            )
        }
    }
}