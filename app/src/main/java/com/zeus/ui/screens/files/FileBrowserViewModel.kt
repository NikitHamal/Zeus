
package com.zeus.ui.screens.files

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.zeus.data.git.GitManager
import com.zeus.util.FileUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import javax.inject.Inject

data class FileBrowserState(val currentDir: File? = null, val files: List<File> = emptyList(), val rootPath: String = "")

@HiltViewModel
class FileBrowserViewModel @Inject constructor(private val gitManager: GitManager) : ViewModel() {
    private val _state = MutableStateFlow(FileBrowserState())
    val state = _state.asStateFlow()
    var createFileDialog by mutableStateOf(false)
    var pendingFileName by mutableStateOf("")

    private var root: File? = null

    fun load(path: String) {
        val dir = File(path)
        val actualDir = if (dir.isFile) dir.parentFile else dir
        if (root == null) root = findGitRoot(actualDir)
        _state.value = FileBrowserState(actualDir, FileUtil.listFiles(actualDir).filter { !it.name.startsWith(".") }, root?.absolutePath ?: actualDir.absolutePath)
    }

    fun navigateUp(): Boolean {
        val current = _state.value.currentDir ?: return false
        val rootPath = root?.absolutePath ?: return false
        if (current.absolutePath == rootPath) return false
        current.parentFile?.let { load(it.absolutePath); return true }
        return false
    }

    fun createFile() {
        val dir = _state.value.currentDir ?: return
        if (pendingFileName.isBlank()) return
        val f = File(dir, pendingFileName)
        f.createNewFile()
        load(dir.absolutePath)
    }

    private fun findGitRoot(start: File): File {
        var cur: File? = start
        while (cur != null) {
            if (File(cur, ".git").exists()) return cur
            cur = cur.parentFile
        }
        return start
    }
}
