package com.dshclient.app.feature.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dshclient.app.AppViewModel
import com.dshclient.app.core.model.long
import com.dshclient.app.core.model.obj
import com.dshclient.app.core.model.str
import com.dshclient.app.data.PendingApproval
import com.dshclient.app.data.PendingQuestion
import com.dshclient.app.data.RenderedBlock
import com.dshclient.app.data.RenderedMessage
import com.dshclient.app.designsystem.extendedColors

@Composable
fun ChatScreen(vm: AppViewModel, sessionId: String) {
    val store = vm.store.value ?: return
    val chatVm: ChatViewModel = viewModel(
        key = "chat_" + sessionId,
        factory = ChatViewModelFactory(store, sessionId),
    )
    val uiState by chatVm.uiState.collectAsStateWithLifecycle()

    ChatScaffold(
        uiState = uiState,
        onBack = { vm.backToHome() },
        onViewModeChange = chatVm::setViewMode,
        onInputChange = chatVm::onInputChange,
        onSend = chatVm::send,
        onCancel = chatVm::cancelTurn,
        onLoadMore = chatVm::loadMore,
        onAnswerQuestion = chatVm::answerQuestion,
        onAnswerApproval = chatVm::answerApproval,
        onEditQueueItem = chatVm::editQueueItem,
        onRemoveQueueItem = chatVm::removeQueueItem,
        onSteerQueueItem = chatVm::steerQueueItem,
        onOpenAttachment = chatVm::openAttachment,
        onCloseAttachment = chatVm::closeAttachment,
        onShowModelPicker = chatVm::showModelPicker,
        onSelectModel = chatVm::selectModel,
        onShowPermissionPicker = chatVm::showPermissionPicker,
        onSetPermission = chatVm::setPermission,
    )
}

class ChatViewModelFactory(
    private val repository: com.dshclient.app.data.DshRepository,
    private val sessionId: String,
) : androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
        ChatViewModel(repository, sessionId) as T
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScaffold(
    uiState: ChatUiState,
    onBack: () -> Unit,
    onViewModeChange: (ChatViewMode) -> Unit,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onCancel: () -> Unit,
    onLoadMore: () -> Unit,
    onAnswerQuestion: (PendingQuestion, List<com.dshclient.app.core.model.QuestionAnswer>) -> Unit,
    onAnswerApproval: (PendingApproval, String) -> Unit,
    onEditQueueItem: (String, String) -> Unit,
    onRemoveQueueItem: (String) -> Unit,
    onSteerQueueItem: (String) -> Unit,
    onOpenAttachment: (String, String?) -> Unit,
    onCloseAttachment: () -> Unit,
    onShowModelPicker: (Boolean) -> Unit,
    onSelectModel: (String, String, String?) -> Unit,
    onShowPermissionPicker: (Boolean) -> Unit,
    onSetPermission: (String) -> Unit,
) {
    val listState = rememberLazyListState()
    var showTasks by remember { mutableStateOf(false) }

    // 首次进入/首次加载完成后定位到最新消息（reverseLayout 下 index 0 即底部）
    LaunchedEffect(Unit) {
        listState.scrollToItem(0)
    }

    // 滑到接近顶部时自动拉取上一页（reverseLayout：最大 index = 视觉顶部）
    LaunchedEffect(uiState.hasMore, uiState.messages.size) {
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        }.collect { lastVisibleIndex ->
            val total = uiState.messages.size + if (uiState.hasMore) 1 else 0
            if (uiState.hasMore && !uiState.loadingMore && total > 0 && lastVisibleIndex >= total - 4) {
                onLoadMore()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            uiState.title ?: uiState.sessionId.take(8),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (uiState.running) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(9.dp),
                                    strokeWidth = 1.5.dp,
                                    color = MaterialTheme.extendedColors.running,
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    "运行中",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.extendedColors.running,
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier
                                    .clip(MaterialTheme.shapes.small)
                                    .clickable { onShowModelPicker(true) },
                            ) {
                                Text(
                                    modelTriggerLabel(uiState),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                )
                            }
                            Spacer(Modifier.width(6.dp))
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier
                                    .clip(MaterialTheme.shapes.small)
                                    .clickable { onShowPermissionPicker(true) },
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                ) {
                                    Icon(
                                        Icons.Default.Lock,
                                        contentDescription = "权限模式",
                                        modifier = Modifier.size(12.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    if (uiState.permissionCurrent != null) {
                                        Spacer(Modifier.width(3.dp))
                                        Text(
                                            uiState.permissionCurrent,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {},
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (uiState.error != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Text(
                        uiState.error,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }

            // Deep diving 状态条
            if (uiState.deepDiving) {
                DeepDivingBar(uiState.deepDivingSeconds)
            }

            // Goal 状态条
            if (uiState.goalObjective != null) {
                GoalBar(uiState)
            }

            // 待发送队列
            if (uiState.queue.isNotEmpty()) {
                QueueCard(
                    items = uiState.queue,
                    onEdit = onEditQueueItem,
                    onRemove = onRemoveQueueItem,
                    onSteer = onSteerQueueItem,
                )
            }

            // 后台任务面板（左滑打开）
            AnimatedVisibility(
                visible = showTasks,
                enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
                exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
            ) {
                TaskPanel(
                    uiState = uiState,
                    onClose = { showTasks = false },
                )
            }

            // 消息区（左滑手势打开任务面板）
            LazyColumn(
                state = listState,
                reverseLayout = true,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragEnd = { },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                if (dragAmount < -60) showTasks = true
                            },
                        )
                    },
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // 最新消息在 index 0（视觉底部），新消息自动贴底
                items(uiState.messages.reversed(), key = { it.seq }) { message ->
                    MessageRow(
                        message = message,
                        onOpenAttachment = { attachmentId, name -> onOpenAttachment(attachmentId, name) },
                    )
                }
                // 加载更早按钮（reverseLayout 下在列表末尾 = 视觉顶部）
                if (uiState.hasMore) {
                    item {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            TextButton(onClick = onLoadMore) {
                                if (uiState.loadingMore) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else {
                                    Text("加载更早消息", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
            }

            // 轮次/上下文 + 输入栏
            TurnContextBar(uiState)
            InputBar(
                input = uiState.input,
                sending = uiState.sending,
                running = uiState.running,
                onInputChange = onInputChange,
                onSend = onSend,
                onCancel = onCancel,
            )
        }
    }

    // 审批弹窗
    uiState.pendingApprovals.firstOrNull()?.let { approval ->
        ApprovalDialog(approval, onAnswer = { outcome -> onAnswerApproval(approval, outcome) })
    }

    // 问题弹窗
    uiState.pendingQuestions.firstOrNull()?.let { question ->
        QuestionDialog(question, onAnswer = { answers -> onAnswerQuestion(question, answers) })
    }

    // 模型选择弹窗
    if (uiState.showModelPicker) {
        ModelPickerDialog(
            current = uiState.currentModel,
            groups = uiState.modelGroups,
            failures = uiState.modelFailures,
            onDismiss = { onShowModelPicker(false) },
            onSelect = { provider, model, effort -> onSelectModel(provider, model, effort) },
        )
    }

    // 权限选择弹窗
    if (uiState.showPermissionPicker) {
        PermissionDialog(
            current = uiState.permissionCurrent,
            options = uiState.permissionOptions,
            onDismiss = { onShowPermissionPicker(false) },
            onSelect = onSetPermission,
        )
    }

    // 图片查看
    uiState.attachmentDialog?.let { dialog ->
        AttachmentDialog(
            state = dialog,
            onDismiss = onCloseAttachment,
        )
    }
}

/** 触发器标签：模型名 + 思考模式（与桌面版一致） */
fun modelTriggerLabel(uiState: ChatUiState): String {
    val model = uiState.currentModel?.model ?: return "未选模型"
    val effortId = uiState.currentModel.reasoningEffort
    if (effortId == null) return model
    val name = uiState.modelGroups
        .flatMap { it.models }
        .firstOrNull { it.id == model }
        ?.reasoning
        ?.efforts
        ?.firstOrNull { it.id == effortId }
        ?.name
    return model + " · " + (name ?: effortId)
}

/** 对话/轨迹切换 chip */
@Composable
fun ViewSwitchChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else Color.Transparent,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 5.dp),
    )
}

// ─────────────────────── 消息渲染（对话视图） ───────────────────────

@Composable
fun MessageRow(
    message: RenderedMessage,
    onOpenAttachment: (String, String?) -> Unit,
) {
    if (message.isToolResult) {
        CollapsibleToolResult(message)
        return
    }
    val isUser = message.role == "user"
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(if (isUser) 0.85f else 1f)
                .clip(
                    RoundedCornerShape(
                        topStart = 18.dp,
                        topEnd = 18.dp,
                        bottomStart = if (isUser) 18.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 18.dp,
                    ),
                )
                .background(
                    if (isUser) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerLow,
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            message.blocks.forEachIndexed { index, block ->
                BlockView(block, message, onOpenAttachment)
                if (index < message.blocks.size - 1) Spacer(Modifier.height(6.dp))
            }
        }
    }
}

@Composable
fun BlockView(
    block: RenderedBlock,
    message: RenderedMessage,
    onOpenAttachment: (String, String?) -> Unit,
) {
    when (block.type) {
        "text" -> {
            if (!block.text.isNullOrBlank()) {
                Text(
                    block.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (message.role == "user") MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        "reasoning" -> CollapsibleReasoning(block)
        "tool-call" -> CollapsibleToolCall(block)
        "tool-result" -> CollapsibleInlineResult(block)
        "image" -> {
            block.image?.let { img ->
                Surface(
                    onClick = { onOpenAttachment(img.attachmentId, img.name) },
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    Row(
                        Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            img.name ?: "图片 " + img.width + "x" + img.height,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
        else -> {
            block.text?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** 思考过程：默认折叠 + 平滑展开动效 */
@Composable
fun CollapsibleReasoning(block: RenderedBlock) {
    val text = block.text ?: return
    if (text.isBlank()) return
    var expanded by remember { mutableStateOf(false) }
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "reasoning_arrow",
    )

    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.6f))
            .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
            .clickable { expanded = !expanded }
            .padding(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier
                    .size(16.dp)
                    .graphicsLayer { rotationZ = arrowRotation },
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                if (expanded) "思考过程" else "思考过程（点击展开）",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (expanded) {
            Spacer(Modifier.height(6.dp))
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                        MaterialTheme.shapes.extraSmall,
                    )
                    .padding(8.dp),
            )
        }
    }
}

/** 工具调用：折叠成一行 + 平滑展开 */
@Composable
fun CollapsibleToolCall(block: RenderedBlock) {
    var expanded by remember { mutableStateOf(false) }
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "tool_call_arrow",
    )

    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.6f))
            .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
            .clickable { expanded = !expanded }
            .padding(8.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("🛠", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.width(6.dp))
            Text(
                block.toolName ?: "工具",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.weight(1f),
            )
            Icon(
                Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier
                    .size(16.dp)
                    .graphicsLayer { rotationZ = arrowRotation },
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (expanded) {
            val args = block.arguments ?: ""
            if (args.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    args,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceContainerHighest,
                            MaterialTheme.shapes.extraSmall,
                        )
                        .padding(8.dp),
                )
            }
        }
    }
}

/** 内嵌工具结果：折叠成一行 + 平滑展开 */
@Composable
fun CollapsibleInlineResult(block: RenderedBlock) {
    var expanded by remember { mutableStateOf(false) }
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "tool_inline_result_arrow",
    )
    val statusColor = if (block.isError) MaterialTheme.colorScheme.error else MaterialTheme.extendedColors.success

    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.6f))
            .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
            .clickable { expanded = !expanded }
            .padding(8.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(if (block.isError) "❌" else "✅", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.width(6.dp))
            Text(
                if (block.isError) "工具出错" else "工具结果",
                style = MaterialTheme.typography.labelSmall,
                color = statusColor,
                modifier = Modifier.weight(1f),
            )
            Icon(
                Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier
                    .size(16.dp)
                    .graphicsLayer { rotationZ = arrowRotation },
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (expanded) {
            val text = block.toolResultText ?: ""
            if (text.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 14,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceContainerHighest,
                            MaterialTheme.shapes.extraSmall,
                        )
                        .padding(8.dp),
                )
            }
        }
    }
}

/** 独立 tool/result 消息：折叠成一行 + 平滑展开 */
@Composable
fun CollapsibleToolResult(message: RenderedMessage) {
    val first = message.blocks.firstOrNull()
    val isError = first?.isError == true
    val text = first?.toolResultText ?: ""
    var expanded by remember { mutableStateOf(false) }
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "tool_result_arrow",
    )
    val statusColor = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.extendedColors.success

    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.6f))
            .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
            .clickable { expanded = !expanded }
            .padding(8.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(if (isError) "❌" else "✅", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.width(6.dp))
            Text(
                if (isError) "工具执行出错" else "工具结果",
                style = MaterialTheme.typography.labelSmall,
                color = statusColor,
                modifier = Modifier.weight(1f),
            )
            Icon(
                Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier
                    .size(16.dp)
                    .graphicsLayer { rotationZ = arrowRotation },
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (expanded && text.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 14,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerHighest,
                        MaterialTheme.shapes.extraSmall,
                    )
                    .padding(8.dp),
            )
        }
    }
}

/** 轨迹视图单行 */
@Composable
fun TrajectoryRow(event: com.dshclient.app.core.model.SessionEvent) {
    val (label, detail) = trajectoryLabel(event)
    val dotColor = trajectoryColor(event.type)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(7.dp)
                .clip(MaterialTheme.shapes.extraSmall)
                .background(dotColor),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            event.seq.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(44.dp),
        )
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(110.dp),
        )
        Text(
            detail,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

fun trajectoryLabel(event: com.dshclient.app.core.model.SessionEvent): Pair<String, String> {
    val d = event.data
    return when (event.type) {
        "turn/start" -> "turn/start" to ("第 " + (d.long("turn") ?: 0) + " 轮")
        "turn/end" -> "turn/end" to ""
        "step/start" -> "step/start" to ("第 " + (d.long("step") ?: 0) + " 步")
        "step/end" -> "step/end" to ""
        "user/message" -> "user/message" to ""
        "assistant/message" -> "assistant/message" to ""
        "assistant/chunk" -> "assistant/chunk" to (d.obj("chunk")?.str("type") ?: "")
        "tool/call" -> "tool/call" to (d.str("name") ?: "")
        "tool/result" -> "tool/result" to (if (d.obj("error") != null) "error" else "ok")
        "session/title" -> "session/title" to (d.str("title") ?: "")
        else -> event.type to ""
    }
}

/** 轨迹行类型色（使用语义色彩 Token） */
@Composable
fun trajectoryColor(type: String): Color {
    val scheme = MaterialTheme.colorScheme
    val extended = MaterialTheme.extendedColors
    return when (type) {
        "user/message" -> scheme.primary
        "assistant/message" -> extended.success
        "tool/call", "tool/result" -> extended.warning
        "turn/start", "turn/end", "step/start", "step/end" -> scheme.outline
        else -> scheme.outlineVariant
    }
}