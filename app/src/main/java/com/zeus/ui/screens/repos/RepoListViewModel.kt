
package com.zeus.ui.screens.repos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zeus.data.git.RepoManager
import com.zeus.data.git.models.LocalRepo
import com.zeus.data.remote.GitHubApi
import com.zeus.data.remote.models.GitHubRepo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RepoListState(val local: List<LocalRepo> = emptyList(), val remote: List<GitHubRepo> = emptyList())

@HiltViewModel
class RepoListViewModel @Inject constructor(private val repoManager: RepoManager, private val api: GitHubApi) : ViewModel() {
    private val _state = MutableStateFlow(RepoListState())
    val state = _state.asStateFlow()
    init { viewModelScope.launch {
        val l = repoManager.getLocalRepos()
        val r = runCatching { api.listRepos() }.getOrDefault(emptyList())
        _state.value = RepoListState(l,r)
    }}
}
