
package com.zeus.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zeus.data.auth.AuthManager
import com.zeus.data.local.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsState(val authorName: String = "", val authorEmail: String = "", val tokenPreview: String = "")

@HiltViewModel
class SettingsViewModel @Inject constructor(private val authManager: AuthManager, private val prefs: PreferencesManager) : ViewModel() {
    private val _state = MutableStateFlow(SettingsState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val token = authManager.getToken() ?: ""
            val preview = if (token.length > 10) token.take(6) + "..." + token.takeLast(4) else "No token"
            val name = prefs.authorName.first()
            val email = prefs.authorEmail.first()
            _state.value = SettingsState(name, email, preview)
        }
    }
    fun setName(n: String) { _state.value = _state.value.copy(authorName = n) }
    fun setEmail(e: String) { _state.value = _state.value.copy(authorEmail = e) }
    fun saveAuthor() = viewModelScope.launch { prefs.setAuthor(_state.value.authorName, _state.value.authorEmail) }
    fun logout(onDone: ()->Unit) = viewModelScope.launch { authManager.logout(); onDone() }
}
