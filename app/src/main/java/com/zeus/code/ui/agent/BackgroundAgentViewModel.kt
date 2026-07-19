package com.zeus.code.ui.agent

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zeus.code.data.BackgroundAgentApi
import com.zeus.code.data.BackgroundAgentApiException
import com.zeus.code.data.SecureTokenStore
import com.zeus.code.model.AgentBranch
import com.zeus.code.model.AgentLlmCatalog
import com.zeus.code.model.AgentLlmProviderEntry
import com.zeus.code.model.AgentLlmSavedProvider
import com.zeus.code.model.AgentLlmTestResponse
import com.zeus.code.model.AgentMeResponse
import com.zeus.code.model.AgentPairingState
import com.zeus.code.model.AgentProject
import com.zeus.code.model.AgentRepository
import com.zeus.code.model.AgentSession
import com.zeus.code.model.AgentUpload
import com.zeus.code.model.AgentWorker
import java.io.OutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


data class AgentUiState(
    val booting: Boolean = true,
    val authorized: Boolean = false,
    val offline: Boolean = false,
    val busy: Boolean = false,
    val message: String? = null,
    val pairing: AgentPairingState? = null,
    val me: AgentMeResponse? = null,
    val projects: List<AgentProject> = emptyList(),
    val sessions: List<AgentSession> = emptyList(),
    val repositories: List<AgentRepository> = emptyList(),
    val repositoryQuery: String = "",
    val branches: List<AgentBranch> = emptyList(),
    val selectedRepository: AgentRepository? = null,
    val selectedBranch: String = "",
    val selectedProject: AgentProject? = null,
    val selectedSession: AgentSession? = null,
    val archivedMode: Boolean = false,
    val worker: AgentWorker = AgentWorker(),
    val modelReady: Boolean = false,
    /** Provider/model catalog from NEBians (official + community + custom). */
    val llmCatalog: AgentLlmCatalog? = null,
    /** The user's saved BYOK provider rows. */
    val llmSavedProviders: List<AgentLlmSavedProvider> = emptyList(),
    /** Picker selection for the next task. Empty provider = default. */
    val llmSelection: AgentLlmSelection = AgentLlmSelection(),
    /** Sessions with server-side activity the user has not viewed yet. */
    val unreadIds: Set<String> = emptySet()
) {
    val filteredRepositories: List<AgentRepository>
        get() = repositories.filter {
            repositoryQuery.isBlank() || it.fullName.contains(repositoryQuery, true) ||
                it.description.contains(repositoryQuery, true)
        }
}

/**
 * The model/provider picked for the next background task.
 * Blank [provider] means "use the server default" (community Qwen).
 */
data class AgentLlmSelection(
    /** Catalog slug ('qwen', 'agnes', 'openai', 'custom', ...) or blank for default. */
    val provider: String = "",
    /** Concrete model id, e.g. 'agnes-2.0-flash' or 'gpt-5.4-mini'. */
    val model: String = "",
    /** [AgentLlmProviderEntry.id]/byokProviderId for custom or BYOK rows. */
    val providerId: String = "",
    /** Human label displayed in the picker and toasts. */
    val label: String = ""
) {
    val isDefault: Boolean get() = provider.isBlank()
}

class BackgroundAgentViewModel(application: Application) : AndroidViewModel(application) {
    private val api = BackgroundAgentApi()
    private val store = SecureTokenStore(application, "background_agent")
    private val seenPrefs = application.getSharedPreferences("zeus_agent_seen", android.content.Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(AgentUiState())
    val state: StateFlow<AgentUiState> = _state.asStateFlow()
    private var token: String? = null
    private var pairingJob: Job? = null
    private var refreshJob: Job? = null

    init {
        viewModelScope.launch {
            token = store.read()
            if (token != null) validateAndLoad()
            _state.update { it.copy(booting = false, authorized = token != null) }
            if (token != null) startRefreshLoop()
        }
    }

    fun startPairing() = task("Creating secure device authorization...") {
        pairingJob?.cancel()
        val response = api.startPairing("Zeus Android")
        val pairing = AgentPairingState(
            deviceCode = response.deviceCode,
            userCode = response.userCode,
            verificationUrl = response.verificationUrl,
            verificationUrlComplete = response.verificationUrlComplete,
            expiresAt = System.currentTimeMillis() + response.expiresIn * 1000L,
            intervalSeconds = response.interval.coerceAtLeast(2)
        )
        _state.update { it.copy(pairing = pairing, message = "Approve Zeus in NEBians with ${pairing.userCode}.") }
        pairingJob = viewModelScope.launch { pollPairing(pairing) }
    }

    fun cancelPairing() {
        pairingJob?.cancel()
        _state.update { it.copy(pairing = null, busy = false) }
    }

    fun disconnect() {
        val current = token
        token = null
        refreshJob?.cancel()
        pairingJob?.cancel()
        store.clear()
        _state.value = AgentUiState(booting = false, message = "Background Agent disconnected from Zeus.")
        if (current != null) viewModelScope.launch { runCatching { api.revoke(current) } }
    }

    fun refresh() = task(null) { refreshState() }
    fun dismissMessage() = _state.update { it.copy(message = null) }
    fun setRepositoryQuery(value: String) = _state.update { it.copy(repositoryQuery = value) }

    fun setArchivedMode(value: Boolean) = task(null) {
        _state.update { it.copy(archivedMode = value, selectedSession = null) }
        refreshState()
    }

    fun loadRepositories() = task("Loading GitHub repositories...") {
        _state.update { it.copy(repositories = api.repositories(requireToken()).repositories) }
    }

    fun selectRepository(repository: AgentRepository) = task("Loading branches...") {
        val response = api.branches(requireToken(), repository.fullName)
        _state.update {
            it.copy(
                selectedRepository = repository,
                branches = response.branches,
                selectedBranch = response.defaultBranch.ifBlank { repository.defaultBranch }
            )
        }
    }

    fun selectBranch(name: String) = _state.update { it.copy(selectedBranch = name) }

    fun clearRepositorySelection() = _state.update {
        it.copy(selectedRepository = null, branches = emptyList(), selectedBranch = "", repositoryQuery = "")
    }

    fun addSelectedProject(onAdded: (() -> Unit)? = null) = task("Adding project...") {
        val repo = _state.value.selectedRepository ?: error("Choose a repository first.")
        val branch = _state.value.selectedBranch.ifBlank { repo.defaultBranch }
        val project = api.addProject(requireToken(), repo.fullName, branch).project
        refreshState()
        _state.update { it.copy(selectedProject = project, selectedRepository = null, branches = emptyList()) }
        onAdded?.invoke()
        toast("${project.repoFullName} is ready for tasks.")
    }

    fun selectProject(project: AgentProject?) = _state.update { it.copy(selectedProject = project) }

    /** Toggle the per-project CI auto-fix automation (applied to new failed runs). */
    fun setProjectAutofix(project: AgentProject, enabled: Boolean) = task(null) {
        val updated = api.updateProjectSettings(requireToken(), project.id, enabled).project
        _state.update { state ->
            state.copy(
                projects = state.projects.map { if (it.id == updated.id) updated else it },
                selectedProject = state.selectedProject?.let { if (it.id == updated.id) updated else it },
                message = if (enabled) {
                    "Auto-fix enabled for ${project.repoFullName}."
                } else {
                    "Auto-fix disabled for ${project.repoFullName}."
                }
            )
        }
        runCatching { refreshState() }
    }

    fun createSession(goal: String, uploads: List<AgentUpload>, onCreated: (() -> Unit)? = null) = task("Starting background task...") {
        val project = _state.value.selectedProject ?: error("Choose a project first.")
        val selection = _state.value.llmSelection
        val session = api.createSession(
            requireToken(), project.id, goal.trim(), project.preferredBaseBranch, uploads,
            llmProvider = selection.provider,
            llmModel = selection.model,
            llmProviderId = selection.providerId
        ).session
        _state.update { it.copy(selectedSession = session, archivedMode = false) }
        refreshState(keepSelected = true)
        onCreated?.invoke()
        toast(if (selection.isDefault) "Task queued for the default model." else "Task queued for ${selection.label}.")
    }

    // ------------------------------------------------------------------
    // LLM provider selection + BYOK management
    // ------------------------------------------------------------------

    /** Pick a catalog entry for the next task; `null` restores the server default. */
    fun selectLlm(entry: AgentLlmProviderEntry?, modelId: String = "") {
        if (entry == null) {
            _state.update { it.copy(llmSelection = AgentLlmSelection()) }
            return
        }
        val model = modelId.ifBlank {
            entry.defaultModel.ifBlank { entry.models.firstOrNull()?.id.orEmpty() }
        }
        val providerId = entry.id.ifBlank { entry.byokProviderId }
        val modelLabel = entry.models.firstOrNull { it.id.equals(model, true) }?.displayLabel.orEmpty()
        _state.update {
            it.copy(
                llmSelection = AgentLlmSelection(
                    provider = entry.slug,
                    model = model,
                    providerId = providerId,
                    label = if (modelLabel.isBlank()) entry.label else "${entry.label} · $modelLabel"
                )
            )
        }
    }

    fun loadLlmProviders() = task(null) {
        _state.update { it.copy(llmSavedProviders = api.llmProviders(requireToken()).providers) }
    }

    /** Create a custom provider or store a preset BYOK key. */
    fun saveLlmProvider(
        fields: Map<String, String>,
        models: List<String>,
        onDone: (() -> Unit)? = null
    ) = task("Saving provider...") {
        val saved = api.saveLlmProvider(requireToken(), fields, models).provider
        refreshLlmData()
        onDone?.invoke()
        toast(if (saved != null && saved.name.isNotBlank()) "${saved.name} saved." else "Provider saved.")
    }

    fun updateLlmProvider(
        providerId: String,
        fields: Map<String, String>,
        models: List<String>?,
        onDone: (() -> Unit)? = null
    ) = task("Updating provider...") {
        api.updateLlmProvider(requireToken(), providerId, fields, models)
        refreshLlmData()
        onDone?.invoke()
        toast("Provider updated.")
    }

    fun deleteLlmProvider(providerId: String, onDone: (() -> Unit)? = null) = task("Removing provider...") {
        api.deleteLlmProvider(requireToken(), providerId)
        _state.update { st ->
            if (st.llmSelection.providerId == providerId) st.copy(llmSelection = AgentLlmSelection()) else st
        }
        refreshLlmData()
        onDone?.invoke()
        toast("Provider removed.")
    }

    /** One-shot connectivity test against NEBians (saved row or draft credentials). */
    fun testLlmProvider(fields: Map<String, String>, onResult: (AgentLlmTestResponse) -> Unit) = task(null) {
        onResult(api.testLlmProvider(requireToken(), fields))
    }

    /** Re-pulls saved BYOK rows and the catalog so availability hints stay fresh. */
    private suspend fun refreshLlmData() {
        val providers = runCatching { api.llmProviders(requireToken()).providers }
            .getOrElse { _state.value.llmSavedProviders }
        val catalog = runCatching { api.state(requireToken(), _state.value.archivedMode).llm }
            .getOrNull() ?: _state.value.llmCatalog
        _state.update { st ->
            st.copy(
                llmSavedProviders = providers,
                llmCatalog = catalog,
                llmSelection = sanitizeLlmSelection(catalog, st.llmSelection)
            )
        }
    }

    fun openSession(session: AgentSession) = task(null) {
        val fresh = api.session(requireToken(), session.id).session
        _state.update { it.copy(selectedSession = fresh) }
        markSeen(fresh)
    }

    fun closeSession() = _state.update { it.copy(selectedSession = null) }

    /** Record that the user has seen this session at its current update stamp. */
    fun markSeen(session: AgentSession) {
        seenPrefs.edit().putLong(session.id, session.updatedAt).apply()
        recomputeUnread()
    }

    private fun lastSeen(id: String): Long = seenPrefs.getLong(id, 0L)

    private fun computeUnread(sessions: List<AgentSession>): Set<String> =
        sessions.filter { it.updatedAt > lastSeen(it.id) }.mapTo(linkedSetOf()) { it.id }

    private fun recomputeUnread() {
        _state.update { it.copy(unreadIds = computeUnread(it.sessions)) }
    }

    fun sendMessage(content: String, uploads: List<AgentUpload>, onSent: (() -> Unit)? = null) = task("Sending guidance...") {
        val session = requireSession()
        api.sendMessage(requireToken(), session.id, content.trim(), uploads)
        reloadSelected()
        onSent?.invoke()
        toast("Guidance added to the task.")
    }

    fun control(command: String) = task("Sending ${command.lowercase()} request...") {
        val session = requireSession()
        _state.update { it.copy(selectedSession = api.control(requireToken(), session.id, command).session) }
        reloadSelected()
    }

    fun runAction(name: String) = task("Queueing action...") {
        api.action(requireToken(), requireSession().id, name)
        reloadSelected()
        toast("Action queued.")
    }

    fun archive(session: AgentSession = requireSession()) = task("Archiving task...") {
        api.lifecycle(requireToken(), session.id, "archive")
        if (_state.value.selectedSession?.id == session.id) _state.update { it.copy(selectedSession = null) }
        refreshState()
    }

    fun restore(session: AgentSession = requireSession()) = task("Restoring task...") {
        api.lifecycle(requireToken(), session.id, "restore")
        refreshState()
    }

    fun delete(session: AgentSession = requireSession()) = task("Deleting task...") {
        api.deleteSession(requireToken(), session.id)
        if (_state.value.selectedSession?.id == session.id) _state.update { it.copy(selectedSession = null) }
        refreshState()
    }

    fun prepareUploads(uris: List<Uri>, onReady: (List<AgentUpload>) -> Unit) = task("Reading attachments...") {
        val uploads = withContext(Dispatchers.IO) { readUploads(uris) }
        onReady(uploads)
    }

    fun downloadArtifact(downloadUrl: String, output: OutputStream, onComplete: () -> Unit) = task("Downloading artifact...") {
        try {
            api.download(requireToken(), downloadUrl, output)
        } finally {
            output.close()
        }
        onComplete()
        toast("Artifact saved.")
    }

    fun readUploads(uris: List<Uri>): List<AgentUpload> {
        val resolver = getApplication<Application>().contentResolver
        return uris.take(5).map { uri ->
            var name = "attachment"
            var size = 0L
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIndex >= 0 && !cursor.isNull(nameIndex)) name = cursor.getString(nameIndex) ?: name
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
                }
            }
            require(size <= 20L * 1024L * 1024L) { "$name exceeds the 20 MB attachment limit." }
            val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Unable to read $name")
            require(bytes.size <= 20 * 1024 * 1024) { "$name exceeds the 20 MB attachment limit." }
            AgentUpload(name, resolver.getType(uri) ?: "application/octet-stream", bytes)
        }
    }

    private suspend fun validateAndLoad() {
        val current = token ?: return
        try {
            val me = api.me(current)
            _state.update { it.copy(authorized = true, offline = false, me = me) }
            refreshState()
        } catch (error: BackgroundAgentApiException) {
            if (error.statusCode == 401) {
                store.clear()
                token = null
                _state.update { it.copy(authorized = false, message = "Zeus authorization expired. Connect once to continue.") }
            } else {
                _state.update { it.copy(authorized = true, offline = true, message = "NEBians is temporarily unavailable. Authorization is still saved.") }
            }
        } catch (_: Throwable) {
            _state.update { it.copy(authorized = true, offline = true, message = "Opened offline. Authorization is still saved.") }
        }
    }

    private suspend fun pollPairing(pairing: AgentPairingState) {
        while (currentCoroutineContext().isActive && System.currentTimeMillis() < pairing.expiresAt) {
            delay(pairing.intervalSeconds * 1000L)
            try {
                val result = api.pollPairing(pairing.deviceCode)
                if (result.accessToken.isNotBlank()) {
                    store.save(result.accessToken)
                    token = result.accessToken
                    _state.update { it.copy(pairing = null, authorized = true, offline = false, busy = false) }
                    validateAndLoad()
                    startRefreshLoop()
                    toast("Zeus is securely connected to NEBians.")
                    return
                }
            } catch (error: BackgroundAgentApiException) {
                if (error.errorCode == "authorization_pending" || error.statusCode == 428) continue
                _state.update { it.copy(pairing = null, busy = false, message = error.message) }
                return
            }
        }
        _state.update { it.copy(pairing = null, busy = false, message = "Authorization code expired. Start again.") }
    }

    private fun startRefreshLoop() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            while (isActive && token != null) {
                delay(if (_state.value.selectedSession != null) 3_000L else 12_000L)
                runCatching {
                    if (_state.value.selectedSession != null) reloadSelected() else refreshState()
                }
            }
        }
    }

    private suspend fun refreshState(keepSelected: Boolean = true) {
        val response = api.state(requireToken(), _state.value.archivedMode)
        val selectedId = _state.value.selectedSession?.id
        _state.update { state ->
            val catalog = response.llm ?: state.llmCatalog
            state.copy(
                authorized = true,
                offline = false,
                projects = response.projects,
                sessions = response.sessions,
                worker = response.worker,
                modelReady = response.model.configured,
                llmCatalog = catalog,
                llmSelection = sanitizeLlmSelection(catalog, state.llmSelection),
                selectedProject = state.selectedProject?.let { selected -> response.projects.firstOrNull { it.id == selected.id } ?: selected },
                selectedSession = if (keepSelected) state.selectedSession else null,
                unreadIds = computeUnread(response.sessions)
            )
        }
        if (keepSelected && selectedId != null) reloadSelected()
    }

    /**
     * Keeps the picked model valid as the catalog changes: resets to the server
     * default when the chosen provider disappeared or lost its key.
     */
    private fun sanitizeLlmSelection(catalog: AgentLlmCatalog?, selection: AgentLlmSelection): AgentLlmSelection {
        if (selection.isDefault || catalog == null) return selection
        val match: AgentLlmProviderEntry? = if (selection.provider == "custom") {
            catalog.custom.firstOrNull { it.id == selection.providerId }
        } else {
            (catalog.community + catalog.official).firstOrNull { it.slug == selection.provider }
        }
        if (match == null || !match.available) return AgentLlmSelection()
        return selection
    }

    private suspend fun reloadSelected() {
        val selected = _state.value.selectedSession ?: return
        val updated = api.session(requireToken(), selected.id).session
        _state.update { st -> st.copy(selectedSession = updated, unreadIds = st.unreadIds - updated.id) }
        // A session the user is looking at is always considered read.
        seenPrefs.edit().putLong(updated.id, updated.updatedAt).apply()
    }

    private fun requireToken(): String = token ?: error("Connect Zeus to NEBians first.")
    private fun requireSession(): AgentSession = _state.value.selectedSession ?: error("Open a task first.")
    private fun toast(value: String) = _state.update { it.copy(message = value) }

    private fun task(label: String?, block: suspend () -> Unit) {
        viewModelScope.launch {
            if (label != null) _state.update { it.copy(busy = true, message = label) }
            try {
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: BackgroundAgentApiException) {
                if (error.statusCode == 401) {
                    store.clear()
                    token = null
                    refreshJob?.cancel()
                    _state.update { it.copy(authorized = false, pairing = null, message = error.message) }
                } else {
                    _state.update { it.copy(message = error.message) }
                }
            } catch (error: Throwable) {
                _state.update { it.copy(message = error.message ?: error.javaClass.simpleName) }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }
}
