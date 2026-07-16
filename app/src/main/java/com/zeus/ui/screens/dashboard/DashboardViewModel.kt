
package com.zeus.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zeus.data.git.RepoManager
import com.zeus.data.git.models.LocalRepo
import com.zeus.data.remote.GitHubApi
import com.zeus.data.remote.models.GitHubRepo
import com.zeus.data.remote.models.GitHubUser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardState(val user: GitHubUser? = null, val localRepos: List<LocalRepo> = emptyList(), val remoteRepos: List<GitHubRepo> = emptyList(), val loading: Boolean = true)

@HiltViewModel
class DashboardViewModel @Inject constructor(private val api: GitHubApi, private val repoManager: RepoManager) : ViewModel() {
    private val _state = MutableStateFlow(DashboardState())
    val state = _state.asStateFlow()
    init { load() }
    private fun load() = viewModelScope.launch {
        try {
            val user = runCatching { api.getUser() }.getOrNull()
            val locals = repoManager.getLocalRepos()
            val remotes = runCatching { api.listRepos() }.getOrDefault(emptyList())
            _state.value = DashboardState(user, locals, remotes, false)
        } catch (e: Exception) { _state.value = _state.value.copy(loading = false) }
    }
}
