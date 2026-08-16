package com.dshclient.app.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

// ─────────────────────────── sessions 域 ───────────────────────────

@Serializable
data class SessionSummary(
    val sessionId: String,
    val updatedAt: Long,
    val running: Boolean,
    val blank: Boolean,
    val parentSessionId: String? = null,
    val origin: String? = null,
    val cwd: String? = null,
    val agentPreset: String? = null,
    val projections: JsonObject? = null,
)

@Serializable
data class SessionListValue(val items: List<SessionSummary>)

@Serializable
data class SessionCreateRequest(
    val workspaceId: String? = null,
    val cwd: String? = null,
    val sessionId: String? = null,
    val agentPreset: String? = null,
)

@Serializable
data class SessionCreateValue(
    val sessionId: String,
    val agentPreset: String? = null,
)

@Serializable
data class SessionHistoryRequest(
    val sessionId: String,
    val beforeSeq: Long? = null,
    val maxMessages: Int? = null,
)

@Serializable
data class HistoryEntry(
    val event: SessionEvent,
    val view: JsonElement? = null,
)

@Serializable
data class SessionHistoryValue(
    val events: List<HistoryEntry>,
    val hasMore: Boolean,
    val projections: JsonObject? = null,
)

@Serializable
data class SessionSearchRequest(val query: String)

@Serializable
data class SessionSearchItem(
    val sessionId: String,
    val snippet: String,
)

@Serializable
data class SessionSearchValue(
    val items: List<SessionSearchItem>,
    val hasMore: Boolean,
)

@Serializable
data class SessionRenameRequest(val sessionId: String, val title: String)

@Serializable
data class SessionRenameValue(val title: String, val seq: Long)

@Serializable
data class SessionForkRequest(val sessionId: String, val atSeq: Long? = null)

@Serializable
data class SessionForkValue(val sessionId: String)

/** prompt 内容：text 或 image（base64 data） */
@Serializable
data class PromptTextPart(
    val type: String = "text",
    val text: String,
)

@Serializable
data class PromptImagePart(
    val type: String = "image",
    val mediaType: String,
    val data: String,
    val name: String? = null,
)

@Serializable
data class SessionPromptRequest(
    val sessionId: String,
    val mode: String,   // "queue" | "steer"
    val content: List<JsonElement>,
    val clientTimeZone: String? = null,
)

@Serializable
data class SessionPromptValue(
    val accepted: Boolean,
    val command: JsonObject? = null,
)

@Serializable
data class SessionCancelValue(val accepted: Boolean)

@Serializable
data class SessionAttachmentRequest(
    val sessionId: String,
    val attachmentId: String,
)

@Serializable
data class SessionAttachmentValue(
    val attachment: ImageAttachmentRef,
    val data: String,   // base64
)

@Serializable
data class SessionUpdateQueueRequest(
    val sessionId: String,
    val itemId: String,
    val action: JsonObject,
)

@Serializable
data class SessionUpdateQueueValue(val accepted: Boolean)

// 模型选择

@Serializable
data class ModelSelection(
    val provider: String,
    val model: String,
    val reasoningEffort: String? = null,
)

@Serializable
data class ModelReasoningEffort(
    val id: String,
    val name: String,
    val description: String? = null,
)

@Serializable
data class ModelReasoning(
    val efforts: List<ModelReasoningEffort> = emptyList(),
    val defaultEffort: String? = null,
)

@Serializable
data class ModelCatalogModel(
    val id: String,
    val name: String,
    val description: String? = null,
    val reasoning: ModelReasoning? = null,
)

@Serializable
data class ModelProviderGroup(
    val id: String,
    val name: String,
    val models: List<ModelCatalogModel> = emptyList(),
)

@Serializable
data class ModelCatalogFailure(
    val id: String,
    val name: String,
    val message: String,
)

@Serializable
data class SessionModelsValue(
    val current: ModelSelection,
    val routable: Boolean,
    val groups: List<ModelProviderGroup> = emptyList(),
    val failures: List<ModelCatalogFailure> = emptyList(),
)

@Serializable
data class SessionSelectModelRequest(
    val sessionId: String,
    val provider: String,
    val model: String,
    val reasoningEffort: String? = null,
)

@Serializable
data class SessionSelectModelValue(val selected: ModelSelection)

// ─────────────────────────── host 域 ───────────────────────────

@Serializable
data class HostDescribeValue(
    val version: String,
    val cwd: String,
    val provider: String? = null,
    val model: String? = null,
    val attachedSessions: Int,
    val canOpenPath: Boolean,
)

@Serializable
data class DirectoryEntry(
    val name: String,
    val path: String,
    val hidden: Boolean,
)

@Serializable
data class HostListDirectoryValue(
    val path: String,
    val home: String,
    val crumbs: List<DirectoryEntry>,
    val entries: List<DirectoryEntry>,
    val truncated: Boolean,
)

@Serializable
data class HostCreateDirectoryRequest(val path: String, val name: String)

@Serializable
data class HostCreateDirectoryValue(val path: String)

@Serializable
data class HostPickDirectoryValue(val path: String? = null)

// ─────────────────────────── workspace 域 ───────────────────────────

@Serializable
data class WorkspaceView(
    val workspaceId: String,
    val path: String,
    val title: String,
    val sessionIds: List<String>,
    val createdAt: String,
    val updatedAt: String,
)

@Serializable
data class WorkspaceListValue(
    val items: List<WorkspaceView>,
    val archivedSessionIds: List<String>,
)

@Serializable
data class WorkspaceCreateRequest(val path: String)

@Serializable
data class WorkspaceCreateValue(
    val workspace: WorkspaceView,
    val created: Boolean,
)

@Serializable
data class WorkspaceRenameRequest(val workspaceId: String, val title: String)

@Serializable
data class WorkspaceRenameValue(val workspace: WorkspaceView)

@Serializable
data class WorkspaceDeleteValue(val deleted: Boolean)

@Serializable
data class WorkspaceInsertBeforeRequest(
    val workspaceId: String,
    val beforeWorkspaceId: String? = null,
)

@Serializable
data class WorkspaceInsertBeforeValue(val workspaceIds: List<String>)

@Serializable
data class WorkspaceInsertSessionBeforeRequest(
    val workspaceId: String,
    val sessionId: String,
    val beforeSessionId: String? = null,
)

@Serializable
data class WorkspaceInsertSessionBeforeValue(val workspace: WorkspaceView)

@Serializable
data class WorkspaceArchiveSessionRequest(val sessionId: String)

@Serializable
data class WorkspaceArchiveSessionValue(val archivedSessionIds: List<String>)

// ─────────────────────────── jobs / subagents ───────────────────────────

@Serializable
data class JobView(
    val id: String,
    val kind: String,
    val label: String,
    val status: String,
    val detail: String? = null,
    val startedAt: Long,
    val finishedAt: Long? = null,
)

@Serializable
data class SubagentListRequest(val parentSessionId: String)

@Serializable
data class SubagentListEntry(
    val kind: String,      // "child" | "diagnostic"
    val id: String,
    val mode: String? = null,     // "one-shot" | "continuable"
    val activity: String? = null, // "running" | "inactive"
    val hasChildren: Boolean? = null,
    val label: String? = null,
    val reason: String? = null,
)

@Serializable
data class SubagentListValue(
    val entries: List<SubagentListEntry>,
    val parentAvailable: Boolean,
)

@Serializable
data class SubagentHistoryRequest(
    val parentSessionId: String,
    val childSessionId: String,
    val mode: String,
    val beforeSeq: Long? = null,
    val maxMessages: Int? = null,
)

@Serializable
data class SubagentPromptRequest(
    val parentSessionId: String,
    val childSessionId: String,
    val mode: String,
    val content: List<JsonElement>,
    val clientTimeZone: String? = null,
)

@Serializable
data class SubagentPromptValue(val messageId: String)

@Serializable
data class SubagentInterruptRequest(
    val parentSessionId: String,
    val childSessionId: String,
    val mode: String,
)

@Serializable
data class SubagentInterruptValue(val accepted: Boolean)

// ─────────────────────────── 其他域 ───────────────────────────

@Serializable
data class SkillEntry(
    val name: String,
    val description: String,
    val whenToUse: String? = null,
    val modelInvocable: Boolean,
)

@Serializable
data class SkillListRequest(val sessionId: String)

@Serializable
data class SkillListValue(val skills: List<SkillEntry>)

@Serializable
data class AgentPresetEntry(
    val id: String,
    val trust: String,
    val isDefault: Boolean,
    val name: String? = null,
    val description: String? = null,
    val broken: String? = null,
)

@Serializable
data class AgentPresetListValue(
    val presets: List<AgentPresetEntry>,
    val authorable: Boolean,
    val hasDocument: Boolean,
)

@Serializable
data class AgentPresetSelectRequest(val sessionId: String, val agentPreset: String)

@Serializable
data class AgentPresetSelectValue(val agentPreset: String)

@Serializable
data class GoalRef(val id: String, val revision: Int)

@Serializable
data class GoalRefValue(val ref: GoalRef)

@Serializable
data class GoalCreateRequest(
    val sessionId: String,
    val objective: String,
    val maxGoalRounds: Int? = null,
)

@Serializable
data class GoalEditRequest(
    val sessionId: String,
    val ref: GoalRef,
    val objective: String? = null,
    val maxGoalRounds: Int? = null,
)

@Serializable
data class GoalRefRequest(val sessionId: String, val ref: GoalRef)

@Serializable
data class GoalClearValue(val cleared: Boolean)

@Serializable
data class SettingsNamespaceView(
    val ns: String,
    val schema: JsonElement? = null,
    val value: JsonElement? = null,
    val base: JsonElement? = null,
    val user: JsonElement? = null,
    val applies: String,
    val secrets: List<SettingsSecretView> = emptyList(),
    val revision: Long,
)

@Serializable
data class SettingsSecretView(
    val path: List<String>,
    val set: Boolean,
)

@Serializable
data class SettingsDescribeValue(
    val writable: Boolean,
    val hasDocument: Boolean,
    val namespaces: List<SettingsNamespaceView>,
)

@Serializable
data class SettingsUpdateRequest(
    val ns: String,
    val patch: JsonObject,
    val expectedRevision: Long? = null,
)

@Serializable
data class SettingsPathOp(
    val op: String,   // "set" | "unset"
    val path: List<String>,
    val value: JsonElement? = null,
)

@Serializable
data class SettingsMutateRequest(
    val ns: String,
    val ops: List<SettingsPathOp>,
    val expectedRevision: Long? = null,
)

@Serializable
data class CredentialView(
    val configured: Boolean,
    val source: String? = null,
    val writable: Boolean,
)

@Serializable
data class CredentialsDescribeRequest(val refs: List<String>)

@Serializable
data class CredentialsDescribeValue(
    val credentials: Map<String, CredentialView>,
)

@Serializable
data class CredentialsSetRequest(val ref: String, val value: String)

@Serializable
data class ConfigurableProviderView(
    val provider: String,
    val displayName: String,
    val settingsNs: String,
    val settingsPath: List<String>,
    val active: Boolean,
    val declared: Boolean? = null,
)

@Serializable
data class LlmProvidersValue(val providers: List<ConfigurableProviderView>)

@Serializable
data class LlmModelsValue(
    val groups: List<ModelProviderGroup> = emptyList(),
    val failures: List<ModelCatalogFailure> = emptyList(),
)

@Serializable
data class DiscoveredModelView(
    val id: String,
    val name: String? = null,
    val contextWindow: Long? = null,
    val maxTokens: Long? = null,
)

@Serializable
data class LlmDiscoverModelsValue(val models: List<DiscoveredModelView>)