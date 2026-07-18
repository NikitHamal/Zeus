package com.zeus.code.data

import android.util.Log
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
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.Base64

class GitService {

    private fun credentials(token: String?) = token?.takeIf { it.isNotBlank() }
        ?.let { UsernamePasswordCredentialsProvider("x-access-token", it) }

    suspend fun clone(url: String, directory: File, token: String?, branch: String? = null): String =
        withContext(Dispatchers.IO) {
            val normalizedUrl = try {
                normalizeRemote(url)
            } catch (invalid: IllegalArgumentException) {
                throw IllegalStateException(invalid.message ?: "That repository URL is invalid.", invalid)
            }
            require(!directory.exists() || directory.list().isNullOrEmpty()) {
                "A workspace with this name already exists. Choose a different workspace name."
            }
            directory.parentFile?.mkdirs()
            try {
                val command = Git.cloneRepository()
                    .setURI(normalizedUrl)
                    .setDirectory(directory)
                    .setCloneAllBranches(false)
                    .setTimeout(180)
                    .setCredentialsProvider(credentials(token))
                if (!branch.isNullOrBlank()) {
                    command.setBranch(branch)
                    command.setBranchesToClone(listOf("refs/heads/$branch"))
                }
                command.call().use { it.repository.branch ?: Constants.MASTER }
            } catch (error: Throwable) {
                runCatching { if (directory.exists()) directory.deleteRecursively() }
                // Always log the full stack trace so logcat carries the raw truth.
                Log.e(TAG, "Git clone failed for $normalizedUrl (branch=$branch)", error)
                throw IllegalStateException(diagnoseClone(normalizedUrl, branch, token, error), error)
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
            .setTimeout(180)
            .call()
        results.flatMap { it.remoteUpdates }.joinToString("\n") { "${it.remoteName}: ${it.status}" }
    }

    suspend fun pull(directory: File, token: String?): String = withGit(directory) { git ->
        val result = git.pull().setCredentialsProvider(credentials(token)).setTimeout(180).call()
        result.mergeResult?.mergeStatus?.toString() ?: result.toString()
    }

    suspend fun fetch(directory: File, token: String?): String = withGit(directory) { git ->
        val result = git.fetch().setCredentialsProvider(credentials(token)).setRemoveDeletedRefs(true).setTimeout(180).call()
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
            Log.e(TAG, "Git operation failed in ${directory.name}", error)
            throw IllegalStateException(friendlyGitError(error), error)
        }
    }

    // ------------------------------------------------------------------
    // Clone URL normalisation
    //
    // Accepts every common way a GitHub URL shows up on a phone:
    //   https://github.com/owner/repo(.git)(/)
    //   github.com/owner/repo(.git)
    //   git@github.com:owner/repo(.git)
    //   URLs with user info, uppercase hosts, extra whitespace
    // ------------------------------------------------------------------
    fun normalizeRemote(value: String): String {
        var v = value.trim()
        require(v.isNotBlank()) { "Enter a GitHub repository URL first." }

        Regex("^git@github\\.com:(.+)$", RegexOption.IGNORE_CASE).matchEntire(v)?.let { match ->
            v = "https://github.com/${match.groupValues[1]}"
        }
        if (v.startsWith("github.com/", ignoreCase = true) || v.startsWith("www.github.com/", ignoreCase = true)) {
            v = "https://$v"
        }
        v = v.trimEnd('/')
        require(v.startsWith("https://", ignoreCase = true)) {
            "Use an HTTPS GitHub URL such as https://github.com/owner/repo"
        }
        val uri = runCatching { URI(v) }.getOrNull()
            ?: throw IllegalArgumentException("That repository URL could not be parsed.")
        val host = uri.host ?: throw IllegalArgumentException("That repository URL could not be parsed.")
        require(host.equals("github.com", ignoreCase = true) || host.equals("www.github.com", ignoreCase = true)) {
            "Only github.com repositories are supported."
        }
        var path = uri.path.orEmpty().trim('/')
        if (path.endsWith(".git", ignoreCase = true)) path = path.dropLast(4)
        require(path.matches(Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+"))) {
            "Use a repository URL such as https://github.com/owner/repo"
        }
        return "https://github.com/$path.git"
    }

    // ------------------------------------------------------------------
    // Clone failure diagnosis
    //
    // Two stages:
    //  1) statically classify the exception chain (authoritative when it
    //     matches — transport/auth/IO/SSL/validation problems)
    //  2) when the chain is opaque (the old "Git could not clone the
    //     repository" dead end), perform ONE lightweight HTTP probe against
    //     github.com itself to establish where reality diverged: network,
    //     TLS, DNS, auth, 404... and return a precise answer.
    // Either way the technical root cause (type + message) is appended so a
    // screenshot of the toast is enough to debug — no logcat required.
    // ------------------------------------------------------------------
    private fun diagnoseClone(url: String, branch: String?, token: String?, error: Throwable): String {
        val detail = technicalDetail(error)

        classify(error)?.let { reason -> return "$reason\n\n$detail" }

        // Chain was opaque; ask GitHub directly what is going on.
        return when (probeRemote(url, token)) {
            is Probe.Ok ->
                "GitHub is reachable and reports this repository exists, but the local Git engine failed mid-clone. " +
                    (branch?.let { "Branch '$it' may not exist. " } ?: "") +
                    "Free up storage, retry, and send the error below to the developer if it repeats.\n\n$detail"
            is Probe.NotFound ->
                "Repository not found (or private and inaccessible with the current sign-in).\n\n$detail"
            is Probe.Unauthorized ->
                if (token.isNullOrBlank()) {
                    "This repository requires a GitHub sign-in. Sign in to GitHub from Settings, then clone again.\n\n$detail"
                } else {
                    "GitHub rejected the saved sign-in. Reconnect GitHub from Settings, then try again.\n\n$detail"
                }
            is Probe.Forbidden ->
                "GitHub refused access to this repository (403). It may be private, SSO-protected, or rate-limited.\n\n$detail"
            is Probe.NoNetwork ->
                "No network path to github.com: ${probe.reason}. Check the connection and try again.\n\n$detail"
            is Probe.Tls ->
                "A secure connection to GitHub could not be established. Check the device date/time and network (VPN/proxy), then retry.\n\n$detail"
        }
    }

    /** Returns a user-facing string when the error chain is classifiable, else null. */
    private fun classify(error: Throwable): String? {
        var current: Throwable? = error
        while (current != null) {
            when (current) {
                is TransportException -> {
                    val msg = current.message.orEmpty().lowercase()
                    return when {
                        "not authorized" in msg || "auth" in msg || "401" in msg ->
                            "GitHub rejected the sign-in for this clone. Reconnect GitHub, then try again."
                        "403" in msg || "denied" in msg ->
                            "Access denied. Confirm your GitHub account can access this repository."
                        "404" in msg || "not found" in msg ->
                            "Repository or branch not found, or the connected account cannot access it."
                        else ->
                            "GitHub rejected the clone. Reconnect GitHub and confirm the app can access this repository."
                    }
                }
                is GitAPIException -> {
                    val msg = current.message.orEmpty().lowercase()
                    if (msg.isNotBlank()) {
                        return when {
                            "not found" in msg || "404" in msg -> "Repository or branch not found, or the connected account cannot access it."
                            "denied" in msg || "403" in msg -> "Access denied. Confirm your GitHub token can access this repository."
                            "unauthorized" in msg || "401" in msg -> "Authentication required. Reconnect GitHub and try again."
                            else -> "Git operation failed: ${current.message}"
                        }
                    }
                }
                is java.net.SocketTimeoutException -> return "The clone timed out. Check the network and try again."
                is java.net.ConnectException -> return "Network error during clone. Check your internet connection."
                is java.net.UnknownHostException -> return "Could not resolve github.com. Check your DNS and internet connection."
                is java.net.SocketException -> return "Network socket error during clone. Check your connection and retry."
                is javax.net.ssl.SSLHandshakeException -> return "A secure connection to GitHub could not be established. Check the device date, network, and certificate settings."
                is java.security.cert.CertificateException -> return "A secure connection to GitHub could not be established. Check the device date, network, and certificate settings."
                is java.io.FileNotFoundException -> return "Repository or branch not found, or the connected account cannot access it."
                is java.nio.file.NoSuchFileException -> return "Repository or branch not found, or the connected account cannot access it."
                is java.io.IOException -> {
                    val msg = current.message.orEmpty().lowercase()
                    return when {
                        "not authorized" in msg || "401" in msg || "auth" in msg ->
                            "GitHub rejected the sign-in for this clone. Reconnect GitHub, then try again."
                        "403" in msg || "denied" in msg ->
                            "Access denied. Confirm your GitHub account can access this repository."
                        "404" in msg || "not found" in msg ->
                            "Repository or branch not found, or the connected account cannot access it."
                        "reset" in msg || "timed out" in msg || "closed" in msg ->
                            "The network connection dropped during clone. Check your connection and retry."
                        else -> "Storage or network I/O failed during clone: ${current.message ?: "unknown I/O error"}"
                    }
                }
                is IllegalArgumentException -> return current.message ?: "Invalid repository URL or parameters."
            }
            current = current.cause
        }
        return null
    }

    /** Low-level probe of the exact HTTP endpoint JGit clones through. */
    private fun probeRemote(url: String, token: String?): Probe {
        val endpoint = "$url/info/refs?service=git-upload-pack"
        return try {
            val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "Zeus-Android")
                if (!token.isNullOrBlank()) {
                    val basic = Base64.getEncoder()
                        .encodeToString("x-access-token:$token".toByteArray(Charsets.UTF_8))
                    setRequestProperty("Authorization", "Basic $basic")
                }
            }
            try {
                when (connection.responseCode) {
                    in 200..399 -> Probe.Ok
                    401 -> Probe.Unauthorized
                    403 -> Probe.Forbidden
                    404 -> Probe.NotFound
                    else -> Probe.Ok // reachable but unusual status; treat as server-side reachable
                }
            } finally {
                connection.disconnect()
            }
        } catch (e: java.net.UnknownHostException) {
            Probe.NoNetwork("github.com did not resolve (DNS)")
        } catch (e: java.net.SocketTimeoutException) {
            Probe.NoNetwork("connection timed out")
        } catch (e: java.net.ConnectException) {
            Probe.NoNetwork(e.message ?: "connection refused")
        } catch (e: javax.net.ssl.SSLException) {
            Probe.Tls
        } catch (e: Throwable) {
            Probe.NoNetwork(e.message ?: e.javaClass.simpleName)
        }
    }

    private sealed interface Probe {
        data object Ok : Probe
        data object NotFound : Probe
        data object Unauthorized : Probe
        data object Forbidden : Probe
        data class NoNetwork(val reason: String) : Probe
        data object Tls : Probe
    }

    /** Short, screenshot-friendly technical cause appended to every clone error. */
    private fun technicalDetail(error: Throwable): String {
        val chain = mutableListOf<String>()
        var current: Throwable? = error
        var depth = 0
        while (current != null && depth < 4) {
            val name = current.javaClass.simpleName.ifBlank { current.javaClass.name }
            val msg = current.message?.take(90).orEmpty()
            chain += if (msg.isBlank()) name else "$name: $msg"
            current = current.cause
            depth++
        }
        return "Cause › ${chain.joinToString(" ← ").ifBlank { "unknown" }}"
    }

    private fun friendlyGitError(error: Throwable): String {
        val root = rootCause(error)
        return root.message?.takeIf { it.isNotBlank() } ?: "Git operation failed (${root.javaClass.simpleName})."
    }

    private fun rootCause(error: Throwable): Throwable {
        var current = error
        while (current.cause != null && current.cause !== current) current = current.cause!!
        return current
    }

    private companion object {
        const val TAG = "Zeus/GitService"
    }
}
