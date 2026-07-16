
package com.zeus.ui.screens.git

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zeus.data.git.GitManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class BranchState(val branches: List<String> = emptyList(), val current: String = "")

@HiltViewModel
class BranchViewModel @Inject constructor(private val gitManager: GitManager) : ViewModel() {
    private val _state = MutableStateFlow(BranchState())
    val state = _state.asStateFlow()
    private var dir: File? = null

    fun load(path: String) {
        dir = findRoot(File(path))
        viewModelScope.launch {
            val branches = gitManager.listLocalBranches(dir!!).getOrDefault(emptyList())
            val current = gitManager.getCurrentBranch(dir!!)
            _state.value = BranchState(branches, current)
        }
    }
    fun createBranch(name: String) = viewModelScope.launch {
        dir?.let { gitManager.createBranch(it, name); load(it.absolutePath) }
    }
    fun checkout(name: String) = viewModelScope.launch {
        dir?.let { gitManager.checkout(it, name); load(it.absolutePath) }
    }
    private fun findRoot(f: File): File {
        var cur: File? = if (f.isFile) f.parentFile else f
        while (cur != null) { if (File(cur, ".git").exists()) return cur; cur = cur.parentFile }
        return f
    }
}
