package com.zeus.code.ui

import android.app.Application
import android.content.Context
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
import com.zeus.code.model.ActionArtifact
import com.zeus.code.model.Branch
import com.zeus.code.model.CommitInfo
import com.zeus.code.model.DeviceLoginState
import com.zeus.code.model.FileEntry
import com.zeus.code.model.GitHubUser
import com.zeus.code.model.Issue
import com.zeus.code.model.PullRequest
import com.zeus.code.model.Repository
import com.zeus.code.model.RunJob
import com.zeus.code.model.WorkflowRun
import com.zeus.code.model.Workspace
import com.zeus.code.ui.theme.ZeusThemeMode
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
    val themeMode: ZeusThemeMode = ZeusThemeMode.LIGHT,
    val login: DeviceLoginState? = null,
    val user: GitHubUser? = null,
    val repositories: List<Repository> = emptyList(),
    val repoQuery: String = "",
    val selectedRepo: Repository? = null,
    val branches: List<Branch> = emptyList(),
    val pullRequests: List<PullRequest> = emptyList(),
    val issues: List<Issue> = emptyList(),
    val actionRuns: List<WorkflowRun> = emptyList(),
    val expandedRunId: Long = 0L,
    val actionJobs: List<RunJob> = emptyList(),
    val actionArtifacts: List<ActionArtifact> = emptyList(),
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
    private val settings = application.getSharedPreferences("zeus_settings", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(ZeusState())
    val state: StateFlow<ZeusState> = _state.asStateFlow()

    private var token: String? = null

    init {
        val savedTheme = runCatching {
            ZeusThemeMode.valueOf(settings.getString("theme_mode", null).orEmpty())
        }.getOrDefault(ZeusThemeMode.LIGHT)
        _state.update { it.copy(themeMode = savedTheme) }
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

    fun startLogin() = task("Starting GitHub login...") {
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

    fun setThemeMode(mode: ZeusThemeMode) {
        if (mode == _state.value.themeMode) return
        settings.edit().putString("theme_mode", mode.name).apply()
        _state.update { it.copy(themeMode = mode) }
    }

    fun refreshAccount() = task("Refreshing GitHub...") {
        val accessToken = requireToken()
        loadAccount(accessToken)
    }

    fun createRepository(name: String, description: String, private: Boolean, autoInit: Boolean) =
        task("Creating repository...") {
            api.createRepository(requireToken(), name, description, private, autoInit)
            loadRepositories(requireToken())
            toast("Repository created.")
        }

    /**
     * Creates a GitHub repository AND seeds it with local content in one flow:
     * new empty repo > new local workspace > import content > initial commit > push.
     * The content can be a ZIP (auto-extracted) and/or a set of picked files.
     */
    fun createRepositoryFromContent(
        name: String,
        description: String,
        private: Boolean,
        zipUri: Uri?,
        fileUris: List<Uri>
    ) = task("Creating repository from your content...") {
        val accessToken = requireToken()
        require(zipUri != null || fileUris.isNotEmpty()) { "Attach a ZIP or pick files to seed the repository." }
        val repo = api.createRepository(accessToken, name, description, private, autoInit = false)
        val workspace = workspaces.create(name, initializeGit = true)
        try {
            if (zipUri != null) workspaces.importZipInto(workspace.directory, zipUri)
            if (fileUris.isNotEmpty()) workspaces.importFiles(workspace, fileUris)
            git.setRemote(workspace.directory, repo.cloneUrl)
            val (authorName, authorEmail) = authorIdentity()
            git.commit(workspace.directory, "Initial commit", authorName, authorEmail)
            git.push(workspace.directory, accessToken)
        } catch (error: Throwable) {
            // Roll the local workspace back when the seed fails — the (empty)
            // GitHub repo stays for the user to keep or delete.
            runCatching { workspaces.delete(workspace) }
            throw error
        }
        loadRepositories(accessToken)
        refreshWorkspacesInternal()
        selectWorkspaceInternal(workspace)
        toast("Repository created and your content was pushed.")
    }

    /** Latest workflow runs for the opened repository. */
    fun refreshActionRuns() {
        val repo = _state.value.selectedRepo ?: return
        task(null) {
            _state.update {
                it.copy(actionRuns = runCatching { api.workflowRuns(requireToken(), repo.owner.login, repo.name) }.getOrDefault(emptyList()))
            }
        }
    }

    fun expandActionRun(run: WorkflowRun) = task(null) {
        if (_state.value.expandedRunId == run.id) {
            _state.update { it.copy(expandedRunId = 0L, actionJobs = emptyList(), actionArtifacts = emptyList()) }
            return@task
        }
        _state.update { it.copy(expandedRunId = run.id, actionJobs = emptyList(), actionArtifacts = emptyList()) }
        val repo = requireRepo()
        val accessToken = requireToken()
        _state.update {
            it.copy(
                actionJobs = runCatching { api.runJobs(accessToken, repo.owner.login, repo.name, run.id) }.getOrDefault(emptyList()),
                actionArtifacts = runCatching { api.runArtifacts(accessToken, repo.owner.login, repo.name, run.id) }.getOrDefault(emptyList())
            )
        }
    }

    fun downloadActionArtifact(artifact: ActionArtifact, output: java.io.OutputStream) = task("Downloading ${artifact.name}...") {
        api.downloadArchive(requireToken(), artifact.archiveDownloadUrl, output)
        toast("${artifact.name} downloaded.")
    }

    fun deleteRepository(repository: Repository) = task("Deleting ${repository.fullName}...") {
        api.deleteRepository(requireToken(), repository.owner.login, repository.name)
        loadRepositories(requireToken())
        if (_state.value.selectedRepo?.id == repository.id) closeRepository()
        toast("Repository deleted.")
    }

    fun forkRepository(repository: Repository) = task("Forking ${repository.fullName}...") {
        api.forkRepository(requireToken(), repository.owner.login, repository.name)
        loadRepositories(requireToken())
        toast("Fork requested. GitHub may take a few seconds to finish it.")
    }

    fun selectRepository(repository: Repository) = task("Loading repository...") {
        _state.update {
            it.copy(
                selectedRepo = repository,
                branches = emptyList(),
                pullRequests = emptyList(),
                issues = emptyList(),
                actionRuns = emptyList(),
                expandedRunId = 0L,
                actionJobs = emptyList(),
                actionArtifacts = emptyList()
            )
        }
        val accessToken = requireToken()
        val owner = repository.owner.login
        _state.update {
            it.copy(
                branches = api.branches(accessToken, owner, repository.name),
                pullRequests = api.pulls(accessToken, owner, repository.name),
                issues = api.issues(accessToken, owner, repository.name),
                actionRuns = runCatching { api.workflowRuns(accessToken, owner, repository.name) }.getOrDefault(emptyList())
            )
        }
    }

    fun refreshSelectedRepository() {
        _state.value.selectedRepo?.let(::selectRepository)
    }

    fun closeRepository() = _state.update {
        it.copy(
            selectedRepo = null,
            branches = emptyList(),
            pullRequests = emptyList(),
            issues = emptyList(),
            actionRuns = emptyList(),
            expandedRunId = 0L,
            actionJobs = emptyList(),
            actionArtifacts = emptyList()
        )
    }

    fun createIssue(title: String, body: String) = task("Creating issue...") {
        val repo = requireRepo()
        api.createIssue(requireToken(), repo.owner.login, repo.name, title, body)
        selectRepositoryData(repo)
        toast("Issue created.")
    }

    fun createPull(title: String, head: String, base: String, body: String) = task("Creating pull request...") {
        val repo = requireRepo()
        api.createPullRequest(requireToken(), repo.owner.login, repo.name, title, head, base, body)
        selectRepositoryData(repo)
        toast("Pull request created.")
    }

    fun mergePull(pull: PullRequest, method: String) = task("Merging pull request...") {
        val repo = requireRepo()
        val result = api.mergePullRequest(requireToken(), repo.owner.login, repo.name, pull.number, method)
        selectRepositoryData(repo)
        toast(if (result.merged) "Pull request merged." else result.message)
    }

    fun reviewPull(pull: PullRequest, body: String, event: String) = task("Submitting review...") {
        val repo = requireRepo()
        api.reviewPullRequest(requireToken(), repo.owner.login, repo.name, pull.number, body, event)
        toast("Review submitted.")
    }

    fun importWorkspace(uri: Uri) = task("Importing folder...") {
        val workspace = workspaces.importTree(uri)
        refreshWorkspacesInternal()
        selectWorkspaceInternal(workspace)
        toast("Folder imported into Zeus private storage.")
    }

    fun exportWorkspace(uri: Uri) = task("Exporting workspace...") {
        workspaces.exportTree(requireWorkspace(), uri)
        toast("Workspace exported. The .git directory is intentionally excluded.")
    }

    fun createWorkspace(name: String, initializeGit: Boolean) = task("Creating workspace...") {
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
    ) = task("Cloning repository...") {
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

    fun selectWorkspace(workspace: Workspace) = task("Opening workspace...") {
        selectWorkspaceInternal(workspace)
    }

    fun refreshWorkspacesList() = task(null) { refreshWorkspacesInternal() }

    suspend fun workspaceBranches(): List<String> =
        runCatching { git.branches(requireWorkspace().directory) }.getOrDefault(emptyList())

    fun deleteWorkspace(workspace: Workspace) = task("Deleting workspace...") {
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

    fun refreshWorkspace() = task("Refreshing workspace...") {
        val current = requireWorkspace()
        refreshWorkspacesInternal()
        val updated = _state.value.workspaces.firstOrNull { it.path == current.path } ?: current
        selectWorkspaceInternal(updated)
    }

    fun initializeGit() = task("Initializing Git...") {
        val workspace = requireWorkspace()
        git.init(workspace.directory)
        refreshWorkspacesInternal()
        val updated = _state.value.workspaces.firstOrNull { it.path == workspace.path } ?: workspace.copy(gitRepository = true)
        selectWorkspaceInternal(updated)
        toast("Git repository initialized.")
    }

    fun createPath(relativePath: String, directory: Boolean) = task("Creating path...") {
        val workspace = requireWorkspace()
        workspaces.createFile(workspace, relativePath, directory)
        refreshFiles(workspace)
    }

    fun deletePath(entry: FileEntry) = task("Deleting ${entry.name}...") {
        val workspace = requireWorkspace()
        workspaces.deleteFile(workspace, File(entry.path))
        refreshFiles(workspace)
    }

    suspend fun readFile(entry: FileEntry): String = workspaces.read(File(entry.path))

    /** Unified diff of a commit against its parent, for the inline diff viewer. */
    suspend fun commitDiffText(hash: String): String {
        val workspace = requireGitWorkspace()
        return runCatching { git.commitDiff(workspace.directory, hash) }
            .getOrElse { "Unable to build the diff: ${it.message ?: it.javaClass.simpleName}" }
    }

    /** Copies picked files into the open workspace root; same-named files are replaced. */
    fun importFilesIntoWorkspace(uris: List<Uri>) = task("Importing files...") {
        val workspace = requireWorkspace()
        val count = workspaces.importFiles(workspace, uris)
        refreshFiles(workspace)
        refreshGitInfo(workspace)
        toast(if (count == 1) "Imported 1 file." else "Imported $count files.")
    }

    /** Extracts a ZIP into the open workspace, replacing same-path files exactly. */
    fun importZipIntoWorkspace(uri: Uri) = task("Extracting ZIP into workspace...") {
        val workspace = requireWorkspace()
        val count = workspaces.importZipInto(workspace.directory, uri)
        refreshFiles(workspace)
        refreshGitInfo(workspace)
        toast(if (count == 1) "Extracted 1 file." else "Extracted $count files.")
    }

    /** Creates a brand-new workspace from a ZIP the user picked. */
    fun importZipAsWorkspace(uri: Uri) = task("Creating workspace from ZIP...") {
        val workspace = workspaces.importZipAsWorkspace(uri, null)
        refreshWorkspacesInternal()
        selectWorkspaceInternal(workspace)
        toast("Workspace created from ZIP.")
    }

    /** Creates a brand-new workspace from picked documents. */
    fun importFilesAsWorkspace(uris: List<Uri>) = task("Creating workspace from files...") {
        val workspace = workspaces.importFilesAsWorkspace(uris, null)
        refreshWorkspacesInternal()
        selectWorkspaceInternal(workspace)
        toast("Workspace created from files.")
    }

    fun saveFile(entry: FileEntry, content: String) = task("Saving ${entry.name}...") {
        workspaces.write(File(entry.path), content)
        refreshFiles(requireWorkspace())
        refreshGitInfo(requireWorkspace())
        toast("Saved ${entry.name}.")
    }

    fun commit(message: String) = task("Committing changes...") {
        val workspace = requireGitWorkspace()
        val (authorName, authorEmail) = authorIdentity()
        val hash = git.commit(workspace.directory, message, authorName, authorEmail)
        refreshGitInfo(workspace)
        toast("Committed ${hash.take(8)}.")
    }

    fun push(force: Boolean = false) = gitTask(if (force) "Force pushing..." else "Pushing...") {
        git.push(it.directory, token, force)
    }

    fun pull() = gitTask("Pulling...") { git.pull(it.directory, token) }
    fun fetch() = gitTask("Fetching...") { git.fetch(it.directory, token) }
    fun hardReset(target: String) = gitTask("Resetting...") { git.hardReset(it.directory, target) }
    fun revert(commit: String) = gitTask("Reverting...") { git.revert(it.directory, commit) }
    fun merge(branch: String) = gitTask("Merging...") { git.merge(it.directory, branch) }
    fun stash() = gitTask("Stashing...") { git.stash(it.directory) }
    fun stashApply() = gitTask("Applying stash...") { git.stashApply(it.directory) }

    fun checkout(branch: String, create: Boolean) = gitTask("Switching branch...") {
        git.checkout(it.directory, branch, create)
    }

    fun setRemote(url: String) = gitTask("Updating remote...") {
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
                issues = api.issues(accessToken, repo.owner.login, repo.name),
                actionRuns = runCatching { api.workflowRuns(accessToken, repo.owner.login, repo.name) }.getOrDefault(emptyList())
            )
        }
    }

    private fun authorIdentity(): Pair<String, String> {
        val account = _state.value.user
        return (account?.name ?: account?.login ?: "Zeus User") to
            (account?.let { "${it.id}+${it.login}@users.noreply.github.com" } ?: "zeus@local")
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
