
package com.zeus.ui.screens.repos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zeus.data.remote.GitHubApi
import com.zeus.data.remote.models.GitHubBranch
import com.zeus.data.remote.models.GitHubCommit
import com.zeus.data.remote.models.GitHubRepo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RepoDetailState(val repo: GitHubRepo? = null, val branches: List<GitHubBranch> = emptyList(), val commits: List<GitHubCommit> = emptyList(), val loading: Boolean = true)

@HiltViewModel
class RepoDetailViewModel @Inject constructor(private val api: GitHubApi) : ViewModel() {
    private val _state = MutableStateFlow(RepoDetailState())
    val state = _state.asStateFlow()
    fun load(fullName: String) = viewModelScope.launch {
        try {
            val repo = api.getRepo(fullName)
            val branches = api.listBranches(fullName)
            val commits = api.listCommits(fullName, repo.default_branch)
            _state.value = RepoDetailState(repo, branches, commits, false)
        } catch (e: Exception) { _state.value = _state.value.copy(loading = false) }
    }
    fun fork(fullName: String) = viewModelScope.launch { runCatching { api.forkRepo(fullName) } }
}
