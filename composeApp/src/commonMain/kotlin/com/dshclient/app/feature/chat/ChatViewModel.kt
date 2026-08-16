package com.dshclient.app.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dshclient.app.core.model.JobView
import com.dshclient.app.core.model.QueueItem
import com.dshclient.app.data.DshRepository
import com.dshclient.app.data.PendingApproval
import com.dshclient.app.data.PendingQuestion
import com.dshclient.app.core.model.int
import com.dshclient.app.core.model.long
import com.dshclient.app.core.model.obj
import com.dshclient.app.core.model.arr
import com.dshclient.app.core.model.str
import com.dshclient.app.data.RenderedMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.put
import kotlinx.coroutines.launch

/** 聊天屏 UI 状态（Now in Android：不可变 UiState + StateFlow） */
data class ChatUiState(
    val sessionId: String,
    val title: String? = null,
    val messages: List<RenderedMessage> = emptyList(),
    val running: Boolean = false,
    val hasMore: Boolean = false,
    val queue: List<QueueItem> = emptyList(),
    val jobs: List<JobView> = emptyList(),
    val pendingQuestions: List<PendingQuestion> = emptyList(),
    val pendingApprovals: List<PendingApproval> = emptyList(),
    val input: String = "",
    val viewMode: ChatViewMode = ChatViewMode.CONVERSATION,
    val sending: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val attachmentDialog: AttachmentDialogState? = null,
    val currentModel: com.dshclient.app.core.model.ModelSelection? = null,
    val modelGroups: List<com.dshclient.app.core.model.ModelProviderGroup> = emptyList(),
    val modelFailures: List<com.dshclient.app.core.model.ModelCatalogFailure> = emptyList(),
    val showModelPicker: Boolean = false,
    val turn: Long? = null,
    val step: Long? = null,
    /** 上下文占用比例 0..1 */
    val contextPercent: Float? = null,
    val contextWindow: Long? = null,
    val contextSystemTokens: Long? = null,
    val contextToolsTokens: Long? = null,
    val contextMessageTokens: Long? = null,
    val goalObjective: String? = null,
    val goalPhase: String? = null,
    val goalRounds: Int? = null,
    val goalMaxRounds: Int? = null,
    val goalId: String? = null,
    val trajectoryEvents: List<com.dshclient.app.core.model.SessionEvent> = emptyList(),
    val deepDiving: Boolean = false,
    val deepDivingSeconds: Long = 0,
    val permissionCurrent: String? = null,
    val permissionOptions: List<PermissionOption> = emptyList(),
    val showPermissionPicker: Boolean = false,
)

/** 图片查看对话框状态 */
data class AttachmentDialogState(
    val sessionId: String,
    val attachmentId: String,
    val name: String? = null,
    val loading: Boolean = false,
    val imageBase64: String? = null,
    val error: String? = null,
)

/** 聊天视图模式：对话 / 轨迹 */
enum class ChatViewMode { CONVERSATION, TRAJECTORY }

/** 权限预设选项（来自 permissions projection） */
data class PermissionOption(
    val value: String,
    val name: String,
    val description: String? = null,
)

@OptIn(kotlin.time.ExperimentalTime::class, kotlinx.coroutines.FlowPreview::class)
class ChatViewModel(
    private val repository: DshRepository,
    val sessionId: String,
) : ViewModel() {

    private val chat = repository.chatState(sessionId)

    private val _uiState = MutableStateFlow(ChatUiState(sessionId))
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var trajectoryEvents: List<com.dshclient.app.core.model.SessionEvent> = emptyList()

    init {
        loadModels()
        viewModelScope.launch {
            // 轨迹事件限流：流式 chunk 高频到达时合并更新，避免全量列表反复重建
            chat.eventsVersion
                .debounce(300)
                .collect {
                    trajectoryEvents = chat.allEvents()
                    _uiState.update { it.copy(trajectoryEvents = trajectoryEvents) }
                }
        }
        viewModelScope.launch {
            combine(
                combine(chat.messages, chat.running, chat.hasMore) { a, b, c -> Triple(a, b, c) },
                combine(chat.queue, chat.jobs, chat.title) { a, b, c -> Triple(a, b, c) },
                combine(repository.pendingQuestions, repository.pendingApprovals) { a, b -> a to b },
                combine(chat.currentTurn, chat.currentStep, chat.projections) { a, b, c -> Triple(a, b, c) },
                chat.turnStartTime,
            ) { t1, t2, t3, t4, t5 ->
                // 解析 goal projection
                val goal = t4.third["goal"]
                val goalObj = (goal as? kotlinx.serialization.json.JsonObject)?.obj("goal")
                val goalPhase = goalObj?.str("phase")
                val goalObjective = goalObj?.str("objective")
                ChatUiState(
                    sessionId = sessionId,
                    title = t2.third,
                    messages = t1.first,
                    running = t1.second,
                    hasMore = t1.third,
                    queue = t2.first,
                    jobs = t2.second,
                    pendingQuestions = t3.first.filter { it.sessionId == sessionId },
                    pendingApprovals = t3.second.filter { it.sessionId == sessionId },
                    input = _uiState.value.input,
                    sending = _uiState.value.sending,
                    loadingMore = _uiState.value.loadingMore,
                    error = _uiState.value.error,
                    attachmentDialog = _uiState.value.attachmentDialog,
                    currentModel = _uiState.value.currentModel,
                    modelGroups = _uiState.value.modelGroups,
                    modelFailures = _uiState.value.modelFailures,
                    showModelPicker = _uiState.value.showModelPicker,
                    turn = t4.first ?: _uiState.value.turn,
                    step = t4.second ?: _uiState.value.step,
                    goalId = goalObj?.str("id") ?: _uiState.value.goalId,
                    goalObjective = goalObjective ?: _uiState.value.goalObjective,
                    goalPhase = goalPhase ?: _uiState.value.goalPhase,
                    goalRounds = goalObj?.int("roundsStarted") ?: _uiState.value.goalRounds,
                    deepDiving = t5 != null,
                    deepDivingSeconds = _uiState.value.deepDivingSeconds,
                    permissionCurrent = _uiState.value.permissionCurrent,
                    permissionOptions = _uiState.value.permissionOptions,
                    showPermissionPicker = _uiState.value.showPermissionPicker,
                    contextPercent = _uiState.value.contextPercent,
                    contextWindow = _uiState.value.contextWindow,
                    contextSystemTokens = _uiState.value.contextSystemTokens,
                    contextToolsTokens = _uiState.value.contextToolsTokens,
                    contextMessageTokens = _uiState.value.contextMessageTokens,
                )
            }.collect { _uiState.value = it }
        }
        // Deep diving 计时器（每秒刷新）
        viewModelScope.launch {
            while (true) {
                val start = chat.turnStartTime.value
                _uiState.update { it.copy(deepDivingSeconds = if (start != null) (kotlin.time.Clock.System.now().toEpochMilliseconds() - start) / 1000 else 0) }
                kotlinx.coroutines.delay(1000)
            }
        }
        // 解析 projections → 上下文占用
        viewModelScope.launch {
            chat.projections.collect { proj ->
                val pressure = proj["contextPressure"]
                val breakdown = proj["contextBreakdown"]
                _uiState.update { state ->
                    var pct = state.contextPercent
                    var window = state.contextWindow
                    var sys = state.contextSystemTokens
                    var tools = state.contextToolsTokens
                    var msgs = state.contextMessageTokens
                    if (pressure is kotlinx.serialization.json.JsonObject) {
                        window = pressure.long("contextWindow") ?: window
                        val projected = pressure.long("projectedTokens")
                        if (projected != null && window != null && window > 0) {
                            pct = (projected.toFloat() / window.toFloat()).coerceIn(0f, 1f)
                        }
                    }
                    if (breakdown is kotlinx.serialization.json.JsonObject) {
                        sys = breakdown.long("systemTokens") ?: sys
                        tools = breakdown.long("toolsTokens") ?: tools
                        msgs = breakdown.long("messageTokens") ?: msgs
                    }
                    // 权限预设（permissions projection：选项列表 + 当前值）
                    var permsOptions: List<PermissionOption>? = null
                    var permsCurrent: String? = null
                    val perms = proj["permissions"]
                    if (perms is kotlinx.serialization.json.JsonObject) {
                        permsOptions = perms.arr("options")
                            ?.mapNotNull { it as? kotlinx.serialization.json.JsonObject }
                            ?.map {
                                PermissionOption(
                                    value = it.str("value") ?: "",
                                    name = it.str("name") ?: "",
                                    description = it.str("description"),
                                )
                            }
                        permsCurrent = perms.str("currentValue")
                    }
                    state.copy(
                        contextPercent = pct,
                        contextWindow = window,
                        contextSystemTokens = sys,
                        contextToolsTokens = tools,
                        contextMessageTokens = msgs,
                        permissionOptions = permsOptions ?: state.permissionOptions,
                        permissionCurrent = permsCurrent ?: state.permissionCurrent,
                    )
                }
            }
        }
    }

    fun setViewMode(mode: ChatViewMode) {
        _uiState.update { it.copy(viewMode = mode) }
    }

    fun onInputChange(text: String) {
        _uiState.update { it.copy(input = text) }
    }

    /** 发送消息（队列模式） */
    fun send() {
        val text = _uiState.value.input.trim()
        if (text.isEmpty() || _uiState.value.sending) return
        _uiState.update { it.copy(sending = true, input = "", error = null) }
        viewModelScope.launch {
            val ok = repository.prompt(sessionId, text)
            _uiState.update {
                it.copy(
                    sending = false,
                    error = if (ok) null else "发送失败，请检查连接",
                )
            }
        }
    }

    /** 取消当前回合 */
    fun cancelTurn() {
        viewModelScope.launch {
            repository.cancelTurn(sessionId)
        }
    }

    /** 加载更早消息 */
    fun loadMore() {
        val state = _uiState.value
        if (!state.hasMore || state.loadingMore) return
        _uiState.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            repository.loadMore(sessionId)
            _uiState.update { it.copy(loadingMore = false) }
        }
    }

    /** 应答用户问题 */
    fun answerQuestion(question: PendingQuestion, answers: List<com.dshclient.app.core.model.QuestionAnswer>) {
        viewModelScope.launch {
            answers.forEach { answer ->
                repository.answerQuestion(question, answer)
            }
        }
    }

    /** 应答工具审批 */
    fun answerApproval(approval: PendingApproval, outcome: String) {
        viewModelScope.launch {
            repository.answerApproval(approval, outcome)
        }
    }

    /** 编辑队列项 */
    fun editQueueItem(itemId: String, newText: String) {
        viewModelScope.launch {
            try {
                repository.connection.callVoid(
                    com.dshclient.app.core.model.ApiMethods.SESSION_UPDATE_QUEUE,
                    kotlinx.serialization.json.buildJsonObject {
                        put("sessionId", sessionId)
                        put("itemId", kotlinx.serialization.json.JsonPrimitive(itemId))
                        put("action", kotlinx.serialization.json.buildJsonObject {
                            put("kind", kotlinx.serialization.json.JsonPrimitive("edit"))
                            put("content", kotlinx.serialization.json.buildJsonArray {
                                add(kotlinx.serialization.json.buildJsonObject {
                                    put("type", kotlinx.serialization.json.JsonPrimitive("text"))
                                    put("text", kotlinx.serialization.json.JsonPrimitive(newText))
                                })
                            })
                        })
                    },
                )
            } catch (e: Exception) {
            }
        }
    }

    /** 删除队列项 */
    fun removeQueueItem(itemId: String) {
        viewModelScope.launch {
            try {
                repository.connection.callVoid(
                    com.dshclient.app.core.model.ApiMethods.SESSION_UPDATE_QUEUE,
                    kotlinx.serialization.json.buildJsonObject {
                        put("sessionId", sessionId)
                        put("itemId", kotlinx.serialization.json.JsonPrimitive(itemId))
                        put("action", kotlinx.serialization.json.buildJsonObject {
                            put("kind", kotlinx.serialization.json.JsonPrimitive("remove"))
                        })
                    },
                )
            } catch (e: Exception) {
            }
        }
    }

    /** 置顶并立即执行队列项 */
    fun steerQueueItem(itemId: String) {
        viewModelScope.launch {
            try {
                repository.connection.callVoid(
                    com.dshclient.app.core.model.ApiMethods.SESSION_UPDATE_QUEUE,
                    kotlinx.serialization.json.buildJsonObject {
                        put("sessionId", sessionId)
                        put("itemId", kotlinx.serialization.json.JsonPrimitive(itemId))
                        put("action", kotlinx.serialization.json.buildJsonObject {
                            put("kind", kotlinx.serialization.json.JsonPrimitive("steer"))
                        })
                    },
                )
            } catch (e: Exception) {
            }
        }
    }

    /** 打开图片 */
    fun openAttachment(attachmentId: String, name: String?) {
        _uiState.update {
            it.copy(attachmentDialog = AttachmentDialogState(sessionId, attachmentId, name, loading = true))
        }
        viewModelScope.launch {
            try {
                val value = repository.connection.call(
                    com.dshclient.app.core.model.ApiMethods.SESSION_ATTACHMENT,
                    kotlinx.serialization.json.buildJsonObject {
                        put("sessionId", sessionId)
                        put("attachmentId", kotlinx.serialization.json.JsonPrimitive(attachmentId))
                    },
                    com.dshclient.app.core.model.SessionAttachmentValue.serializer(),
                )
                _uiState.update { state ->
                    state.copy(
                        attachmentDialog = state.attachmentDialog?.copy(
                            loading = false,
                            imageBase64 = value.data,
                        ),
                    )
                }
            } catch (e: Exception) {
                _uiState.update { state ->
                    state.copy(
                        attachmentDialog = state.attachmentDialog?.copy(
                            loading = false,
                            error = e.message,
                        ),
                    )
                }
            }
        }
    }

    fun closeAttachment() {
        _uiState.update { it.copy(attachmentDialog = null) }
    }

    /** 加载当前模型与模型目录（会话级 session.models） */
    fun loadModels() {
        viewModelScope.launch {
            try {
                val value = repository.connection.call(
                    com.dshclient.app.core.model.ApiMethods.SESSION_MODELS,
                    kotlinx.serialization.json.buildJsonObject {
                        put("sessionId", sessionId)
                    },
                    com.dshclient.app.core.model.SessionModelsValue.serializer(),
                )
                _uiState.update {
                    it.copy(
                        currentModel = value.current,
                        modelGroups = value.groups,
                        modelFailures = value.failures,
                    )
                }
            } catch (e: Exception) {
            }
        }
    }

    fun showPermissionPicker(show: Boolean) {
        _uiState.update { it.copy(showPermissionPicker = show) }
    }

    /** 切换权限预设（发送 /permission 命令） */
    fun setPermission(name: String) {
        _uiState.update { it.copy(showPermissionPicker = false) }
        viewModelScope.launch {
            repository.prompt(sessionId, "/permission " + name)
        }
    }

    fun showModelPicker(show: Boolean) {
        _uiState.update { it.copy(showModelPicker = show) }
        if (show && _uiState.value.modelGroups.isEmpty()) loadModels()
    }

    /** 选择模型（含思考模式 reasoningEffort） */
    fun selectModel(provider: String, model: String, reasoningEffort: String?) {
        viewModelScope.launch {
            try {
                val payload = kotlinx.serialization.json.buildJsonObject {
                    put("sessionId", sessionId)
                    put("provider", provider)
                    put("model", model)
                    if (reasoningEffort != null) put("reasoningEffort", reasoningEffort)
                }
                val value = repository.connection.call(
                    com.dshclient.app.core.model.ApiMethods.SESSION_SELECT_MODEL,
                    payload,
                    com.dshclient.app.core.model.SessionSelectModelValue.serializer(),
                )
                _uiState.update {
                    it.copy(currentModel = value.selected, showModelPicker = false)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(showModelPicker = false) }
            }
        }
    }
}