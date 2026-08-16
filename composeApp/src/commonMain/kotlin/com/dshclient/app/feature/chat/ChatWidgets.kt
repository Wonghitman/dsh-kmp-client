package com.dshclient.app.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dshclient.app.core.model.ModelCatalogModel
import com.dshclient.app.core.model.ModelProviderGroup
import com.dshclient.app.core.model.QueueItem
import com.dshclient.app.core.model.str
import com.dshclient.app.data.PendingApproval
import com.dshclient.app.data.PendingQuestion
import com.dshclient.app.decodeImageBase64

/** Deep diving 状态条（与桌面版一致） */
@Composable
fun DeepDivingBar(seconds: Long) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(8.dp))
        Text(
            "Deep diving..." + (if (seconds >= 15) " " + formatDuration(seconds) else ""),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

fun formatDuration(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return m.toString().padStart(2, '0') + ":" + s.toString().padStart(2, '0')
}

/** Goal 状态条（底栏上方） */
@Composable
fun GoalBar(uiState: ChatUiState) {
    val phaseLabel = when (uiState.goalPhase) {
        "active" -> "进行中"
        "paused" -> "已暂停"
        "blocked" -> "受阻"
        "complete" -> "已完成"
        else -> uiState.goalPhase ?: ""
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
        ),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("🎯", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    uiState.goalObjective ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                uiState.goalMaxRounds?.let { max ->
                    val done = uiState.goalRounds ?: 0
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { if (max > 0) done.toFloat() / max.toFloat() else 0f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp)),
                    )
                    val rounds = if (done > 0) " · 第 " + done + " 轮" else ""
                    Text(
                        phaseLabel + rounds + " / 上限 " + max,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** 后台任务面板（左滑打开） */
@Composable
fun TaskPanel(uiState: ChatUiState, onClose: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("后台任务", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f))
                IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "关闭", Modifier.size(16.dp))
                }
            }
            if (uiState.jobs.isEmpty()) {
                Text("暂无后台任务", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            uiState.jobs.forEach { job ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val color = when (job.status) {
                        "running" -> Color(0xFF4CAF50)
                        "stopping" -> Color(0xFFFF9800)
                        "completed" -> Color(0xFF2196F3)
                        "failed" -> Color(0xFFF44336)
                        else -> Color(0xFF9E9E9E)
                    }
                    if (job.status == "running") {
                        CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp)
                    } else {
                        Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(color))
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(job.label, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        job.detail?.let {
                            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Text(job.status, style = MaterialTheme.typography.labelSmall, color = color)
                }
            }
        }
    }
}

/** 待发送队列 */
@Composable
fun QueueCard(
    items: List<QueueItem>,
    onEdit: (String, String) -> Unit,
    onRemove: (String) -> Unit,
    onSteer: (String) -> Unit,
) {
    var editingItem by remember { mutableStateOf<QueueItem?>(null) }
    var editText by remember { mutableStateOf("") }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)),
    ) {
        Column(Modifier.padding(10.dp)) {
            Text(
                "待发送（" + items.size + "）",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            items.forEach { item ->
                Row(
                    Modifier.fillMaxWidth().padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val text = item.message.content
                        .mapNotNull { (it as? kotlinx.serialization.json.JsonObject)?.str("text") }
                        .joinToString("")
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(MaterialTheme.shapes.small)
                            .clickable {
                                editingItem = item
                                editText = text
                            },
                    ) {
                        Text(
                            text.ifBlank { "(无文本)" },
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "位置: " + item.placement + " · 点击编辑",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { onSteer(item.id) }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Send, contentDescription = "立即发送", Modifier.size(16.dp))
                    }
                    IconButton(onClick = { onRemove(item.id) }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Clear, contentDescription = "删除", Modifier.size(16.dp))
                    }
                }
            }
        }
    }

    // 编辑对话框
    editingItem?.let { item ->
        AlertDialog(
            onDismissRequest = { editingItem = null },
            title = { Text("编辑待发送消息") },
            text = {
                OutlinedTextField(
                    value = editText,
                    onValueChange = { editText = it },
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = editText.isNotBlank(),
                    onClick = {
                        onEdit(item.id, editText)
                        editingItem = null
                    },
                ) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { editingItem = null }) { Text("取消") } },
        )
    }
}

/** 轮次与上下文指示 */
@Composable
fun TurnContextBar(uiState: ChatUiState) {
    val turnText = uiState.turn?.let { "第 " + it + " 轮" }
    val stepText = uiState.step?.let { "第 " + it + " 步" }
    val statsText = listOfNotNull(turnText, stepText).joinToString(" · ")
    val percent = uiState.contextPercent
    val window = uiState.contextWindow

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (statsText.isBlank()) "等待对话开始" else statsText,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (percent != null) {
            Spacer(Modifier.width(8.dp))
            androidx.compose.material3.LinearProgressIndicator(
                progress = { percent },
                modifier = Modifier
                    .width(64.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = when {
                    percent > 0.85f -> MaterialTheme.colorScheme.error
                    percent > 0.6f -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.primary
                },
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                (percent * 100).toInt().toString() + "%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (window != null) {
                Text(
                    " / " + formatTokens(window),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

fun formatTokens(tokens: Long): String {
    return when {
        tokens >= 1_000_000 -> (tokens / 1_000_000.0).toString().take(3) + "M"
        tokens >= 1_000 -> (tokens / 1_000.0).toString().take(4) + "K"
        else -> tokens.toString()
    }
}

/** 桌面风输入框：发送/停止内嵌，无内容不显示 */
@Composable
fun InputBar(
    input: String,
    sending: Boolean,
    running: Boolean,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onCancel: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        OutlinedTextField(
            value = input,
            onValueChange = onInputChange,
            modifier = Modifier
                .weight(1f)
                .onPreviewKeyEvent { event ->
                    // 回车发送（进入队列），Shift+回车换行
                    if (event.type == KeyEventType.KeyDown &&
                        (event.key == Key.Enter || event.key == Key.NumPadEnter) &&
                        !event.isShiftPressed &&
                        input.isNotBlank() && !sending
                    ) {
                        onSend()
                        true
                    } else {
                        false
                    }
                },
            placeholder = { Text("输入消息…") },
            maxLines = 5,
            trailingIcon = {
                if (running) {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.Stop, contentDescription = "停止", tint = MaterialTheme.colorScheme.error)
                    }
                } else if (input.isNotBlank() && !sending) {
                    IconButton(onClick = onSend) {
                        Icon(
                            Icons.Default.Send,
                            contentDescription = "发送",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            },
            shape = RoundedCornerShape(24.dp),
        )
    }
}

// ─────────────────────── 审批弹窗 ───────────────────────

@Composable
fun ApprovalDialog(
    approval: PendingApproval,
    onAnswer: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = { },
        title = { Text("等待审批") },
        text = {
            Column {
                Text("工具请求执行：" + approval.toolName, style = MaterialTheme.typography.bodyMedium)
                approval.reason?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onAnswer("allowed-once") }) {
                Icon(Icons.Default.Check, contentDescription = null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("允许一次")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = { onAnswer("rejected") }) {
                Icon(Icons.Default.Close, contentDescription = null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("拒绝")
            }
        },
    )
}

// ─────────────────────── 问题弹窗（逐题分步 + 自定义输入，对齐桌面版） ───────────────────────

/** 单题草稿 */
private class DraftState(
    var selected: List<String> = emptyList(),
    var custom: String = "",
    var skipped: Boolean = false,
)

private fun draftAnswered(draft: DraftState): Boolean =
    draft.selected.isNotEmpty() || draft.custom.isNotBlank()

@Composable
fun QuestionDialog(
    question: PendingQuestion,
    onAnswer: (List<com.dshclient.app.core.model.QuestionAnswer>) -> Unit,
) {
    val questions = question.questions
    var index by remember { mutableStateOf(0) }
    var drafts by remember { mutableStateOf(questions.map { DraftState() }) }
    var error by remember { mutableStateOf<String?>(null) }

    if (questions.isEmpty()) return
    val q = questions[index]
    val draft = drafts[index]

    val updateDraft = { update: (DraftState) -> Unit ->
        val next = drafts.toMutableList()
        update(next[index])
        drafts = next
        error = null
    }

    val choose = { label: String ->
        updateDraft { d ->
            if (q.multiSelect == true) {
                d.selected = if (label in d.selected) d.selected - label else d.selected + label
            } else {
                d.selected = listOf(label)
                d.custom = ""
            }
            d.skipped = false
        }
        // 单选直接进入下一题（对齐桌面版）
        if (q.multiSelect != true && index < questions.size - 1) {
            index += 1
        }
    }

    val skip = {
        val next = drafts.toMutableList()
        next[index] = DraftState(skipped = true)
        drafts = next
        error = null
        if (index < questions.size - 1) index += 1
    }

    fun submitAnswers() {
        val answers = questions.mapIndexed { i, item ->
            val d = drafts[i]
            if (d.skipped) {
                com.dshclient.app.core.model.QuestionAnswer(id = item.id, selected = emptyList())
            } else {
                val custom = d.custom.trim()
                com.dshclient.app.core.model.QuestionAnswer(
                    id = item.id,
                    selected = if (custom.isEmpty() || item.multiSelect == true) d.selected else emptyList(),
                    custom = custom.ifEmpty { null },
                )
            }
        }
        onAnswer(answers)
    }

    val submit = {
        val missing = drafts.indexOfFirst { !(draftAnswered(it) || it.skipped) }
        if (missing >= 0) {
            index = missing
            error = "请回答所有问题或跳过"
        } else {
            submitAnswers()
        }
    }

    AlertDialog(
        onDismissRequest = { },
        title = { Text("需要你的回答 (" + (index + 1) + "/" + questions.size + ")") },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(q.question, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                q.detail?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(8.dp))
                q.options?.forEach { option ->
                    val selected = option.label in draft.selected
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { choose(option.label) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(18.dp)
                                .clip(RoundedCornerShape(if (q.multiSelect == true) 4.dp else 10.dp))
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (selected) {
                                Icon(Icons.Default.Check, contentDescription = null, Modifier.size(12.dp),
                                    tint = MaterialTheme.colorScheme.onPrimary)
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(option.label, style = MaterialTheme.typography.bodyMedium)
                        option.description?.let {
                            Text(it, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                // 自定义输入（对齐桌面版 customRow）
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = draft.custom,
                    onValueChange = { value ->
                        updateDraft { d ->
                            d.custom = value
                            if (q.multiSelect != true) d.selected = emptyList()
                            d.skipped = false
                        }
                    },
                    placeholder = { Text("其他回答（可选）") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (error != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(error.orEmpty(), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = draftAnswered(draft) || draft.skipped,
                onClick = {
                    if (index < questions.size - 1) {
                        if (!draftAnswered(draft)) {
                            error = "请选择或输入回答"
                        } else {
                            index += 1
                            error = null
                        }
                    } else {
                        submit()
                    }
                },
            ) {
                Text(if (index < questions.size - 1) "下一题" else "提交回答")
            }
        },
        dismissButton = {
            TextButton(onClick = { skip() }) { Text("跳过") }
        },
    )
}

// ─────────────────────── 权限模式弹窗 ───────────────────────

@Composable
fun PermissionDialog(
    current: String?,
    options: List<PermissionOption>,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("权限模式") },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    "当前: " + (current ?: "未知"),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                options.forEach { opt ->
                    val selected = opt.value == current
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(opt.value) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected, onClick = null)
                        Column {
                            Text(opt.name, style = MaterialTheme.typography.bodyMedium)
                            opt.description?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                if (options.isEmpty()) {
                    Text("暂无可切换的权限预设", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}

// ─────────────────────── 模型选择弹窗（桌面版两级菜单） ───────────────────────

@Composable
fun ModelPickerDialog(
    current: com.dshclient.app.core.model.ModelSelection?,
    groups: List<ModelProviderGroup>,
    failures: List<com.dshclient.app.core.model.ModelCatalogFailure>,
    onDismiss: () -> Unit,
    onSelect: (String, String, String?) -> Unit,
) {
    // 两级状态：null = 模型列表；非 null = 该模型的思考模式选择
    var pickingEffortFor by remember { mutableStateOf<ModelCatalogModel?>(null) }

    if (pickingEffortFor == null) {
        // ── 第一级：模型列表（按提供商分组） ──
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("选择模型") },
            text = {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 460.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    groups.forEach { group ->
                        Text(
                            group.name,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                        )
                        group.models.forEach { model ->
                            val selected = current?.provider == group.id && current?.model == model.id
                            val effortName = modelEffortName(model, current)
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(MaterialTheme.shapes.small)
                                    .clickable {
                                        if (model.reasoning != null) {
                                            pickingEffortFor = model
                                        } else {
                                            onSelect(group.id, model.id, null)
                                        }
                                    }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = selected, onClick = null)
                                Column {
                                    Text(model.name, style = MaterialTheme.typography.bodyMedium)
                                    if (model.reasoning != null) {
                                        Text(
                                            "思考模式" + (effortName?.let { " · " + it } ?: ""),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    model.description?.let {
                                        Text(it, style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                    if (failures.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            failures.joinToString("\n") { f -> "加载失败: " + f.name + " — " + f.message },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 3,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text("取消") }
            },
        )
    } else {
        // ── 第二级：思考模式级别（含"提供商默认"） ──
        val model = pickingEffortFor!!
        val group = groups.firstOrNull { g -> g.models.any { it.id == model.id } }
        val provider = group?.id ?: ""
        val reasoning = model.reasoning
        val effectiveEffort = current?.reasoningEffort
        val choices = buildList<Pair<String?, String>> {
            if (reasoning?.defaultEffort == null) {
                add(null to "提供商默认")
            }
            reasoning?.efforts?.forEach { e ->
                add(e.id to e.name)
            }
        }

        AlertDialog(
            onDismissRequest = { pickingEffortFor = null },
            title = { Text("思考模式 · " + model.name) },
            text = {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    choices.forEach { (effortId, label) ->
                        val selected = if (effortId == null) effectiveEffort == null else effectiveEffort == effortId
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelect(provider, model.id, effortId)
                                    pickingEffortFor = null
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = selected, onClick = null)
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    reasoning?.efforts?.firstOrNull { it.id == effectiveEffort }?.description?.let { desc ->
                        Spacer(Modifier.height(6.dp))
                        Text(desc, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { pickingEffortFor = null }) { Text("返回") }
            },
        )
    }
}

/** 模型条目的思考模式标签（如 "思考模式 · 高"） */
fun modelEffortName(
    model: ModelCatalogModel,
    current: com.dshclient.app.core.model.ModelSelection?,
): String? {
    val reasoning = model.reasoning ?: return null
    val effortId = current?.reasoningEffort ?: reasoning.defaultEffort
    return reasoning.efforts.firstOrNull { it.id == effortId }?.name
}

// ─────────────────────── 图片查看 ───────────────────────

@Composable
fun AttachmentDialog(
    state: AttachmentDialogState,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(state.name ?: "图片") },
        text = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(360.dp),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    state.loading -> CircularProgressIndicator()
                    state.error != null -> Text("加载失败: " + state.error)
                    state.imageBase64 != null -> {
                        val bitmap = remember(state.imageBase64) {
                            decodeImageBase64(state.imageBase64)
                        }
                        if (bitmap != null) {
                            androidx.compose.foundation.Image(
                                bitmap = bitmap,
                                contentDescription = state.name,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            Text("图片解码失败")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}