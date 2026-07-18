package com.zeus.code.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zeus.code.BuildConfig
import com.zeus.code.data.GitHubApi
import com.zeus.code.data.GitHubApiException
import com.zeus.code.data.GitService
import com.zeus.code.data.SecureTokenStore
import com.zeus.code.data.TerminalEngine
import com.zeus.code.data.WorkspaceManager
import com.zeus.code.model.Branch
import com.zeus.code.model.CommitInfo
import com.zeus.code.model.DeviceLoginState
import com.zeus.code.model.FileEntry
import com.zeus.code.model.GitHubUser
import com.zeus.code.model.Issue
import com.zeus.code.model.PullRequest
import com.zeus.code.model.Repository
import com.zeus.code.model.Workspace
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File


data class ZeusState(
    val booting: Boolean = true,
    val authenticated: Boolean = false,
    val offlineMode: Boolean = false,
    val busy: Boolean = false,
    val message: String? = null,
    val login: DeviceLoginState? = null,
    val user: GitHubUser? = null,
    val repositories: List<Repository> = emptyList(),
    val repoQuery: String = "",
    val selectedRepo: Repository? = null,
    val branches: List<Branch> = emptyList(),
    val pullRequests: List<PullRequest> = emptyList(),
    val issues: List<Issue> = emptyList(),
    val workspaces: List<Workspace> = emptyList(),
    val selectedWorkspace: Workspace? = null,
    val files: List<FileEntry> = emptyList(),
    val commits: List<CommitInfo> = emptyList(),
    val gitStatus: String = "Select a workspace to see Git status.",
    val terminalLines: List<String> = listOf("Zeus terminal ready. Select a workspace and type `git help`."),
    val terminalHistory: List<String> = emptyList()
) {
    val filteredRepositories: List<Repository>
        get() = repositories.filter {
            repoQuery.isBlank() || it.fullName.contains(repoQuery, ignoreCase = true) ||
                it.description.orEmpty().contains(repoQuery, ignoreCase = true)
        }
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val tokenStore = SecureTokenStore(application)
    private val api = GitHubApi()
    private val git = GitService()
    private val workspaces = WorkspaceManager(application, git)
    private val terminal = TerminalEngine(git)

    private val _state = MutableStateFlow(ZeusState())
    val state: StateFlow<ZeusState> = _state.asStateFlow()

    private var token: String? = null

    init {
        viewModelScope.launch {
            token = tokenStore.read()
            refreshWorkspacesInternal()
            if (token != null) {
                try {
                    loadAccount(token!!)
                } catch (error: GitHubApiException) {
                    if (error.statusCode == 401) {
                        tokenStore.clear()
                        token = null
                        _state.update { it.copy(message = "GitHub authorization expired. Sign in once to reconnect.") }
                    } else {
                        _state.update { it.copy(authenticated = true, offlineMode = true, message = "GitHub is temporarily unavailable. Your authorization is still saved.") }
                    }
                } catch (error: Throwable) {
                    _state.update { it.copy(authenticated = true, offlineMode = true, message = "Zeus opened offline. Your GitHub authorization is still saved.") }
                }
            }
            _state.update { it.copy(booting = false, authenticated = token != null) }
        }
    }

    fun startLogin() = task("Starting GitHub login…") {
        val device = api.requestDeviceCode(BuildConfig.OAUTH_CLIENT_ID)
        _state.update {
            it.copy(
                login = DeviceLoginState(device.userCode, device.verificationUri, device.expiresIn),
                message = "Enter ${device.userCode} on GitHub to authorize Zeus."
            )
        }
        val accessToken = api.pollForToken(BuildConfig.OAUTH_CLIENT_ID, device)
        tokenStore.save(accessToken)
        token = accessToken
        loadAccount(accessToken)
        _state.update { it.copy(authenticated = true, offlineMode = false, login = null) }
    }

    fun continueOffline() {
        _state.update { it.copy(booting = false, offlineMode = true, authenticated = false, login = null) }
    }

    fun logout() {
        tokenStore.clear()
        token = null
        _state.update {
            it.copy(
                authenticated = false,
                offlineMode = false,
                user = null,
                repositories = emptyList(),
                selectedRepo = null,
                login = null,
                message = "Signed out."
            )
        }
    }

    fun dismissMessage() = _state.update { it.copy(message = null) }
    fun setRepoQuery(value: String) = _state.update { it.copy(repoQuery = value) }

    fun refreshAccount() = task("Refreshing GitHub…") {
        val accessToken = requireToken()
        loadAccount(accessToken)
    }

    fun createRepository(name: String, description: String, private: Boolean, autoInit: Boolean) =
        task("Creating repository…") {
            api.createRepository(requireToken(), name, description, private, autoInit)
            loadRepositories(requireToken())
            toast("Repository created.")
        }

    fun deleteRepository(repository: Repository) = task("Deleting ${repository.fullName}…") {
        api.deleteRepository(requireToken(), repository.owner.login, repository.name)
        loadRepositories(requireToken())
        if (_state.value.selectedRepo?.id == repository.id) closeRepository()
        toast("Repository deleted.")
    }

    fun forkRepository(repository: Repository) = task("Forking ${repository.fullName}…") {
        api.forkRepository(requireToken(), repository.owner.login, repository.name)
        loadRepositories(requireToken())
        toast("Fork requested. GitHub may take a few seconds to finish it.")
    }

    fun selectRepository(repository: Repository) = task("Loading repository…") {
        _state.update { it.copy(selectedRepo = repository, branches = emptyList(), pullRequests = emptyList(), issues = emptyList()) }
        val accessToken = requireToken()
        val owner = repository.owner.login
        _state.update {
            it.copy(
                branches = api.branches(accessToken, owner, repository.name),
                pullRequests = api.pulls(accessToken, owner, repository.name),
                issues = api.issues(accessToken, owner, repository.name)
            )
        }
    }

    fun refreshSelectedRepository() {
        _state.value.selectedRepo?.let(::selectRepository)
    }

    fun closeRepository() = _state.update {
        it.copy(selectedRepo = null, branches = emptyList(), pullRequests = emptyList(), issues = emptyList())
    }

    fun createIssue(title: String, body: String) = task("Creating issue…") {
        val repo = requireRepo()
        api.createIssue(requireToken(), repo.owner.login, repo.name, title, body)
        selectRepositoryData(repo)
        toast("Issue created.")
    }

    fun createPull(title: String, head: String, base: String, body: String) = task("Creating pull request…") {
        val repo = requireRepo()
        api.createPullRequest(requireToken(), repo.owner.login, repo.name, title, head, base, body)
        selectRepositoryData(repo)
        toast("Pull request created.")
    }

    fun mergePull(pull: PullRequest, method: String) = task("Merging pull request…") {
        val repo = requireRepo()
        val result = api.mergePullRequest(requireToken(), repo.owner.login, repo.name, pull.number, method)
        selectRepositoryData(repo)
        toast(if (result.merged) "Pull request merged." else result.message)
    }

    fun reviewPull(pull: PullRequest, body: String, event: String) = task("Submitting review…") {
        val repo = requireRepo()
        api.reviewPullRequest(requireToken(), repo.owner.login, repo.name, pull.number, body, event)
        toast("Review submitted.")
    }

    fun importWorkspace(uri: Uri) = task("Importing folder…") {
        val workspace = workspaces.importTree(uri)
        refreshWorkspacesInternal()
        selectWorkspaceInternal(workspace)
        toast("Folder imported into Zeus private storage.")
    }

    fun exportWorkspace(uri: Uri) = task("Exporting workspace…") {
        workspaces.exportTree(requireWorkspace(), uri)
        toast("Workspace exported. The .git directory is intentionally excluded.")
    }

    fun createWorkspace(name: String, initializeGit: Boolean) = task("Creating workspace…") {
        val workspace = workspaces.create(name, initializeGit)
        refreshWorkspacesInternal()
        selectWorkspaceInternal(workspace)
        toast("Workspace created.")
    }

    fun cloneRepository(repository: Repository) = cloneUrl(repository.cloneUrl, repository.name)

    fun cloneUrl(
        url: String,
        preferredName: String = url.substringAfterLast('/').removeSuffix(".git"),
        branch: String? = null
    ) = task("Cloning repository…") {
        val destination = workspaces.destination(preferredName)
        val actual = if (destination.exists()) {
            workspaces.destination("$preferredName-${System.currentTimeMillis().toString().takeLast(4)}")
        } else {
            destination
        }
        git.clone(url, actual, token, branch)
        refreshWorkspacesInternal()
        selectWorkspaceInternal(workspaces.workspaceAt(actual))
        toast(branch?.let { "Repository cloned on $it." } ?: "Repository cloned.")
    }

    fun selectWorkspace(workspace: Workspace) = task("Opening workspace…") {
        selectWorkspaceInternal(workspace)
    }

    fun deleteWorkspace(workspace: Workspace) = task("Deleting workspace…") {
        workspaces.delete(workspace)
        if (_state.value.selectedWorkspace?.path == workspace.path) {
            _state.update { it.copy(selectedWorkspace = null, files = emptyList(), commits = emptyList()) }
        }
        refreshWorkspacesInternal()
        toast("Local workspace deleted. The GitHub repository was not changed.")
    }

    fun closeWorkspace() {
        _state.update { it.copy(selectedWorkspace = null, files = emptyList(), commits = emptyList(), gitStatus = "Select a workspace to see Git status.") }
    }

    fun refreshWorkspace() = task("Refreshing workspace…") {
        val current = requireWorkspace()
        refreshWorkspacesInternal()
        val updated = _state.value.workspaces.firstOrNull { it.path == current.path } ?: current
        selectWorkspaceInternal(updated)
    }

    fun initializeGit() = task("Initializing Git…") {
        val workspace = requireWorkspace()
        git.init(workspace.directory)
        refreshWorkspacesInternal()
        val updated = _state.value.workspaces.firstOrNull { it.path == workspace.path } ?: workspace.copy(gitRepository = true)
        selectWorkspaceInternal(updated)
        toast("Git repository initialized.")
    }

    fun createPath(relativePath: String, directory: Boolean) = task("Creating path…") {
        val workspace = requireWorkspace()
        workspaces.createFile(workspace, relativePath, directory)
        refreshFiles(workspace)
    }

    fun deletePath(entry: FileEntry) = task("Deleting ${entry.name}…") {
        val workspace = requireWorkspace()
        workspaces.deleteFile(workspace, File(entry.path))
        refreshFiles(workspace)
    }

    suspend fun readFile(entry: FileEntry): String = workspaces.read(File(entry.path))

    fun saveFile(entry: FileEntry, content: String) = task("Saving ${entry.name}…") {
        workspaces.write(File(entry.path), content)
        refreshFiles(requireWorkspace())
        refreshGitInfo(requireWorkspace())
        toast("Saved ${entry.name}.")
    }

    fun commit(message: String) = task("Committing changes…") {
        val workspace = requireGitWorkspace()
        val user = _state.value.user
        val hash = git.commit(
            workspace.directory,
            message,
            user?.name ?: user?.login ?: "Zeus User",
            user?.let { "${it.id}+${it.login}@users.noreply.github.com" } ?: "zeus@local"
        )
        refreshGitInfo(workspace)
        toast("Committed ${hash.take(8)}.")
    }

    fun push(force: Boolean = false) = gitTask(if (force) "Force pushing…" else "Pushing…") {
        git.push(it.directory, token, force)
    }

    fun pull() = gitTask("Pulling…") { git.pull(it.directory, token) }
    fun fetch() = gitTask("Fetching…") { git.fetch(it.directory, token) }
    fun hardReset(target: String) = gitTask("Resetting…") { git.hardReset(it.directory, target) }
    fun revert(commit: String) = gitTask("Reverting…") { git.revert(it.directory, commit) }
    fun merge(branch: String) = gitTask("Merging…") { git.merge(it.directory, branch) }
    fun stash() = gitTask("Stashing…") { git.stash(it.directory) }
    fun stashApply() = gitTask("Applying stash…") { git.stashApply(it.directory) }

    fun checkout(branch: String, create: Boolean) = gitTask("Switching branch…") {
        git.checkout(it.directory, branch, create)
    }

    fun setRemote(url: String) = gitTask("Updating remote…") {
        git.setRemote(it.directory, url)
        "Remote set to $url"
    }

    fun runTerminal(command: String) = task(null) {
        val workspace = requireWorkspace()
        _state.update {
            it.copy(
                terminalLines = (it.terminalLines + "\n${workspace.name} \$ $command").takeLast(600),
                terminalHistory = (listOf(command) + it.terminalHistory.filterNot { old -> old == command }).take(50)
            )
        }
        val result = terminal.execute(workspace, command, token)
        _state.update { it.copy(terminalLines = (it.terminalLines + result).takeLast(600)) }
        refreshGitInfo(workspace)
        refreshFiles(workspace)
    }

    fun clearTerminal() = _state.update { it.copy(terminalLines = listOf("Terminal cleared.")) }

    private fun gitTask(label: String, operation: suspend (Workspace) -> String) = task(label) {
        val workspace = requireGitWorkspace()
        val result = operation(workspace)
        refreshGitInfo(workspace)
        refreshFiles(workspace)
        toast(result.ifBlank { "Done." })
    }

    private suspend fun loadAccount(accessToken: String) {
        val user = api.user(accessToken)
        _state.update { it.copy(user = user, authenticated = true, offlineMode = false) }
        loadRepositories(accessToken)
    }

    private suspend fun loadRepositories(accessToken: String) {
        _state.update { it.copy(repositories = api.repositories(accessToken)) }
    }

    private suspend fun selectRepositoryData(repo: Repository) {
        val accessToken = requireToken()
        _state.update {
            it.copy(
                branches = api.branches(accessToken, repo.owner.login, repo.name),
                pullRequests = api.pulls(accessToken, repo.owner.login, repo.name),
                issues = api.issues(accessToken, repo.owner.login, repo.name)
            )
        }
    }

    private suspend fun refreshWorkspacesInternal() {
        _state.update { it.copy(workspaces = workspaces.list()) }
    }

    private suspend fun selectWorkspaceInternal(workspace: Workspace) {
        val updated = workspaces.list().firstOrNull { it.path == workspace.path } ?: workspace
        _state.update { it.copy(selectedWorkspace = updated) }
        refreshFiles(updated)
        refreshGitInfo(updated)
    }

    private suspend fun refreshFiles(workspace: Workspace) {
        _state.update { it.copy(files = workspaces.files(workspace)) }
    }

    private suspend fun refreshGitInfo(workspace: Workspace) {
        if (!File(workspace.path, ".git").exists()) {
            _state.update { it.copy(gitStatus = "This workspace is not initialized as a Git repository.", commits = emptyList()) }
            return
        }
        val status = git.status(workspace.directory)
        val commits = runCatching { git.log(workspace.directory) }.getOrDefault(emptyList())
        _state.update { it.copy(gitStatus = status.pretty(), commits = commits) }
        refreshWorkspacesInternal()
        _state.value.workspaces.firstOrNull { it.path == workspace.path }?.let { refreshed ->
            _state.update { it.copy(selectedWorkspace = refreshed) }
        }
    }

    private fun requireToken(): String = token ?: error("Sign in to GitHub first.")
    private fun requireRepo(): Repository = _state.value.selectedRepo ?: error("Select a repository first.")
    private fun requireWorkspace(): Workspace = _state.value.selectedWorkspace ?: error("Select a workspace first.")
    private fun requireGitWorkspace(): Workspace = requireWorkspace().also {
        require(File(it.path, ".git").exists()) { "This workspace is not a Git repository." }
    }

    private fun toast(message: String) = _state.update { it.copy(message = message) }

    private fun task(label: String?, block: suspend () -> Unit) {
        viewModelScope.launch {
            if (label != null) _state.update { it.copy(busy = true, message = label) }
            try {
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: GitHubApiException) {
                if (error.statusCode == 401) {
                    tokenStore.clear()
                    token = null
                    _state.update {
                        it.copy(
                            authenticated = false,
                            offlineMode = false,
                            user = null,
                            repositories = emptyList(),
                            selectedRepo = null,
                            message = "GitHub authorization expired. Sign in once to reconnect."
                        )
                    }
                } else {
                    _state.update { it.copy(message = error.message) }
                }
            } catch (error: Throwable) {
                val msg = error.message.orEmpty()
                // Detect obfuscated messages from R8/ProGuard (e.g., "H2.a<init>[]")
                val isObfuscated = msg.isNotEmpty() && (
                    msg.contains("<init>") ||
                    msg.matches(Regex("^[A-Z][a-z]?[0-9]*\\.[A-Za-z]")) ||
                    (msg.length < 20 && !msg.any { it.isLetterOrDigit() || it == ' ' || it == '.' || it == ':' })
                )
                val displayMessage = if (isObfuscated) {
                    "An unexpected error occurred. Please try again."
                } else {
                    msg.ifBlank { error.javaClass.simpleName }
                }
                _state.update { it.copy(message = displayMessage) }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }
}
