
package com.zeus.ui.screens.git

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zeus.data.auth.AuthManager
import com.zeus.data.git.GitManager
import com.zeus.data.git.models.GitStatus
import com.zeus.data.local.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class CommitState(val status: GitStatus = GitStatus(), val staged: Set<String> = emptySet(), val message: String = "", val loading: Boolean = false, val error: String? = null)

@HiltViewModel
class CommitViewModel @Inject constructor(private val gitManager: GitManager, private val authManager: AuthManager, private val prefs: PreferencesManager) : ViewModel() {
    private val _state = MutableStateFlow(CommitState())
    val state = _state.asStateFlow()
    private var repoDir: File? = null

    fun load(path: String) {
        repoDir = findRoot(File(path))
        viewModelScope.launch {
            val status = gitManager.status(repoDir!!).getOrDefault(GitStatus())
            _state.value = _state.value.copy(status = status)
        }
    }
    fun toggleStage(file: String) {
        val s = _state.value.staged.toMutableSet()
        if (s.contains(file)) s.remove(file) else s.add(file)
        _state.value = _state.value.copy(staged = s)
    }
    fun setMessage(m: String) { _state.value = _state.value.copy(message = m) }
    fun commit(onDone: ()->Unit) {
        val dir = repoDir ?: return
        viewModelScope.launch {
            try {
                if (_state.value.staged.isNotEmpty()) gitManager.addFiles(dir, _state.value.staged.toList())
                else gitManager.addAll(dir)
                val name = prefs.authorName.first()
                val email = prefs.authorEmail.first()
                gitManager.commit(dir, _state.value.message, name, email).onSuccess { onDone() }.onFailure { _state.value = _state.value.copy(error = it.message) }
            } catch (e: Exception) { _state.value = _state.value.copy(error = e.message) }
        }
    }
    fun push(force: Boolean = false) = viewModelScope.launch {
        val dir = repoDir ?: return@launch
        val token = authManager.getToken() ?: return@launch
        gitManager.push(dir, token, force).onFailure { _state.value = _state.value.copy(error = it.message) }
    }
    private fun findRoot(f: File): File {
        var cur: File? = if (f.isFile) f.parentFile else f
        while (cur != null) { if (File(cur, ".git").exists()) return cur; cur = cur.parentFile }
        return f
    }
}
