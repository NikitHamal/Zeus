
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

data class DiffState(val diff: String = "Loading...")

@HiltViewModel
class DiffViewModel @Inject constructor(private val gitManager: GitManager) : ViewModel() {
    private val _state = MutableStateFlow(DiffState())
    val state = _state.asStateFlow()
    fun load(path: String, file: String) = viewModelScope.launch {
        val dir = findRoot(File(path))
        val diff = gitManager.diff(dir, file.ifBlank { null }).getOrDefault("No diff")
        _state.value = DiffState(diff)
    }
    private fun findRoot(f: File): File { var cur: File? = f; while(cur!=null){ if(File(cur,".git").exists()) return cur; cur=cur.parentFile }; return f }
}
