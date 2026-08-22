package com.zeus.code.ui.local

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zeus.code.data.BackgroundAgentApi
import com.zeus.code.data.GitService
import com.zeus.code.data.SecureTokenStore
import com.zeus.code.data.WorkspaceManager
import com.zeus.code.local.LocalLlmClient
import com.zeus.code.local.LocalModelChoice
import com.zeus.code.local.LocalProviderStore
import com.zeus.code.local.LocalSource
import com.zeus.code.local.LocalTask
import com.zeus.code.local.LocalTaskStatus
import com.zeus.code.local.LocalTaskStore
import com.zeus.code.model.AgentLlmCatalog
import com.zeus.code.model.Workspace
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class LocalAgentUiState(
    val booting: Boolean = true,
    val busy: Boolean = false,
    val message: String? = null,
    val tasks: List<LocalTask> = emptyList(),
    val selectedTaskId: String? = null,
    val workspaces: List<Workspace> = emptyList(),
    val workspaceQuery: String = "",
    val selectedWorkspace: Workspace? = null,
    /** True when a paired NEBians token exists on this device. */
    val nebiansConnected: Boolean = false,
    /** Catalog of NEBians models (community/official/custom), loaded lazily. */
    val nebiansCatalog: AgentLlmCatalog? = null,
    val zenKeyMasked: String = "",
    val customProviders: List<com.zeus.code.local.LocalCustomProvider> = emptyList(),
    val customKeyMasks: Map<String, String> = emptyMap(),
    val selection: LocalModelChoice = LocalModelChoice(),
    val maxSteps: Int = 40
) {
    val filteredWorkspaces: List<Workspace>
        get() = workspaces.filter {
            workspaceQuery.isBlank() || it.name.contains(workspaceQuery, ignoreCase = true)
        }

    val hasAnyProvider: Boolean
        get() = nebiansConnected || zenKeyMasked.isNotBlank() || customProviders.isNotEmpty()
}

class LocalAgentViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val PREF_CHOICE = "selected_choice"
        private const val PREF_MAX_STEPS = "max_steps"
    }

    private val prefs = application.getSharedPreferences("zeus_local_agent", android.content.Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val providerStore = LocalProviderStore(application)
    private val nebiansTokenStore = SecureTokenStore(application, "background_agent")
    private val nebiansApi = BackgroundAgentApi(application)
    private val workspaceManager = WorkspaceManager(application, GitService())

    private val llm = LocalLlmClient(
        nebiansApi = nebiansApi,
        nebiansToken = { nebiansTokenStore.read() }
    ).apply {
        zenKeyProvider = { providerStore.zenKey() }
        customConfigProvider = { id -> providerStore.customProvider(id) }
        customKeyProvider = { id -> providerStore.customKey(id) }
    }

    private val _state = MutableStateFlow(LocalAgentUiState())
    val state: StateFlow<LocalAgentUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            LocalTaskStore.init(application)
            LocalTaskStore.recoverOrphans()
            restoreSelection()
            _state.update { it.copy(booting = false) }
            launch {
                LocalTaskStore.tasks.collect { tasks ->
                    _state.update { current ->
                        current.copy(
                            tasks = tasks,
                            selectedTaskId = current.selectedTaskId?.takeIf { id -> tasks.any { it.id == id } }
                        )
                    }
                }
            }
            refreshWorkspaces()
            loadNebiansCatalogQuietly()
        }
    }

    // ------------------------------------------------------------------
    // Data refresh
    // ------------------------------------------------------------------

    fun refresh() = task(null) {
        refreshWorkspaces()
        _state.update { it.copy(customProviders = providerStore.customProviders()) }
        loadNebiansCatalogQuietly()
    }

    private suspend fun refreshWorkspaces() {
        val workspaces = runCatching { workspaceManager.list() }.getOrDefault(emptyList())
        _state.update { current ->
            current.copy(
                workspaces = workspaces,
                selectedWorkspace = current.selectedWorkspace?.let { selected ->
                    workspaces.firstOrNull { it.path == selected.path }
                } ?: current.selectedWorkspace
            )
        }
    }

    fun setWorkspaceQuery(value: String) = _state.update { it.copy(workspaceQuery = value) }

    fun selectWorkspace(workspace: Workspace?) = _state.update { it.copy(selectedWorkspace = workspace) }

    fun dismissMessage() = _state.update { it.copy(message = null) }

    fun openTask(taskId: String) = _state.update { it.copy(selectedTaskId = taskId, message = null) }

    fun closeTask() = _state.update { it.copy(selectedTaskId = null) }

    fun setMaxSteps(value: Int) {
        val coerced = value.coerceIn(5, 120)
        prefs.edit().putInt(PREF_MAX_STEPS, coerced).apply()
        _state.update { it.copy(maxSteps = coerced) }
    }

    // ------------------------------------------------------------------
    // Model selection
    // ------------------------------------------------------------------

    fun select(choice: LocalModelChoice?) {
        val value = choice ?: LocalModelChoice()
        prefs.edit().putString(PREF_CHOICE, json.encodeToString(value)).apply()
        _state.update { it.copy(selection = value, message = null) }
    }

    private fun restoreSelection() {
        val saved = prefs.getString(PREF_CHOICE, null)?.let { raw ->
            runCatching { json.decodeFromString<LocalModelChoice>(raw) }.getOrNull()
        }
        _state.update {
            it.copy(
                selection = saved ?: LocalModelChoice(),
                maxSteps = prefs.getInt(PREF_MAX_STEPS, 40),
                zenKeyMasked = providerStore.zenKeyMasked(),
                customProviders = providerStore.customProviders(),
                customKeyMasks = providerStore.customProviders().associate { p -> p.id to providerStore.customKeyMasked(p.id) },
                nebiansConnected = !nebiansTokenStore.read().isNullOrBlank()
            )
        }
    }

    /**
     * Loads the NEBians model catalog so its providers appear in the picker;
     * silent failure keeps Local Mode fully usable without NEBians.
     */
    private suspend fun loadNebiansCatalogQuietly() {
        val token = nebiansTokenStore.read() ?: run {
            _state.update { it.copy(nebiansConnected = false, nebiansCatalog = null) }
            return
        }
        _state.update { it.copy(nebiansConnected = true) }
        val catalog = withContext(Dispatchers.IO) {
            runCatching { nebiansApi.state(token, archived = false).llm }.getOrNull()
        }
        _state.update { it.copy(nebiansCatalog = catalog ?: it.nebiansCatalog) }
    }

    // ------------------------------------------------------------------
    // Task lifecycle
    // ------------------------------------------------------------------

    fun startTask(goal: String, onStarted: (() -> Unit)? = null) = task("Queueing local task…") {
        val context = getApplication<Application>()
        val workspace = _state.value.selectedWorkspace
            ?: error("Choose a workspace first.")
        val choice = _state.value.selection
        check(choice.isValid) { "Pick a model before starting." }
        check(goal.trim().length >= 10) { "Describe the change in at least 10 characters." }
        ensureChoiceAvailable(choice)

        val task = LocalTask(
            id = LocalTaskStore.newId(),
            workspaceName = workspace.name,
            workspacePath = workspace.path,
            goal = goal.trim(),
            status = LocalTaskStatus.QUEUED,
            choice = choice,
            maxSteps = _state.value.maxSteps,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            workBranch = suggestedBranch(goal)
        )
        LocalTaskStore.save(task)
        com.zeus.code.local.LocalAgentService.enqueue(context, task.id)
        _state.update { it.copy(message = "Task queued locally on ${workspace.name}.") }
        onStarted?.invoke()
    }

    private fun suggestedBranch(goal: String): String {
        val slug = goal.trim().lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .take(28)
        return if (slug.isBlank()) "zeus/local-agent" else "zeus/$slug"
    }

    /** Guards against starting with a provider that lost its key/config. */
    private fun ensureChoiceAvailable(choice: LocalModelChoice) {
        when (choice.source) {
            LocalSource.NEBIANS -> {
                if (nebiansTokenStore.read().isNullOrBlank()) {
                    error("NEBians is not connected — connect it or switch model.")
                }
                if (choice.nebSlug.isNotBlank() && choice.nebRowId.isBlank()) {
                    val catalog = _state.value.nebiansCatalog
                    val entry = catalog?.selectableEntries()?.firstOrNull { it.slug == choice.nebSlug }
                    if (entry != null && !entry.available && entry.byokProviderId.isBlank()) {
                        error("${entry.label} needs an API key — add it under AI providers.")
                    }
                }
            }
            LocalSource.ZEN -> require(providerStore.zenConfigured()) {
                "Add your OpenCode Zen API key first."
            }
            LocalSource.CUSTOM -> {
                val config = providerStore.customProvider(choice.customId)
                    ?: error("This custom provider was removed.")
                require(config.enabled) { "${config.label} is disabled." }
                require(providerStore.customKey(choice.customId).isNotBlank() || config.baseUrl.contains("127.0.0.1") || config.baseUrl.contains("localhost")) {
                    "Add the ${config.label} API key first."
                }
            }
        }
    }

    fun stopSelectedTask() = task("Stopping…") {
        val id = _state.value.selectedTaskId ?: return@task
        com.zeus.code.local.LocalAgentService.stopCurrent(getApplication())
        LocalTaskStore.get(id)?.let { current ->
            if (current.status == LocalTaskStatus.QUEUED) {
                LocalTaskStore.save(current.copy(status = LocalTaskStatus.STOPPED))
            }
        }
    }

    fun retryTask(taskId: String) = task("Re-queueing task…") {
        val current = LocalTaskStore.get(taskId) ?: error("Task not found.")
        require(!LocalTaskStatus.isActive(current.status)) { "Task is already active." }
        LocalTaskStore.save(
            current.copy(
                status = LocalTaskStatus.QUEUED,
                error = "",
                summary = "",
                steps = 0,
                completedAt = 0L
            )
        )
        com.zeus.code.local.LocalAgentService.enqueue(getApplication(), taskId)
        toast("Task re-queued.")
    }

    fun deleteTask(taskId: String) = task(null) {
        LocalTaskStore.delete(taskId)
        if (_state.value.selectedTaskId == taskId) _state.update { it.copy(selectedTaskId = null) }
    }

    fun deleteFinished() = task(null) {
        LocalTaskStore.deleteAllFinished()
        toast("Cleared finished tasks.")
    }

    // ------------------------------------------------------------------
    // Provider management
    // ------------------------------------------------------------------

    fun saveZenKey(key: String, onSaved: (() -> Unit)? = null) = task("Saving OpenCode Zen key…") {
        providerStore.setZenKey(key)
        _state.update { it.copy(zenKeyMasked = providerStore.zenKeyMasked()) }
        toast("OpenCode Zen key saved on this device.")
        onSaved?.invoke()
    }

    fun removeZenKey(onDone: (() -> Unit)? = null) = task(null) {
        providerStore.setZenKey("")
        clearSelectionIf { it.source == LocalSource.ZEN }
        _state.update { it.copy(zenKeyMasked = "") }
        toast("Zen key removed.")
        onDone?.invoke()
    }

    fun testZenKey(key: String, onResult: (ok: Boolean, detail: String) -> Unit) = task(null) {
        try {
            val models = withContext(Dispatchers.IO) {
                llm.listModels(LocalProviderStore.ZEN_BASE_URL, key.ifBlank { providerStore.zenKey() })
            }
            onResult(models.isNotEmpty(), if (models.isEmpty()) "Connected, but no models returned." else "Connected · ${models.size} models available.")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            onResult(false, error.message ?: "Test failed.")
        }
    }

    fun saveCustomProvider(
        id: String?,
        name: String,
        baseUrl: String,
        apiKey: String,
        modelsCsv: String,
        defaultModel: String,
        onSaved: (() -> Unit)? = null
    ) = task("Saving provider…") {
        val trimmedBase = baseUrl.trim().trimEnd('/')
        require(trimmedBase.startsWith("http://") || trimmedBase.startsWith("https://")) {
            "Base URL must start with http(s)://"
        }
        val models = modelsCsv.split(',').map { it.trim() }.filter { it.isNotBlank() }.distinct().take(40)
        require(models.isNotEmpty()) { "Add at least one model id." }
        val providerId = id ?: "lc_" + java.util.UUID.randomUUID().toString().replace("-", "").take(16)
        val existing = id?.let { providerStore.customProvider(it) }
        providerStore.saveCustomProvider(
            com.zeus.code.local.LocalCustomProvider(
                id = providerId,
                name = name.trim().ifBlank { "Custom provider" },
                baseUrl = trimmedBase,
                defaultModel = defaultModel.trim().ifBlank { models.first() },
                models = models,
                enabled = existing?.enabled ?: true
            )
        )
        if (apiKey.isNotBlank()) providerStore.setCustomKey(providerId, apiKey)
        publishCustomProviders()
        toast("${name.trim().ifBlank { "Custom provider" }} saved.")
        onSaved?.invoke()
    }

    fun deleteCustomProvider(id: String, onDone: (() -> Unit)? = null) = task(null) {
        providerStore.deleteCustomProvider(id)
        clearSelectionIf { it.source == LocalSource.CUSTOM && it.customId == id }
        publishCustomProviders()
        toast("Provider removed.")
        onDone?.invoke()
    }

    fun setCustomEnabled(id: String, enabled: Boolean) = task(null) {
        val config = providerStore.customProvider(id) ?: return@task
        providerStore.saveCustomProvider(config.copy(enabled = enabled))
        publishCustomProviders()
    }

    fun testCustomProvider(
        baseUrl: String,
        apiKey: String,
        onResult: (ok: Boolean, detail: String) -> Unit
    ) = task(null) {
        try {
            val models = withContext(Dispatchers.IO) { llm.listModels(baseUrl.trim().trimEnd('/'), apiKey) }
            onResult(models.isNotEmpty(), if (models.isEmpty()) "Connected, but no models returned." else "Connected · ${models.size} models found.")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            onResult(false, error.message ?: "Test failed.")
        }
    }

    private fun publishCustomProviders() {
        val providers = providerStore.customProviders()
        _state.update {
            it.copy(
                customProviders = providers,
                customKeyMasks = providers.associate { p -> p.id to providerStore.customKeyMasked(p.id) }
            )
        }
    }

    private fun clearSelectionIf(predicate: (LocalModelChoice) -> Boolean) {
        _state.update { current ->
            if (!current.selection.isValid || predicate(current.selection)) {
                current.copy(selection = LocalModelChoice())
            } else current
        }
    }

    private fun toast(value: String) = _state.update { it.copy(message = value) }

    private fun task(label: String?, block: suspend () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, message = label ?: it.message) }
            try {
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                _state.update { it.copy(message = error.message ?: error.javaClass.simpleName) }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }
}
