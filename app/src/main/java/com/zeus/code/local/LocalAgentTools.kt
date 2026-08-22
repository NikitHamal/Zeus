package com.zeus.code.local

import com.zeus.code.data.GitService
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Executes the agent's tools inside a strict workspace sandbox:
 *  - every path argument is canonicalised and must stay inside the workspace
 *  - shell commands run through Android's /system/bin/sh with the workspace
 *    as working directory (bounded output + timeout)
 *  - git operations go through JGit [GitService] because Android ships no git
 *    binary
 *
 * Every tool returns plain text ready to be fed back to the model.
 */
class LocalAgentTools(
    private val workspace: File,
    private val git: GitService = GitService()
) {

    companion object {
        const val LIST_FILES = "list_files"
        const val READ_FILE = "read_file"
        const val WRITE_FILE = "write_file"
        const val EDIT_FILE = "edit_file"
        const val DELETE_PATH = "delete_path"
        const val SEARCH_FILES = "search_files"
        const val RUN_COMMAND = "run_command"
        const val GIT_STATUS = "git_status"
        const val GIT_DIFF = "git_diff"
        const val GIT_COMMIT = "git_commit"
        const val FINISH = "finish"

        const val MAX_OUTPUT_CHARS = 12_000
        const val MAX_READ_CHARS = 48_000
        const val MAX_LIST_ENTRIES = 500
        const val MAX_SEARCH_HITS = 120
        const val COMMAND_TIMEOUT_MS = 60_000L

        /** Tool names the model may invoke. */
        val NAMES = listOf(
            LIST_FILES, READ_FILE, WRITE_FILE, EDIT_FILE, DELETE_PATH,
            SEARCH_FILES, RUN_COMMAND, GIT_STATUS, GIT_DIFF, GIT_COMMIT, FINISH
        )

        private val BINARY_EXTENSIONS = setOf(
            "png", "jpg", "jpeg", "gif", "webp", "bmp", "ico", "pdf", "zip", "jar",
            "apk", "so", "bin", "exe", "dll", "ttf", "otf", "mp3", "mp4", "ogg", "wav"
        )
    }

    // ------------------------------------------------------------------
    // Dispatch
    // ------------------------------------------------------------------

    suspend fun execute(name: String, argumentsJson: String): String {
        val args = runCatching { JSONObject(argumentsJson.ifBlank { "{}" }) }
            .getOrElse { JSONObject() }
        return try {
            when (name) {
                LIST_FILES -> listFiles(args)
                READ_FILE -> readFile(args)
                WRITE_FILE -> writeFile(args)
                EDIT_FILE -> editFile(args)
                DELETE_PATH -> deletePath(args)
                SEARCH_FILES -> searchFiles(args)
                RUN_COMMAND -> runCommand(args)
                GIT_STATUS -> gitStatus()
                GIT_DIFF -> gitDiff()
                GIT_COMMIT -> gitCommit(args)
                FINISH -> "Task marked as finished."
                else -> error("Unknown tool '$name'. Available: ${NAMES.joinToString()}")
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            val reason = error.message?.take(400) ?: error.javaClass.simpleName
            "ERROR: $reason"
        }
    }

    /** True when a tool mutates the workspace (used for change tracking). */
    fun isMutating(name: String): Boolean =
        name in setOf(WRITE_FILE, EDIT_FILE, DELETE_PATH, GIT_COMMIT)

    // ------------------------------------------------------------------
    // Path safety
    // ------------------------------------------------------------------

    /** org.json's optString returns the literal "null" for JSON null values. */
    private fun JSONObject.str(key: String): String =
        optString(key).takeUnless { it.isBlank() || it == "null" || it == "undefined" }.orEmpty()

    private fun resolve(path: String, mustExist: Boolean): File {
        val cleaned = path.trim().trimStart('/')
        require(cleaned.isNotBlank()) { "A non-empty 'path' is required." }
        require(!cleaned.contains("..")) { "Path may not contain '..'." }
        val target = File(workspace, cleaned).canonicalFile
        require(target.path == workspace.canonicalFile.path ||
            target.path.startsWith(workspace.canonicalFile.path + File.separator)
        ) { "Path escapes the workspace." }
        if (mustExist) require(target.exists()) { "'$path' does not exist." }
        return target
    }

    private fun relative(file: File): String =
        file.path.removePrefix(workspace.canonicalFile.path).trimStart('/').ifBlank { "." }

    private fun bounded(text: String, limit: Int = MAX_OUTPUT_CHARS): String =
        if (text.length <= limit) text else text.take(limit) + "\n…[output truncated at $limit characters]"

    // ------------------------------------------------------------------
    // File tools
    // ------------------------------------------------------------------

    private suspend fun listFiles(args: JSONObject): String {
        val rootArg = args.str("path").ifBlank { "." }
        val root = resolve(rootArg, mustExist = true)
        require(root.isDirectory) { "'$rootArg' is not a directory." }
        val collected = mutableListOf<String>()
        collectTree(root, depth = 0, maxDepth = args.optInt("depth", 4).coerceIn(1, 8), collected)
        if (collected.isEmpty()) return "(empty directory)"
        return bounded(collected.joinToString("\n"))
    }

    private fun collectTree(dir: File, depth: Int, maxDepth: Int, out: MutableList<String>) {
        if (out.size >= MAX_LIST_ENTRIES || depth > maxDepth) return
        val children = dir.listFiles()
            ?.sortedWith(compareBy<File>({ !it.isDirectory }, { it.name.lowercase() }))
            .orEmpty()
        for (child in children) {
            if (out.size >= MAX_LIST_ENTRIES) return
            if (child.name == ".git") continue
            out += relative(child) + if (child.isDirectory) "/" else ""
            if (child.isDirectory && depth < maxDepth) collectTree(child, depth + 1, maxDepth, out)
        }
    }

    private suspend fun readFile(args: JSONObject): String {
        val file = resolve(args.str("path"), mustExist = true)
        require(file.isFile) { "'${args.str("path")}' is not a file." }
        require(file.length() <= 2_000_000L) { "File exceeds the 2 MB read limit." }
        val lines = file.readLines()
        val start = args.optInt("startLine", 0).coerceIn(0, lines.size)
        val end = args.optInt("endLine", lines.size).coerceIn(start, lines.size)
        val numbered = lines.subList(start, end).mapIndexed { index, line -> "${start + index + 1}: $line" }
        if (numbered.isEmpty()) return "(empty file)"
        var output = bounded(numbered.joinToString("\n"), MAX_READ_CHARS)
        if (end < lines.size) output += "\n…[${lines.size - end} more lines]"
        return output
    }

    private suspend fun writeFile(args: JSONObject): String {
        val path = args.str("path")
        require(args.has("content")) { "'content' is required." }
        val content = args.str("content")
        val file = resolve(path, mustExist = false)
        require(!file.isDirectory) { "'$path' is a directory." }
        file.parentFile?.mkdirs()
        file.writeText(content)
        return "Wrote ${content.length} characters to '${relative(file)}'."
    }

    private suspend fun editFile(args: JSONObject): String {
        val file = resolve(args.str("path"), mustExist = true)
        require(file.isFile) { "'${args.str("path")}' is not a file." }
        val find = args.str("find")
        require(find.isNotEmpty()) { "'find' is required and must match exactly once." }
        val replace = args.str("replace")
        val content = file.readText()
        when (countOccurrences(content, find)) {
            0 -> error("'find' text was not found in ${relative(file)}. Read the file again and retry.")
            1 -> {}
            else -> error("'find' matched multiple times; provide a longer unique snippet.")
        }
        file.writeText(content.replace(find, replace))
        return "Edited '${relative(file)}'."
    }

    private fun countOccurrences(haystack: String, needle: String): Int {
        if (needle.isEmpty()) return 0
        var count = 0
        var index = haystack.indexOf(needle)
        while (index >= 0) {
            count++
            index = haystack.indexOf(needle, index + needle.length)
        }
        return count
    }

    private suspend fun deletePath(args: JSONObject): String {
        val target = resolve(args.str("path"), mustExist = true)
        check(relative(target) != ".") { "Refusing to delete the workspace root." }
        val deleted = if (target.isDirectory) target.deleteRecursively() else target.delete()
        check(deleted) { "Could not delete '${relative(target)}'." }
        return "Deleted '${relative(target)}'."
    }

    // ------------------------------------------------------------------
    // Search
    // ------------------------------------------------------------------

    private suspend fun searchFiles(args: JSONObject): String {
        val query = args.str("query")
        require(query.isNotBlank()) { "'query' is required." }
        val regex = if (args.optBoolean("regex", false)) {
            runCatching { Regex(query, RegexOption.IGNORE_CASE) }
                .getOrElse { error("Invalid regex: ${it.message}") }
        } else null
        val rootArg = args.str("path").ifBlank { "." }
        val root = resolve(rootArg, mustExist = true)

        val hits = mutableListOf<String>()
        scanForMatches(root, query, regex, hits)
        if (hits.isEmpty()) return "No matches for '$query'."
        return bounded(hits.joinToString("\n"))
    }

    private fun scanForMatches(dir: File, query: String, regex: Regex?, hits: MutableList<String>) {
        val children = dir.listFiles()?.sortedBy { it.name.lowercase() }.orEmpty()
        for (child in children) {
            if (hits.size >= MAX_SEARCH_HITS) return
            if (!child.isDirectory) {
                if (child.length() > 1_000_000L || child.extension.lowercase() in BINARY_EXTENSIONS) continue
                runCatching {
                    child.useLines { lines ->
                        lines.forEachIndexed { index, line ->
                            val matched = regex?.containsMatchIn(line) ?: line.contains(query, ignoreCase = true)
                            if (matched) hits += "${relative(child)}:${index + 1}: ${line.trim().take(240)}"
                        }
                    }
                }
            } else if (child.name != ".git") {
                scanForMatches(child, query, regex, hits)
            }
        }
    }

    // ------------------------------------------------------------------
    // Shell
    // ------------------------------------------------------------------

    private suspend fun runCommand(args: JSONObject): String {
        val command = args.str("command").trim()
        require(command.isNotBlank()) { "'command' is required." }
        require(!command.contains("sudo")) { "sudo is not available on Android." }
        val process = ProcessBuilder("/system/bin/sh", "-c", command)
            .directory(workspace)
            .redirectErrorStream(true)
            .apply {
                environment()["HOME"] = workspace.absolutePath
                environment()["PWD"] = workspace.absolutePath
                environment()["ZEUS_WORKSPACE"] = workspace.absolutePath
            }
            .start()
        val output = StringBuilder()
        process.inputStream.bufferedReader().useLines { lines ->
            for (line in lines) {
                if (output.length > MAX_OUTPUT_CHARS) break
                output.append(line).append('\n')
            }
        }
        val finished = process.waitFor(COMMAND_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        if (!finished) {
            process.destroyForcibly()
            return bounded(output.toString()) + "\nERROR: command timed out after ${COMMAND_TIMEOUT_MS / 1000}s."
        }
        val exit = process.exitValue()
        val body = bounded(output.toString().trimEnd())
        return if (exit == 0) body.ifBlank { "(no output)" } else "$body\n[exit $exit]"
    }

    // ------------------------------------------------------------------
    // Git via JGit
    // ------------------------------------------------------------------

    private suspend fun gitStatus(): String = try {
        git.status(workspace).pretty()
    } catch (error: Throwable) {
        "Not a git repository (${error.message?.take(160)}). Continue without version control."
    }

    private suspend fun gitDiff(): String {
        val status = try {
            git.status(workspace)
        } catch (error: Throwable) {
            return "Not a git repository — nothing to diff."
        }
        val files = (status.added + status.changed + status.modified + status.removed).distinct().take(30)
        if (files.isEmpty()) return "No uncommitted changes."
        val out = StringBuilder()
        for (relPath in files) {
            out.appendLine("=== $relPath ===")
            runCatching {
                val before = git.headFileContent(workspace, relPath)
                val current = File(workspace, relPath).takeIf { it.exists() }?.readText().orEmpty()
                out.appendLine(unifiedDiff(before.lines(), current.lines()))
            }.onFailure {
                out.appendLine("(binary or unreadable)")
            }
            if (out.length > MAX_OUTPUT_CHARS) {
                out.appendLine("…[diff truncated]")
                break
            }
        }
        return bounded(out.toString().trimEnd())
    }

    /**
     * Compact line-based diff: trims the common prefix/suffix then shows the
     * removed (-) and added (+) middle section. Cheap, deterministic and easy
     * enough for any model to reason about.
     */
    private fun unifiedDiff(before: List<String>, after: List<String>): String {
        val out = StringBuilder()
        var start = 0
        while (start < before.size && start < after.size && before[start] == after[start]) start++
        var endBefore = before.size
        var endAfter = after.size
        while (endBefore > start && endAfter > start && before[endBefore - 1] == after[endAfter - 1]) {
            endBefore--
            endAfter--
        }
        for (i in start until endBefore) out.appendLine("-${before[i]}")
        for (j in start until endAfter) out.appendLine("+${after[j]}")
        val text = out.toString().trimEnd()
        return text.ifBlank { "(no changes)" }
    }

    private suspend fun gitCommit(args: JSONObject): String {
        val message = args.str("message").trim()
        require(message.isNotBlank()) { "'message' is required." }
        return try {
            val hash = git.commit(workspace, message, "Zeus Local Agent", "agent@zeus.local")
            "Committed ${hash.take(8)}: $message"
        } catch (error: Throwable) {
            "Not a git repository — commit skipped. Continue without version control."
        }
    }

    // ------------------------------------------------------------------
    // Tool definitions advertised to OpenAI-compatible endpoints
    // ------------------------------------------------------------------

    fun definitions(): List<LocalToolDef> = listOf(
        def(LIST_FILES, "List files and folders. Use path=\".\" for the workspace root.", mapOf(
            "path" to "string", "depth" to "integer"
        )),
        def(READ_FILE, "Read a text file; output includes line numbers.", mapOf(
            "path" to "string", "startLine" to "integer", "endLine" to "integer"
        ), listOf("path")),
        def(WRITE_FILE, "Create or overwrite a file with the complete content.", mapOf(
            "path" to "string", "content" to "string"
        ), listOf("path", "content")),
        def(EDIT_FILE, "Replace one exact unique snippet inside a file.", mapOf(
            "path" to "string", "find" to "string", "replace" to "string"
        ), listOf("path", "find", "replace")),
        def(DELETE_PATH, "Delete a file or folder.", mapOf(
            "path" to "string"
        ), listOf("path")),
        def(SEARCH_FILES, "Search file contents across the workspace; rows look like path:line:text.", mapOf(
            "query" to "string", "path" to "string", "regex" to "boolean"
        ), listOf("query")),
        def(RUN_COMMAND, "Run a shell command (/system/bin/sh) inside the workspace. No sudo, no interactive input.", mapOf(
            "command" to "string"
        ), listOf("command")),
        def(GIT_STATUS, "Show the git working-tree status of this workspace."),
        def(GIT_DIFF, "Show a compact diff of uncommitted changes."),
        def(GIT_COMMIT, "Stage every change and create one local git commit.", mapOf(
            "message" to "string"
        ), listOf("message")),
        def(FINISH, "Call once the goal is fully reached.", mapOf(
            "summary" to "string"
        ))
    )

    private fun def(
        name: String,
        description: String,
        properties: Map<String, String> = emptyMap(),
        required: List<String> = emptyList()
    ): LocalToolDef {
        val props = JSONObject()
        properties.forEach { (key, type) -> props.put(key, JSONObject().put("type", type)) }
        val schema = JSONObject()
            .put("type", "object")
            .put("properties", props)
            .put("required", JSONArray(required))
        val element = Json.parseToJsonElement(schema.toString())
        return LocalToolDef(name, description, element.jsonObject)
    }
}
