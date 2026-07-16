
package com.zeus.data.local

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.prefDataStore by preferencesDataStore(name = "zeus_prefs")

@Singleton
class PreferencesManager @Inject constructor(@ApplicationContext private val context: Context) {
    companion object {
        val KEY_DEFAULT_BRANCH = stringPreferencesKey("default_branch")
        val KEY_AUTHOR_NAME = stringPreferencesKey("author_name")
        val KEY_AUTHOR_EMAIL = stringPreferencesKey("author_email")
        val KEY_THEME = stringPreferencesKey("theme")
    }

    val authorName: Flow<String> = context.prefDataStore.data.map { it[KEY_AUTHOR_NAME] ?: "Zeus User" }
    val authorEmail: Flow<String> = context.prefDataStore.data.map { it[KEY_AUTHOR_EMAIL] ?: "zeus@local" }

    suspend fun setAuthor(name: String, email: String) {
        context.prefDataStore.edit {
            it[KEY_AUTHOR_NAME] = name
            it[KEY_AUTHOR_EMAIL] = email
        }
    }

    suspend fun setTheme(theme: String) {
        context.prefDataStore.edit { it[KEY_THEME] = theme }
    }
}
