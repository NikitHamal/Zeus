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
    val modelReady: Boolean = false
) {
    val filteredRepositories: List<AgentRepository>
        get() = repositories.filter {
            repositoryQuery.isBlank() || it.fullName.contains(repositoryQuery, true) ||
                it.description.contains(repositoryQuery, true)
        }
}

class BackgroundAgentViewModel(application: Application) : AndroidViewModel(application) {
    private val api = BackgroundAgentApi()
    private val store = SecureTokenStore(application, "background_agent")
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

    fun startPairing() = task("Creating secure device authorization…") {
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

    fun loadRepositories() = task("Loading GitHub repositories…") {
        _state.update { it.copy(repositories = api.repositories(requireToken()).repositories) }
    }

    fun selectRepository(repository: AgentRepository) = task("Loading branches…") {
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

    fun addSelectedProject(onAdded: (() -> Unit)? = null) = task("Adding project…") {
        val repo = _state.value.selectedRepository ?: error("Choose a repository first.")
        val branch = _state.value.selectedBranch.ifBlank { repo.defaultBranch }
        val project = api.addProject(requireToken(), repo.fullName, branch).project
        refreshState()
        _state.update { it.copy(selectedProject = project, selectedRepository = null, branches = emptyList()) }
        onAdded?.invoke()
        toast("${project.repoFullName} is ready for tasks.")
    }

    fun selectProject(project: AgentProject?) = _state.update { it.copy(selectedProject = project) }

    fun createSession(goal: String, uploads: List<AgentUpload>, onCreated: (() -> Unit)? = null) = task("Starting background task…") {
        val project = _state.value.selectedProject ?: error("Choose a project first.")
        val session = api.createSession(
            requireToken(), project.id, goal.trim(), project.preferredBaseBranch, uploads
        ).session
        _state.update { it.copy(selectedSession = session, archivedMode = false) }
        refreshState(keepSelected = true)
        onCreated?.invoke()
        toast("Task queued for Qwen 3.7 Plus.")
    }

    fun openSession(session: AgentSession) = task(null) {
        _state.update { it.copy(selectedSession = api.session(requireToken(), session.id).session) }
    }

    fun closeSession() = _state.update { it.copy(selectedSession = null) }

    fun sendMessage(content: String, uploads: List<AgentUpload>, onSent: (() -> Unit)? = null) = task("Sending guidance…") {
        val session = requireSession()
        api.sendMessage(requireToken(), session.id, content.trim(), uploads)
        reloadSelected()
        onSent?.invoke()
        toast("Guidance added to the task.")
    }

    fun control(command: String) = task("Sending ${command.lowercase()} request…") {
        val session = requireSession()
        _state.update { it.copy(selectedSession = api.control(requireToken(), session.id, command).session) }
        reloadSelected()
    }

    fun runAction(name: String) = task("Queueing action…") {
        api.action(requireToken(), requireSession().id, name)
        reloadSelected()
        toast("Action queued.")
    }

    fun archive(session: AgentSession = requireSession()) = task("Archiving task…") {
        api.lifecycle(requireToken(), session.id, "archive")
        if (_state.value.selectedSession?.id == session.id) _state.update { it.copy(selectedSession = null) }
        refreshState()
    }

    fun restore(session: AgentSession = requireSession()) = task("Restoring task…") {
        api.lifecycle(requireToken(), session.id, "restore")
        refreshState()
    }

    fun delete(session: AgentSession = requireSession()) = task("Deleting task…") {
        api.deleteSession(requireToken(), session.id)
        if (_state.value.selectedSession?.id == session.id) _state.update { it.copy(selectedSession = null) }
        refreshState()
    }

    fun prepareUploads(uris: List<Uri>, onReady: (List<AgentUpload>) -> Unit) = task("Reading attachments…") {
        val uploads = withContext(Dispatchers.IO) { readUploads(uris) }
        onReady(uploads)
    }

    fun downloadArtifact(downloadUrl: String, output: OutputStream, onComplete: () -> Unit) = task("Downloading artifact…") {
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
        while (isActive && System.currentTimeMillis() < pairing.expiresAt) {
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
            state.copy(
                authorized = true,
                offline = false,
                projects = response.projects,
                sessions = response.sessions,
                worker = response.worker,
                modelReady = response.model.configured,
                selectedProject = state.selectedProject?.let { selected -> response.projects.firstOrNull { it.id == selected.id } ?: selected },
                selectedSession = if (keepSelected) state.selectedSession else null
            )
        }
        if (keepSelected && selectedId != null) reloadSelected()
    }

    private suspend fun reloadSelected() {
        val selected = _state.value.selectedSession ?: return
        val updated = api.session(requireToken(), selected.id).session
        _state.update { it.copy(selectedSession = updated) }
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
