
package com.zeus.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zeus.data.auth.AuthManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginState(val loading: Boolean = false, val isLoggedIn: Boolean = false, val error: String? = null)

@HiltViewModel
class LoginViewModel @Inject constructor(private val authManager: AuthManager) : ViewModel() {
    private val _state = MutableStateFlow(LoginState())
    val state = _state.asStateFlow()

    init { viewModelScope.launch { _state.value = _state.value.copy(isLoggedIn = authManager.isLoggedIn()) } }

    fun openGitHubAuth() {
        try { authManager.openAuthPage() } catch (e: Exception) { _state.value = _state.value.copy(error = e.message) }
    }

    fun exchangeCode(code: String) {
        if (code.isBlank()) return
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            val result = authManager.exchangeCodeForToken(code)
            if (result.isSuccess) _state.value = LoginState(isLoggedIn = true) else _state.value = LoginState(error = result.exceptionOrNull()?.message)
        }
    }
}
