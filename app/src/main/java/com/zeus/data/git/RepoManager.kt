
package com.zeus.data.git

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.zeus.data.git.models.LocalRepo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RepoManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gitManager: GitManager
) {
    private val reposBaseDir: File get() = File(context.filesDir, "zeus_repos").apply { mkdirs() }

    suspend fun getLocalRepos(): List<LocalRepo> = withContext(Dispatchers.IO) {
        if (!reposBaseDir.exists()) return@withContext emptyList()
        reposBaseDir.listFiles()?.filter { File(it, ".git").exists() }?.map {
            val branch = runCatching { gitManager.getCurrentBranch(it) }.getOrDefault("main")
            val remote = runCatching { gitManager.remoteUrl(it) }.getOrNull()
            LocalRepo(name = it.name, path = it.absolutePath, currentBranch = branch, remoteUrl = remote, lastModified = it.lastModified())
        }?.sortedByDescending { it.lastModified } ?: emptyList()
    }

    suspend fun deleteLocalRepo(path: String): Boolean = withContext(Dispatchers.IO) {
        File(path).deleteRecursively()
    }

    suspend fun importFromSAF(uri: Uri, displayName: String): Result<File> = withContext(Dispatchers.IO) {
        try {
            val dest = File(reposBaseDir, displayName)
            if (dest.exists()) dest.deleteRecursively()
            dest.mkdirs()
            copyUriRecursively(uri, dest)
            Result.success(dest)
        } catch (e: Exception) { Result.failure(e) }
    }

    private fun copyUriRecursively(treeUri: Uri, destDir: File) {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
        val resolver = context.contentResolver
        resolver.query(childrenUri, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE), null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val docId = cursor.getString(0)
                val name = cursor.getString(1)
                val mime = cursor.getString(2)
                val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                if (DocumentsContract.Document.MIME_TYPE_DIR == mime) {
                    val newDir = File(destDir, name)
                    newDir.mkdirs()
                    copyUriRecursively(docUri, newDir)
                } else {
                    val outFile = File(destDir, name)
                    resolver.openInputStream(docUri)?.use { input ->
                        outFile.outputStream().use { output -> input.copyTo(output) }
                    }
                }
            }
        }
    }

    fun getBaseDir(): File = reposBaseDir
}
