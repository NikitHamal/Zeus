package com.zeus.code.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import com.zeus.code.model.FileEntry
import com.zeus.code.model.Workspace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipFile

class WorkspaceManager(private val context: Context, private val gitService: GitService) {
    private val root = File(context.filesDir, "workspaces").apply { mkdirs() }

    suspend fun list(): List<Workspace> = withContext(Dispatchers.IO) {
        root.listFiles().orEmpty().filter { it.isDirectory }.map { directory ->
            val git = File(directory, ".git").exists()
            Workspace(
                name = directory.name,
                path = directory.absolutePath,
                gitRepository = git,
                currentBranch = if (git) runCatching { gitService.currentBranch(directory) }.getOrNull() else null,
                remoteUrl = if (git) runCatching { gitService.remoteUrl(directory) }.getOrNull() else null,
                modifiedAt = directory.lastModified()
            )
        }.sortedByDescending { it.modifiedAt }
    }

    fun destination(name: String): File {
        val safe = name.trim().replace(Regex("[^A-Za-z0-9._-]"), "-").trim('-')
        require(safe.isNotBlank()) { "Workspace name is invalid." }
        return File(root, safe)
    }

    suspend fun workspaceAt(directory: File): Workspace = withContext(Dispatchers.IO) {
        require(directory.canonicalPath.startsWith(root.canonicalPath + File.separator)) { "Invalid workspace path." }
        workspace(directory)
    }

    suspend fun importTree(uri: Uri, preferredName: String? = null): Workspace = withContext(Dispatchers.IO) {
        val source = DocumentFile.fromTreeUri(context, uri) ?: error("Unable to open selected folder.")
        val name = preferredName?.takeIf { it.isNotBlank() } ?: source.name ?: "workspace-${System.currentTimeMillis()}"
        val target = uniqueDestination(name)
        target.mkdirs()
        copyDocumentTree(source, target)
        workspace(target)
    }

    suspend fun exportTree(workspace: Workspace, uri: Uri) = withContext(Dispatchers.IO) {
        val target = DocumentFile.fromTreeUri(context, uri) ?: error("Unable to open export folder.")
        copyFileTreeToDocument(workspace.directory, target)
    }

    /** Copies picked documents into the workspace root. Same-named files are replaced. */
    suspend fun importFiles(workspace: Workspace, uris: List<Uri>): Int = withContext(Dispatchers.IO) {
        require(workspace.directory.canonicalPath.startsWith(root.canonicalPath)) { "Invalid workspace path." }
        var count = 0
        uris.take(200).forEach { uri ->
            val name = queryDisplayName(uri)?.sanitizeEntryName() ?: return@forEach
            context.contentResolver.openInputStream(uri)?.use { input ->
                val target = File(workspace.directory, name)
                target.outputStream().use { input.copyTo(it) }
                count++
            }
        }
        count
    }

    /**
     * Extracts [zipUri] into [targetDir].
     *  - ZIP-slip paths and `.git/` entries are rejected/skipped.
     *  - Existing files with the same path are replaced cleanly (truncate+write).
     *  - If every entry lives under one top-level folder (the typical AI-chat
     *    export), that wrapper folder is stripped so content lands at the root.
     */
    suspend fun importZipInto(targetDir: File, zipUri: Uri): Int = withContext(Dispatchers.IO) {
        val source = File(context.cacheDir, "imports/incoming-${System.currentTimeMillis()}.zip")
        try {
            source.parentFile?.mkdirs()
            context.contentResolver.openInputStream(zipUri)?.use { input ->
                source.outputStream().use { input.copyTo(it) }
            } ?: error("Unable to read the selected ZIP file.")
            require(source.length() <= 300L * 1024L * 1024L) { "ZIP is larger than 300 MB." }

            val rootCanonical = targetDir.canonicalFile
            var count = 0
            ZipFile(source).use { zip ->
                val entries = zip.entries().asSequence()
                    .filter { it.name.isNotBlank() && it.name != ".git" && !it.name.startsWith(".git/", ignoreCase = true) }
                    .toList()
                require(entries.isNotEmpty()) { "The ZIP file is empty." }
                // Detect a single common wrapper folder like "project-x/...".
                val names = entries.map { it.name.trimStart('/') }
                val wrapper = names.first().substringBefore('/', "").takeIf { prefix ->
                    prefix.isNotBlank() && names.all { it.startsWith("$prefix/") || it == prefix }
                }
                entries.forEach { entry ->
                    var relative = entry.name.trimStart('/')
                    if (wrapper != null) {
                        relative = if (relative == wrapper) "" else relative.removePrefix("$wrapper/")
                        if (relative.isBlank()) return@forEach
                    }
                    val target = File(rootCanonical, relative).canonicalFile
                    require(
                        target.path == rootCanonical.path ||
                            target.path.startsWith(rootCanonical.path + File.separator)
                    ) { "ZIP contains an unsafe path (${entry.name})." }
                    if (entry.isDirectory) {
                        target.mkdirs()
                    } else {
                        target.parentFile?.mkdirs()
                        zip.getInputStream(entry).use { input ->
                            target.outputStream().use { input.copyTo(it) }
                            count++
                        }
                    }
                }
            }
            count
        } finally {
            source.delete()
            source.parentFile?.let { dir -> if (dir.list().isNullOrEmpty()) dir.delete() }
        }
    }

    /** Creates a brand-new workspace whose content comes from a ZIP file. */
    suspend fun importZipAsWorkspace(zipUri: Uri, preferredName: String?): Workspace = withContext(Dispatchers.IO) {
        val fallback = queryDisplayName(zipUri)?.removeSuffix(".zip") ?: "workspace-${System.currentTimeMillis()}"
        val target = uniqueDestination(preferredName?.takeIf { it.isNotBlank() } ?: fallback)
        target.mkdirs()
        try {
            importZipInto(target, zipUri)
        } catch (error: Throwable) {
            target.deleteRecursively()
            throw error
        }
        workspace(target)
    }

    /** Creates a brand-new workspace from a set of picked documents. */
    suspend fun importFilesAsWorkspace(uris: List<Uri>, preferredName: String?): Workspace = withContext(Dispatchers.IO) {
        val target = uniqueDestination(preferredName?.takeIf { it.isNotBlank() } ?: "workspace-${System.currentTimeMillis()}")
        target.mkdirs()
        uris.take(200).forEach { uri ->
            val name = queryDisplayName(uri)?.sanitizeEntryName() ?: return@forEach
            context.contentResolver.openInputStream(uri)?.use { input ->
                File(target, name).outputStream().use { input.copyTo(it) }
            }
        }
        workspace(target)
    }

    private fun queryDisplayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')

    private fun String.sanitizeEntryName(): String =
        replace(Regex("[\\\\/:*?\"<>|]"), "-").trim().ifBlank { "file-${System.currentTimeMillis()}" }

    suspend fun create(name: String, initializeGit: Boolean): Workspace = withContext(Dispatchers.IO) {
        val target = uniqueDestination(name)
        target.mkdirs()
        if (initializeGit) gitService.init(target)
        workspace(target)
    }

    suspend fun delete(workspace: Workspace) = withContext(Dispatchers.IO) {
        require(workspace.directory.canonicalPath.startsWith(root.canonicalPath)) { "Invalid workspace path." }
        workspace.directory.deleteRecursively()
    }

    suspend fun files(workspace: Workspace, maxDepth: Int = 5): List<FileEntry> = withContext(Dispatchers.IO) {
        val output = mutableListOf<FileEntry>()
        fun visit(file: File, depth: Int) {
            if (depth > maxDepth || file.name == ".git") return
            if (file != workspace.directory) {
                output += FileEntry(file.name, file.absolutePath, file.isDirectory, file.length(), depth)
            }
            if (file.isDirectory) file.listFiles().orEmpty()
                .sortedWith(compareBy<File>({ !it.isDirectory }, { it.name.lowercase() }))
                .forEach { visit(it, depth + 1) }
        }
        visit(workspace.directory, -1)
        output
    }

    suspend fun read(file: File): String = withContext(Dispatchers.IO) {
        require(file.exists() && file.isFile) { "File does not exist." }
        require(file.length() <= 2_000_000L) { "Files larger than 2 MB are not opened in the editor." }
        file.readText()
    }

    suspend fun write(file: File, content: String) = withContext(Dispatchers.IO) {
        file.parentFile?.mkdirs()
        file.writeText(content)
    }

    suspend fun createFile(workspace: Workspace, relativePath: String, directory: Boolean): File =
        withContext(Dispatchers.IO) {
            val target = safeChild(workspace.directory, relativePath)
            if (directory) target.mkdirs() else {
                target.parentFile?.mkdirs()
                if (!target.exists()) target.createNewFile()
            }
            target
        }

    suspend fun deleteFile(workspace: Workspace, file: File) = withContext(Dispatchers.IO) {
        require(file.canonicalPath.startsWith(workspace.directory.canonicalPath)) { "Invalid path." }
        if (file.isDirectory) file.deleteRecursively() else file.delete()
    }

    private fun uniqueDestination(name: String): File {
        val base = destination(name)
        if (!base.exists()) return base
        var index = 2
        while (File(root, "${base.name}-$index").exists()) index++
        return File(root, "${base.name}-$index")
    }

    private fun workspace(directory: File): Workspace {
        val git = File(directory, ".git").exists()
        return Workspace(directory.name, directory.absolutePath, git, modifiedAt = directory.lastModified())
    }

    private fun safeChild(root: File, relativePath: String): File {
        val child = File(root, relativePath).canonicalFile
        require(child.path.startsWith(root.canonicalPath + File.separator)) { "Path leaves the workspace." }
        return child
    }

    private fun copyDocumentTree(source: DocumentFile, target: File) {
        source.listFiles().forEach { child ->
            val name = child.name ?: return@forEach
            val out = File(target, name)
            if (child.isDirectory) {
                out.mkdirs()
                copyDocumentTree(child, out)
            } else {
                context.contentResolver.openInputStream(child.uri)?.use { input ->
                    out.parentFile?.mkdirs()
                    out.outputStream().use(input::copyTo)
                }
            }
        }
    }

    private fun copyFileTreeToDocument(source: File, target: DocumentFile) {
        source.listFiles().orEmpty().filter { it.name != ".git" }.forEach { child ->
            if (child.isDirectory) {
                val directory = target.findFile(child.name) ?: target.createDirectory(child.name)
                if (directory != null) copyFileTreeToDocument(child, directory)
            } else {
                target.findFile(child.name)?.delete()
                val document = target.createFile("application/octet-stream", child.name)
                if (document != null) context.contentResolver.openOutputStream(document.uri)?.use { output ->
                    child.inputStream().use { it.copyTo(output) }
                }
            }
        }
    }
}
