package com.zeus.code.data

import com.zeus.code.model.Workspace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class TerminalEngine(private val git: GitService) {
    suspend fun execute(workspace: Workspace, command: String, token: String?): String {
        val trimmed = command.trim()
        if (trimmed.isBlank()) return ""
        return if (trimmed == "git" || trimmed.startsWith("git ")) {
            executeGit(workspace.directory, trimmed.removePrefix("git").trim(), token)
        } else {
            executeShell(workspace.directory, trimmed)
        }
    }

    private suspend fun executeGit(directory: File, command: String, token: String?): String {
        val parts = tokenize(command)
        val verb = parts.firstOrNull() ?: return help()
        return when (verb) {
            "init" -> git.init(directory)
            "status" -> git.status(directory).pretty()
            "add" -> { git.addAll(directory); "Staged all changes." }
            "commit" -> {
                val message = optionValue(parts, "-m") ?: parts.drop(1).joinToString(" ").ifBlank { "Zeus commit" }
                val hash = git.commit(directory, message, "Zeus User", "zeus@local")
                "Committed ${hash.take(8)}"
            }
            "push" -> git.push(directory, token, parts.contains("--force") || parts.contains("-f"))
            "pull" -> git.pull(directory, token)
            "fetch" -> git.fetch(directory, token)
            "branch" -> when {
                parts.size == 1 -> git.branches(directory).joinToString("\n")
                parts.getOrNull(1) == "-d" || parts.getOrNull(1) == "-D" -> {
                    val branch = parts.getOrNull(2) ?: error("Usage: git branch -d <name>")
                    git.deleteBranch(directory, branch, parts[1] == "-D").joinToString()
                }
                else -> git.checkout(directory, parts[1], create = true)
            }
            "checkout", "switch" -> {
                val create = parts.contains("-b") || parts.contains("-c")
                val name = parts.lastOrNull()?.takeUnless { it.startsWith("-") }
                    ?: error("Branch name required")
                git.checkout(directory, name, create)
            }
            "merge" -> git.merge(directory, parts.getOrNull(1) ?: error("Branch required"))
            "reset" -> {
                val target = parts.lastOrNull()?.takeUnless { it.startsWith("-") } ?: "HEAD"
                git.hardReset(directory, target)
            }
            "revert" -> git.revert(directory, parts.getOrNull(1) ?: error("Commit required"))
            "stash" -> if (parts.getOrNull(1) == "apply" || parts.getOrNull(1) == "pop") {
                git.stashApply(directory)
            } else git.stash(directory, parts.drop(1).joinToString(" ").ifBlank { "Zeus stash" })
            "log" -> git.log(directory).joinToString("\n") { "${it.shortHash}  ${it.message}  — ${it.author}" }
            "remote" -> {
                if (parts.getOrNull(1) == "set-url" && parts.getOrNull(2) == "origin") {
                    git.setRemote(directory, parts.getOrNull(3) ?: error("URL required")); "Remote updated."
                } else git.remoteUrl(directory) ?: "No origin remote."
            }
            "help", "--help" -> help()
            else -> "Unsupported embedded git command: $verb\n\n${help()}"
        }
    }

    private suspend fun executeShell(directory: File, command: String): String = withContext(Dispatchers.IO) {
        val process = ProcessBuilder("/system/bin/sh", "-c", command)
            .directory(directory)
            .redirectErrorStream(true)
            .apply {
                environment()["HOME"] = directory.absolutePath
                environment()["PWD"] = directory.absolutePath
                environment()["ZEUS_WORKSPACE"] = directory.absolutePath
            }
            .start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        val code = process.waitFor()
        buildString {
            append(output.trimEnd())
            if (code != 0) {
                if (isNotEmpty()) appendLine()
                append("[exit $code]")
            }
        }
    }

    private fun optionValue(parts: List<String>, option: String): String? {
        val index = parts.indexOf(option)
        return if (index >= 0) parts.getOrNull(index + 1) else null
    }

    private fun tokenize(input: String): List<String> {
        val regex = Regex("""(?:[^\s\"']+|\"[^\"]*\"|'[^']*')+""")
        return regex.findAll(input).map { it.value.trim('"', '\'') }.toList()
    }

    private fun help() = """
        Zeus embedded Git commands:
          git init
          git status
          git add .
          git commit -m "message"
          git push [--force]
          git pull | git fetch
          git branch | git branch <new> | git branch -d <name>
          git checkout [-b] <branch>
          git merge <branch>
          git reset --hard [target]
          git revert <commit>
          git stash | git stash apply
          git log
          git remote | git remote set-url origin <url>

        Other commands run through Android's /system/bin/sh inside the selected workspace.
    """.trimIndent()
}
