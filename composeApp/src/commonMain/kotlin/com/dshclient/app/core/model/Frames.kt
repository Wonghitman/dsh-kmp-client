package com.dshclient.app.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

// ─────────────────────────── Mux 帧 ───────────────────────────
// /api/events.mux WebSocket 下行帧（payload 槽位；判别字段 type）

@Serializable
data class SessionSubscribedFrame(
    val sessionId: String,
    val lastSeq: Long,
)

@Serializable
data class SessionEventFrame(
    val sessionId: String,
    val event: SessionEvent,
    val view: JsonElement? = null,
)

@Serializable
data class ApprovalRequestedFrame(
    val sessionId: String,
    val approvalId: String,
    val toolName: String,
    val callId: String? = null,
    val reason: String? = null,
)

@Serializable
data class ApprovalResolvedFrame(
    val sessionId: String,
    val approvalId: String,
    val outcome: String,   // allowed-once | rejected | cancelled | unavailable
)

@Serializable
data class AskUserQuestionItem(
    val id: String,
    val question: String,
    val header: String? = null,
    val detail: String? = null,
    val options: List<QuestionOption>? = null,
    val multiSelect: Boolean? = null,
    val intent: JsonElement? = null,
)

@Serializable
data class QuestionOption(
    val label: String,
    val description: String? = null,
)

@Serializable
data class QuestionRequestedFrame(
    val sessionId: String,
    val questions: List<AskUserQuestionItem>,
)

@Serializable
data class QuestionResolvedFrame(
    val sessionId: String,
    val questionRpcId: String,
    val outcome: String,   // answered | cancelled
)

@Serializable
data class QueueMessage(
    val id: String,
    val role: String,
    val content: List<JsonElement>,
    val source: JsonObject,
)

@Serializable
data class QueueItem(
    val id: String,
    val placement: String,   // queued | steering | context
    val message: QueueMessage,
)

@Serializable
data class SessionQueueFrame(
    val sessionId: String,
    val items: List<QueueItem>,
)

@Serializable
data class SessionJobsFrame(
    val sessionId: String,
    val jobs: List<JobView>,
)

@Serializable
data class SessionProjectionFrame(
    val sessionId: String,
    val key: String,
    val value: JsonElement,
    val seq: Long,
)

@Serializable
data class StreamErrorFrame(
    val error: RpcError,
)

// ─────────────────────────── Host 帧 ───────────────────────────
// /api/events.host 下行帧

@Serializable
data class HostSessionAddedFrame(
    val sessionId: String,
    val blank: Boolean,
    val parentSessionId: String? = null,
    val origin: String? = null,
    val cwd: String? = null,
    val agentPreset: String? = null,
)

@Serializable
data class HostSessionRemovedFrame(val sessionId: String)

@Serializable
data class HostSessionStatusFrame(
    val sessionId: String,
    val running: Boolean,
)

@Serializable
data class HostAgentErrorFrame(
    val sessionId: String,
    val message: String,
)

@Serializable
data class HostWorkspaceChangedFrame(val workspace: WorkspaceView)

@Serializable
data class HostWorkspaceRemovedFrame(val workspaceId: String)

@Serializable
data class HostWorkspaceOrderChangedFrame(val workspaceIds: List<String>)

@Serializable
data class HostArchivedSessionsChangedFrame(val archivedSessionIds: List<String>)

@Serializable
data class HostRemoteEventFrame(
    val event: String,
    val args: List<JsonElement> = emptyList(),
)

// ─────────────────────────── 应答 payload ───────────────────────────
// POST /api/respond 的 client-response value 槽

@Serializable
data class ApprovalResponsePayload(
    val sessionId: String,
    val approvalId: String,
    val outcome: String,   // allowed-once | rejected
)

@Serializable
data class QuestionAnswer(
    val id: String,
    val selected: List<String>,
    val custom: String? = null,
)

@Serializable
data class AskUserQuestionAnswer(val answers: List<QuestionAnswer>)

@Serializable
data class QuestionResponsePayload(
    val sessionId: String,
    val answer: AskUserQuestionAnswer,
)

// ─────────────────────────── 方法常量 ───────────────────────────

object ApiMethods {
    // sessions
    const val SESSION_LIST = "session.list"
    const val SESSION_SEARCH = "session.search"
    const val SESSION_CREATE = "session.create"
    const val SESSION_HISTORY = "session.history"
    const val SESSION_MODELS = "session.models"
    const val SESSION_SELECT_MODEL = "session.selectModel"
    const val SESSION_RENAME = "session.rename"
    const val SESSION_FORK = "session.fork"
    const val SESSION_PROMPT = "session.prompt"
    const val SESSION_ATTACHMENT = "session.attachment"
    const val SESSION_UPDATE_QUEUE = "session.updateQueue"
    const val SESSION_CANCEL = "session.cancel"

    // subagents
    const val SUBAGENT_LIST = "subagent.list"
    const val SUBAGENT_HISTORY = "subagent.history"
    const val SUBAGENT_PROMPT = "subagent.prompt"
    const val SUBAGENT_INTERRUPT = "subagent.interrupt"

    // host
    const val HOST_DESCRIBE = "host.describe"
    const val HOST_PICK_DIRECTORY = "host.pickDirectory"
    const val HOST_LIST_DIRECTORY = "host.listDirectory"
    const val HOST_CREATE_DIRECTORY = "host.createDirectory"
    const val HOST_OPEN_PATH = "host.openPath"

    // workspace
    const val WORKSPACE_LIST = "workspace.list"
    const val WORKSPACE_CREATE = "workspace.create"
    const val WORKSPACE_RENAME = "workspace.rename"
    const val WORKSPACE_DELETE = "workspace.delete"
    const val WORKSPACE_INSERT_BEFORE = "workspace.insertBefore"
    const val WORKSPACE_INSERT_SESSION_BEFORE = "workspace.insertSessionBefore"
    const val WORKSPACE_ARCHIVE_SESSION = "workspace.archiveSession"

    // skills / agent presets
    const val SKILL_LIST = "skill.list"
    const val AGENT_PRESET_LIST = "agentPreset.list"
    const val AGENT_PRESET_SELECT = "agentPreset.select"
    const val AGENT_PRESET_READ = "agentPreset.read"
    const val AGENT_PRESET_COPY = "agentPreset.copy"
    const val AGENT_PRESET_OPEN_DOCUMENT = "agentPreset.openDocument"
    const val AGENT_PRESET_REMOVE = "agentPreset.remove"

    // goals
    const val GOAL_CREATE = "goal.create"
    const val GOAL_EDIT = "goal.edit"
    const val GOAL_PAUSE = "goal.pause"
    const val GOAL_RESUME = "goal.resume"
    const val GOAL_COMPLETE = "goal.complete"
    const val GOAL_CLEAR = "goal.clear"

    // settings / credentials
    const val SETTINGS_DESCRIBE = "settings.describe"
    const val SETTINGS_OPEN_DOCUMENT = "settings.openDocument"
    const val SETTINGS_UPDATE = "settings.update"
    const val SETTINGS_REPLACE = "settings.replace"
    const val SETTINGS_MUTATE = "settings.mutate"
    const val CREDENTIALS_DESCRIBE = "credentials.describe"
    const val CREDENTIALS_SET = "credentials.set"
    const val CREDENTIALS_UNSET = "credentials.unset"

    // llm
    const val LLM_PROVIDERS = "llm.providers"
    const val LLM_MODELS = "llm.models"
    const val LLM_DISCOVER_MODELS = "llm.discoverModels"
}

/** 特权方法：即使配置了 trustedHosts 也强制 loopback（settings/credentials/目录/路径/模型发现） */
val PRIVILEGED_METHODS = setOf(
    ApiMethods.AGENT_PRESET_READ,
    ApiMethods.AGENT_PRESET_COPY,
    ApiMethods.AGENT_PRESET_OPEN_DOCUMENT,
    ApiMethods.AGENT_PRESET_REMOVE,
    ApiMethods.HOST_PICK_DIRECTORY,
    ApiMethods.HOST_OPEN_PATH,
    ApiMethods.SETTINGS_DESCRIBE,
    ApiMethods.SETTINGS_OPEN_DOCUMENT,
    ApiMethods.SETTINGS_UPDATE,
    ApiMethods.SETTINGS_REPLACE,
    ApiMethods.SETTINGS_MUTATE,
    ApiMethods.CREDENTIALS_DESCRIBE,
    ApiMethods.CREDENTIALS_SET,
    ApiMethods.CREDENTIALS_UNSET,
    ApiMethods.LLM_DISCOVER_MODELS,
)
