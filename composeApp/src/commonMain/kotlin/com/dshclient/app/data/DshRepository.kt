package com.dshclient.app.data

import com.dshclient.app.core.model.AskUserQuestionItem
import com.dshclient.app.core.model.QuestionAnswer
import com.dshclient.app.core.model.SessionHistoryValue
import com.dshclient.app.core.network.DshConnection
import kotlinx.coroutines.flow.StateFlow

/** 会话列表项（含派生标题） */
data class SessionListItem(
    val summary: com.dshclient.app.core.model.SessionSummary,
    val title: String? = null,
)

/** 挂起的用户问题（ask_user_question 工具） */
data class PendingQuestion(
    val rpcId: String,
    val sessionId: String,
    val questions: List<AskUserQuestionItem>,
)

/** 挂起的审批 */
data class PendingApproval(
    val rpcId: String,
    val sessionId: String,
    val approvalId: String,
    val toolName: String,
    val callId: String?,
    val reason: String?,
)

/**
 * DSH 数据仓库契约（Now in Android 风格 repository 接口）。
 * UI 只依赖本接口，不直接接触传输层。
 */
interface DshRepository {
    val connection: DshConnection

    val sessions: StateFlow<List<SessionListItem>>
    val workspaces: StateFlow<List<com.dshclient.app.core.model.WorkspaceView>>
    val archivedSessionIds: StateFlow<List<String>>
    val hostInfo: StateFlow<com.dshclient.app.core.model.HostDescribeValue?>
    val pendingQuestions: StateFlow<List<PendingQuestion>>
    val pendingApprovals: StateFlow<List<PendingApproval>>

    fun start()
    fun chatState(sessionId: String): ChatState
    fun chatStateOrNull(sessionId: String): ChatState?

    suspend fun refreshSessions()
    suspend fun refreshWorkspaces()
    suspend fun refreshHostInfo()
    suspend fun loadHistory(sessionId: String, beforeSeq: Long? = null, maxMessages: Int = 50)
    suspend fun loadMore(sessionId: String)

    /** 重命名工作区 */
    suspend fun renameWorkspace(workspaceId: String, title: String): Boolean

    /** 删除工作区 */
    suspend fun deleteWorkspace(workspaceId: String): Boolean

    /** 在工作区创建新会话，返回会话 id 或 null */
    suspend fun createSessionInWorkspace(workspaceId: String): String?

    /** 发送提示词（queue 模式），返回是否接受 */
    suspend fun prompt(sessionId: String, text: String): Boolean

    /** 取消当前回合 */
    suspend fun cancelTurn(sessionId: String): Boolean

    /** 应答用户问题 */
    suspend fun answerQuestion(question: PendingQuestion, answer: QuestionAnswer): Boolean

    /** 应答工具审批 */
    suspend fun answerApproval(approval: PendingApproval, outcome: String): Boolean
}