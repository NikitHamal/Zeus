
package com.zeus.ui.screens.editor

import androidx.lifecycle.ViewModel
import com.zeus.util.FileUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import javax.inject.Inject

data class EditorState(val fileName: String = "", val content: String = "", val saved: Boolean = false, val path: String = "")

@HiltViewModel
class EditorViewModel @Inject constructor() : ViewModel() {
    private val _state = MutableStateFlow(EditorState())
    val state = _state.asStateFlow()

    private var file: File? = null

    fun load(path: String) {
        file = File(path)
        val content = runCatching { FileUtil.readFile(file!!) }.getOrDefault("")
        _state.value = EditorState(fileName = file!!.name, content = content, path = path)
    }

    fun updateContent(newContent: String) { _state.value = _state.value.copy(content = newContent, saved = false) }

    fun save() {
        file?.let { FileUtil.writeFile(it, _state.value.content); _state.value = _state.value.copy(saved = true) }
    }
}
