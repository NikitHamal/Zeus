package com.zeus.code.data

import com.zeus.code.model.CommitInfo
import com.zeus.code.model.GitStatusSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.ListBranchCommand
import org.eclipse.jgit.api.ResetCommand
import org.eclipse.jgit.api.errors.GitAPIException
import org.eclipse.jgit.errors.TransportException
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import java.io.File
import java.net.URI

class GitService {
    private fun credentials(token: String?) = token?.takeIf { it.isNotBlank() }
        ?.let { UsernamePasswordCredentialsProvider("x-access-token", it) }

    suspend fun clone(url: String, directory: File, token: String?, branch: String? = null): String =
        withContext(Dispatchers.IO) {
            val normalizedUrl = normalizeRemote(url)
            require(!directory.exists() || directory.list().isNullOrEmpty()) { "Destination is not empty." }
            directory.parentFile?.mkdirs()
            try {
                val command = Git.cloneRepository()
                    .setURI(normalizedUrl)
                    .setDirectory(directory)
                    .setCloneAllBranches(false)
                    .setTimeout(120)
                    .setCredentialsProvider(credentials(token))
                if (!branch.isNullOrBlank()) {
                    command.setBranch(branch)
                    command.setBranchesToClone(listOf("refs/heads/$branch"))
                }
                command.call().use { it.repository.branch }
            } catch (error: Throwable) {
                if (directory.exists()) directory.deleteRecursively()
                throw IllegalStateException(friendlyCloneError(error), error)
            }
        }

    suspend fun init(directory: File): String = withContext(Dispatchers.IO) {
        directory.mkdirs()
        Git.init().setDirectory(directory).call().use { it.repository.branch }
    }

    suspend fun status(directory: File): GitStatusSummary = withGit(directory) { git ->
        val status = git.status().call()
        GitStatusSummary(
            branch = git.repository.branch,
            added = status.added,
            changed = status.changed,
            modified = status.modified,
            removed = status.removed,
            missing = status.missing,
            untracked = status.untracked,
            conflicting = status.conflicting
        )
    }

    suspend fun addAll(directory: File) = withGit(directory) { git ->
        git.add().addFilepattern(".").call()
        git.add().setUpdate(true).addFilepattern(".").call()
        Unit
    }

    suspend fun commit(directory: File, message: String, name: String, email: String): String =
        withGit(directory) { git ->
            require(message.isNotBlank()) { "Commit message cannot be empty." }
            git.add().addFilepattern(".").call()
            git.add().setUpdate(true).addFilepattern(".").call()
            git.commit()
                .setMessage(message)
                .setAuthor(name.ifBlank { "Zeus User" }, email.ifBlank { "zeus@local" })
                .setCommitter(name.ifBlank { "Zeus User" }, email.ifBlank { "zeus@local" })
                .call().name
        }

    suspend fun push(directory: File, token: String?, force: Boolean = false): String = withGit(directory) { git ->
        val results = git.push()
            .setCredentialsProvider(credentials(token))
            .setForce(force)
            .setPushAll()
            .setTimeout(120)
            .call()
        results.flatMap { it.remoteUpdates }.joinToString("\n") { "${it.remoteName}: ${it.status}" }
    }

    suspend fun pull(directory: File, token: String?): String = withGit(directory) { git ->
        val result = git.pull().setCredentialsProvider(credentials(token)).setTimeout(120).call()
        result.mergeResult?.mergeStatus?.toString() ?: result.toString()
    }

    suspend fun fetch(directory: File, token: String?): String = withGit(directory) { git ->
        val result = git.fetch().setCredentialsProvider(credentials(token)).setRemoveDeletedRefs(true).setTimeout(120).call()
        result.messages.ifBlank { "Fetch complete" }
    }

    suspend fun branches(directory: File): List<String> = withGit(directory) { git ->
        git.branchList().setListMode(ListBranchCommand.ListMode.ALL).call()
            .map { it.name.removePrefix(Constants.R_HEADS).removePrefix(Constants.R_REMOTES) }
            .distinct()
            .sorted()
    }

    suspend fun currentBranch(directory: File): String = withGit(directory) { it.repository.branch }

    suspend fun checkout(directory: File, branch: String, create: Boolean = false, startPoint: String? = null): String =
        withGit(directory) { git ->
            val command = git.checkout().setName(branch).setCreateBranch(create)
            if (!startPoint.isNullOrBlank()) command.setStartPoint(startPoint)
            command.call().name
        }

    suspend fun deleteBranch(directory: File, branch: String, force: Boolean = false): List<String> =
        withGit(directory) { git -> git.branchDelete().setBranchNames(branch).setForce(force).call() }

    suspend fun merge(directory: File, branch: String): String = withGit(directory) { git ->
        val ref = git.repository.resolve(branch) ?: git.repository.resolve("refs/heads/$branch")
        ?: git.repository.resolve("refs/remotes/origin/$branch")
        ?: error("Branch '$branch' not found")
        git.merge().include(ref).call().mergeStatus.toString()
    }

    suspend fun hardReset(directory: File, target: String = "HEAD"): String = withGit(directory) { git ->
        git.reset().setMode(ResetCommand.ResetType.HARD).setRef(target).call().name
    }

    suspend fun revert(directory: File, commit: String): String = withGit(directory) { git ->
        val objectId = git.repository.resolve(commit) ?: error("Commit '$commit' not found")
        git.revert().include(objectId).call()?.name ?: error("Revert failed; resolve conflicts first.")
    }

    suspend fun stash(directory: File, message: String = "Zeus stash"): String = withGit(directory) { git ->
        git.stashCreate().setWorkingDirectoryMessage(message).call()?.name ?: "Nothing to stash"
    }

    suspend fun stashApply(directory: File, ref: String = "stash@{0}"): String = withGit(directory) { git ->
        git.stashApply().setStashRef(ref).call().name
    }

    suspend fun log(directory: File, maxCount: Int = 50): List<CommitInfo> = withGit(directory) { git ->
        git.log().setMaxCount(maxCount).call().map {
            CommitInfo(
                hash = it.name,
                shortHash = it.abbreviate(8).name(),
                message = it.shortMessage,
                author = it.authorIdent?.name ?: "Unknown",
                timestamp = it.commitTime * 1000L
            )
        }
    }

    suspend fun setRemote(directory: File, url: String) = withGit(directory) { git ->
        val config = git.repository.config
        config.setString("remote", "origin", "url", normalizeRemote(url))
        config.setString("remote", "origin", "fetch", "+refs/heads/*:refs/remotes/origin/*")
        config.save()
    }

    suspend fun remoteUrl(directory: File): String? = withGit(directory) { git ->
        git.repository.config.getString("remote", "origin", "url")
    }

    private suspend fun <T> withGit(directory: File, block: (Git) -> T): T = withContext(Dispatchers.IO) {
        require(File(directory, ".git").exists()) { "${directory.name} is not a Git repository." }
        try {
            Git.open(directory).use(block)
        } catch (error: Throwable) {
            throw IllegalStateException(friendlyGitError(error), error)
        }
    }

    private fun normalizeRemote(value: String): String {
        val trimmed = value.trim()
        require(trimmed.startsWith("https://")) { "Use an HTTPS Git repository URL." }
        val uri = runCatching { URI(trimmed) }.getOrNull() ?: error("Repository URL is invalid.")
        require(uri.host.equals("github.com", ignoreCase = true)) { "Only github.com repositories are supported." }
        val path = uri.path.orEmpty().trim('/')
        require(path.count { it == '/' } == 1) { "Use a repository URL such as https://github.com/owner/repo.git" }
        return "https://github.com/$path" + if (path.endsWith(".git")) "" else ".git"
    }

    private fun friendlyCloneError(error: Throwable): String {
        val root = rootCause(error)
        val message = root.message.orEmpty()
        
        // Detect obfuscated messages (e.g., "H2.a<init>[]" from R8/ProGuard)
        val isObfuscated = message.isNotEmpty() && (
            message.contains("<init>") ||
            message.matches(Regex("^[A-Z][a-z]?[0-9]*\\.[A-Za-z]")) ||
            (message.length < 20 && !message.any { it.isLetterOrDigit() || it == ' ' || it == '.' || it == ':' })
        )
        
        if (isObfuscated) {
            return "Git could not clone the repository. Check the URL, network connection, and GitHub access permissions."
        }
        
        return when {
            root is TransportException && message.contains("not authorized", true) -> "GitHub rejected the clone. Reconnect GitHub and confirm the app can access this repository."
            root is TransportException && message.contains("authentication", true) -> "GitHub authentication failed. Reconnect GitHub, then try again."
            message.contains("HTTP 404", true) || message.contains("404", true) -> "Repository not found. Check the URL and ensure you have access."
            message.contains("HTTP 403", true) || message.contains("403", true) -> "Access denied. Confirm your GitHub token has permission to access this repository."
            message.contains("HTTP 401", true) || message.contains("401", true) -> "Authentication required. Reconnect GitHub and try again."
            message.contains("not found", true) -> "Repository or branch not found, or the connected account cannot access it."
            message.contains("SSL", true) || message.contains("certificate", true) -> "A secure connection to GitHub could not be established. Check the device date, network, and certificate settings."
            message.contains("timeout", true) || message.contains("timed out", true) -> "The clone timed out. Check the network and try again."
            message.contains("No space", true) -> "The device does not have enough free storage for this repository."
            root is GitAPIException -> message.ifBlank { "Git could not clone the repository." }
            else -> message.ifBlank { "Git could not clone the repository." }
        }
    }

    private fun friendlyGitError(error: Throwable): String {
        val root = rootCause(error)
        return root.message?.takeIf { it.isNotBlank() } ?: "Git operation failed."
    }

    private fun rootCause(error: Throwable): Throwable {
        var current = error
        while (current.cause != null && current.cause !== current) current = current.cause!!
        return current
    }
}