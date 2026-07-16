
package com.zeus.ui.screens.git

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zeus.data.git.GitManager
import com.zeus.data.git.models.GitCommitLocal
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class HistoryState(val commits: List<GitCommitLocal> = emptyList())

@HiltViewModel
class HistoryViewModel @Inject constructor(private val gitManager: GitManager) : ViewModel() {
    private val _state = MutableStateFlow(HistoryState())
    val state = _state.asStateFlow()
    private var dir: File? = null

    fun load(path: String) {
        dir = findRoot(File(path))
        viewModelScope.launch { _state.value = HistoryState(gitManager.log(dir!!).getOrDefault(emptyList())) }
    }
    fun resetHard(hash: String) = viewModelScope.launch { dir?.let { gitManager.resetHard(it, hash) } }
    fun revert(hash: String) = viewModelScope.launch { dir?.let { gitManager.revert(it, hash) } }
    private fun findRoot(f: File): File { var cur: File? = f; while(cur!=null){ if(File(cur,".git").exists()) return cur; cur=cur.parentFile }; return f }
}
