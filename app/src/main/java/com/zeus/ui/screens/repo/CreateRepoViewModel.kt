
package com.zeus.ui.screens.repo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zeus.data.remote.GitHubApi
import com.zeus.data.remote.models.CreateRepoRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CreateRepoState(val name: String = "", val description: String = "", val isPrivate: Boolean = false, val loading: Boolean = false, val error: String? = null)

@HiltViewModel
class CreateRepoViewModel @Inject constructor(private val api: GitHubApi) : ViewModel() {
    private val _state = MutableStateFlow(CreateRepoState())
    val state = _state.asStateFlow()
    fun setName(n: String) { _state.value = _state.value.copy(name = n) }
    fun setDesc(d: String) { _state.value = _state.value.copy(description = d) }
    fun setPrivate(p: Boolean) { _state.value = _state.value.copy(isPrivate = p) }
    fun create(onDone: ()->Unit) = viewModelScope.launch {
        try {
            _state.value = _state.value.copy(loading = true)
            api.createRepo(CreateRepoRequest(_state.value.name, _state.value.description, _state.value.isPrivate))
            onDone()
        } catch (e: Exception) { _state.value = _state.value.copy(error = e.message, loading = false) }
    }
}
