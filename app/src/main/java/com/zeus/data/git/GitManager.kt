
package com.zeus.data.git

import android.content.Context
import com.zeus.data.git.models.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.PersonIdent
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GitManager @Inject constructor(@ApplicationContext private val ctx: Context) {

    private fun credentials(token: String) = UsernamePasswordCredentialsProvider("git", token)

    suspend fun cloneRepo(remoteUrl: String, localDir: File, token: String, progress: (String)->Unit = {}): Result<File> = withContext(Dispatchers.IO) {
        try {
            if (localDir.exists()) localDir.deleteRecursively()
            progress("Cloning ${'$'}remoteUrl ...")
            val git = Git.cloneRepository()
                .setURI(remoteUrl)
                .setDirectory(localDir)
                .setCredentialsProvider(credentials(token))
                .call()
            git.close()
            Result.success(localDir)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun initRepo(dir: File): Result<File> = withContext(Dispatchers.IO) {
        try {
            Git.init().setDirectory(dir).call().close()
            Result.success(dir)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun status(dir: File): Result<GitStatus> = withContext(Dispatchers.IO) {
        try {
            val repo = FileRepositoryBuilder().setGitDir(File(dir, ".git")).readEnvironment().findGitDir().build()
            val git = Git(repo)
            val s = git.status().call()
            git.close()
            Result.success(GitStatus(
                untracked = s.untracked.toList(),
                modified = s.modified.toList(),
                added = s.added.toList(),
                removed = s.removed.toList(),
                changed = s.changed.toList(),
                conflicting = s.conflicting.toList(),
                isClean = s.isClean
            ))
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun addAll(dir: File): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val git = Git.open(File(dir, ".git").let { if (it.isDirectory) it.parentFile else dir })
            // Actually open dir
            val realGit = Git.open(dir)
            realGit.add().addFilepattern(".").call()
            realGit.close()
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun addFiles(dir: File, files: List<String>): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val git = Git.open(dir)
            files.forEach { git.add().addFilepattern(it).call() }
            git.close()
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun commit(dir: File, message: String, name: String, email: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val git = Git.open(dir)
            val commit = git.commit().setMessage(message).setAuthor(PersonIdent(name, email)).setCommitter(PersonIdent(name, email)).call()
            git.close()
            Result.success(commit.name)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun push(dir: File, token: String, force: Boolean = false): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val git = Git.open(dir)
            val push = git.push().setCredentialsProvider(credentials(token))
            if (force) push.setForce(true)
            push.call()
            git.close()
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun pull(dir: File, token: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val git = Git.open(dir)
            git.pull().setCredentialsProvider(credentials(token)).call()
            git.close()
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun fetch(dir: File, token: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val git = Git.open(dir)
            git.fetch().setCredentialsProvider(credentials(token)).call()
            git.close()
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun createBranch(dir: File, name: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val git = Git.open(dir)
            git.branchCreate().setName(name).call()
            git.close()
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun checkout(dir: File, branch: String, create: Boolean = false): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val git = Git.open(dir)
            git.checkout().setName(branch).setCreateBranch(create).call()
            git.close()
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun listLocalBranches(dir: File): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val git = Git.open(dir)
            val branches = git.branchList().call().map { it.name.removePrefix("refs/heads/") }
            git.close()
            Result.success(branches)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun getCurrentBranch(dir: File): String = withContext(Dispatchers.IO) {
        try {
            val git = Git.open(dir)
            val branch = git.repository.branch
            git.close()
            branch
        } catch (e: Exception) { "main" }
    }

    suspend fun log(dir: File, max: Int = 100): Result<List<GitCommitLocal>> = withContext(Dispatchers.IO) {
        try {
            val git = Git.open(dir)
            val logs = git.log().setMaxCount(max).call()
            val list = logs.map {
                GitCommitLocal(
                    hash = it.name,
                    shortHash = it.name.take(7),
                    message = it.shortMessage,
                    fullMessage = it.fullMessage,
                    author = it.authorIdent.name,
                    email = it.authorIdent.emailAddress,
                    time = it.commitTime.toLong()*1000
                )
            }
            git.close()
            Result.success(list)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun resetHard(dir: File, commitHash: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val git = Git.open(dir)
            git.reset().setMode(org.eclipse.jgit.api.ResetCommand.ResetType.HARD).setRef(commitHash).call()
            git.close()
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun revert(dir: File, commitHash: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val git = Git.open(dir)
            val repo = git.repository
            val walk = RevWalk(repo)
            val commit = walk.parseCommit(repo.resolve(commitHash))
            git.revert().include(commit).call()
            git.close()
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun diff(dir: File, filePath: String? = null): Result<String> = withContext(Dispatchers.IO) {
        try {
            val git = Git.open(dir)
            val out = java.io.ByteArrayOutputStream()
            if (filePath != null) {
                git.diff().setPathFilter(org.eclipse.jgit.treewalk.filter.PathFilter.create(filePath)).setOutputStream(out).call()
            } else {
                git.diff().setOutputStream(out).call()
            }
            git.close()
            Result.success(out.toString())
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun remoteUrl(dir: File): String? = withContext(Dispatchers.IO) {
        try {
            val git = Git.open(dir)
            val url = git.repository.config.getString("remote", "origin", "url")
            git.close()
            url
        } catch (e: Exception) { null }
    }
}
