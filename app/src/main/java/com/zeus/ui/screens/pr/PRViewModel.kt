
package com.zeus.ui.screens.pr

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zeus.data.remote.GitHubApi
import com.zeus.data.remote.models.PullRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PRState(val prs: List<PullRequest> = emptyList())

@HiltViewModel
class PRViewModel @Inject constructor(private val api: GitHubApi) : ViewModel() {
    private val _state = MutableStateFlow(PRState())
    val state = _state.asStateFlow()
    fun load(fullName: String) = viewModelScope.launch { _state.value = PRState(api.listPRs(fullName)) }
    fun merge(fullName: String, number: Int) = viewModelScope.launch { runCatching { api.mergePR(fullName, number) } }
}
