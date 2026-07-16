
package com.zeus.ui.screens.terminal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zeus.data.auth.AuthManager
import com.zeus.data.git.GitManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class TerminalState(val logs: List<String> = listOf("Zeus Terminal v1.0 - type 'help'"), val currentDir: String = "")

@HiltViewModel
class TerminalViewModel @Inject constructor(private val gitManager: GitManager, private val authManager: AuthManager) : ViewModel() {
    private val _state = MutableStateFlow(TerminalState())
    val state = _state.asStateFlow()
    private var dir: File? = null

    fun init(path: String) {
        dir = findRoot(File(path))
        _state.value = _state.value.copy(currentDir = dir?.absolutePath ?: path)
    }

    fun execute(cmd: String) {
        if (cmd.isBlank()) return
        val currentLogs = _state.value.logs.toMutableList()
        currentLogs.add("$ $cmd")
        _state.value = _state.value.copy(logs = currentLogs)
        viewModelScope.launch {
            val result = runCommand(cmd.trim())
            val updated = _state.value.logs.toMutableList()
            updated.add(result)
            _state.value = _state.value.copy(logs = updated.takeLast(200))
        }
    }

    private suspend fun runCommand(cmd: String): String {
        val d = dir ?: return "No repo dir"
        val token = authManager.getToken() ?: "no-token"
        return try {
            when {
                cmd == "help" -> "Supported: git status, add ., commit -m msg, push, pull, fetch, branch, checkout <b>, log, diff, reset --hard <hash>, revert <hash>, remote -v, clear"
                cmd == "clear" -> { _state.value = TerminalState(listOf("Cleared"), _state.value.currentDir); return "cleared" }
                cmd.startsWith("git status") -> gitManager.status(d).getOrNull()?.toString() ?: "error"
                cmd.startsWith("git add") -> { gitManager.addAll(d); "added" }
                cmd.startsWith("git commit") -> {
                    val msg = cmd.substringAfter("-m").trim().removeSurrounding(""").removeSurrounding("'").ifBlank { "commit via zeus" }
                    val res = gitManager.commit(d, msg, "Zeus User", "zeus@local")
                    res.fold({ "committed $it" }, { "error: ${it.message}" })
                }
                cmd.startsWith("git push -f") || cmd.startsWith("git push --force") -> gitManager.push(d, token, true).fold({ "force pushed" }, { "error ${it.message}" })
                cmd.startsWith("git push") -> gitManager.push(d, token).fold({ "pushed" }, { "error ${it.message}" })
                cmd.startsWith("git pull") -> gitManager.pull(d, token).fold({ "pulled" }, { "error ${it.message}" })
                cmd.startsWith("git fetch") -> gitManager.fetch(d, token).fold({ "fetched" }, { "error ${it.message}" })
                cmd.startsWith("git branch") && !cmd.contains("checkout") -> gitManager.listLocalBranches(d).getOrDefault(emptyList()).joinToString("
")
                cmd.startsWith("git checkout") -> {
                    val branch = cmd.substringAfter("checkout").trim()
                    gitManager.checkout(d, branch, false).fold({ "checked out $branch" }, { "error ${it.message}" })
                }
                cmd.startsWith("git log") -> gitManager.log(d).getOrDefault(emptyList()).joinToString("
") { "${it.shortHash} ${it.message}" }
                cmd.startsWith("git diff") -> gitManager.diff(d).getOrDefault("no diff")
                cmd.startsWith("git reset --hard") -> {
                    val hash = cmd.substringAfter("--hard").trim()
                    gitManager.resetHard(d, hash).fold({ "reset to $hash" }, { "error ${it.message}" })
                }
                cmd.startsWith("git revert") -> {
                    val hash = cmd.substringAfter("revert").trim()
                    gitManager.revert(d, hash).fold({ "reverted $hash" }, { "error ${it.message}" })
                }
                cmd.startsWith("git remote") -> gitManager.remoteUrl(d) ?: "no remote"
                else -> "Unknown command: $cmd. Type help"
            }
        } catch (e: Exception) { "Exception: ${e.message}" }
    }

    private fun findRoot(f: File): File { var cur: File? = f; while(cur!=null){ if(File(cur,".git").exists()) return cur; cur=cur.parentFile }; return f }
}
