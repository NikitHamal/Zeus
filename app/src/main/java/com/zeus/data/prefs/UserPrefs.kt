
package com.zeus.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

val Context.dataStore by preferencesDataStore(name = "zeus_prefs")

@Singleton
class UserPrefs @Inject constructor(private val context: Context) {
    private val KEY_THEME_DARK = booleanPreferencesKey("theme_dark")
    private val KEY_AUTHOR_NAME = stringPreferencesKey("author_name")
    private val KEY_AUTHOR_EMAIL = stringPreferencesKey("author_email")
    private val KEY_GITHUB_TOKEN = stringPreferencesKey("github_token")
    private val KEY_DEFAULT_REPO_PATH = stringPreferencesKey("default_repo_path")

    val isDarkTheme: Flow<Boolean> = context.dataStore.data.map { it[KEY_THEME_DARK] ?: false }
    val authorName: Flow<String> = context.dataStore.data.map { it[KEY_AUTHOR_NAME] ?: "Zeus User" }
    val authorEmail: Flow<String> = context.dataStore.data.map { it[KEY_AUTHOR_EMAIL] ?: "user@zeus.app" }

    suspend fun setDarkTheme(v: Boolean) { context.dataStore.edit { it[KEY_THEME_DARK] = v } }
    suspend fun setAuthor(name: String, email: String) { context.dataStore.edit { it[KEY_AUTHOR_NAME]=name; it[KEY_AUTHOR_EMAIL]=email } }
    suspend fun clearToken() { context.dataStore.edit { it.remove(KEY_GITHUB_TOKEN) } }
}
