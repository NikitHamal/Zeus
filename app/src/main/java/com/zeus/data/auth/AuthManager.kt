
package com.zeus.data.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.zeus.git.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "zeus_auth")

@Singleton
class AuthManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: HttpClient
) {
    companion object {
        private val TOKEN_KEY = stringPreferencesKey("github_token")
        private val USER_KEY = stringPreferencesKey("github_user_json")
        const val DEFAULT_SCOPES = "repo,delete_repo,user,workflow,write:packages"
    }

    fun getClientId(): String {
        // BuildConfig field injected via gradle or env
        return try { BuildConfig.GITHUB_CLIENT_ID } catch (e: Exception) { System.getenv("GITHUB_CLIENT_ID") ?: "" }
    }
    fun getClientSecret(): String {
        return try { BuildConfig.GITHUB_CLIENT_SECRET } catch (e: Exception) { System.getenv("GITHUB_CLIENT_SECRET") ?: "" }
    }

    fun getAuthUrl(): String {
        val clientId = getClientId()
        val redirect = "zeus://oauth"
        return "https://github.com/login/oauth/authorize?client_id=$clientId&scope=$DEFAULT_SCOPES&redirect_uri=$redirect"
    }

    fun openAuthPage() {
        val url = getAuthUrl()
        val intent = CustomTabsIntent.Builder().build()
        intent.launchUrl(context, Uri.parse(url))
    }

    suspend fun exchangeCodeForToken(code: String): Result<String> {
        return try {
            val resp: TokenResponse = client.submitForm(
                url = "https://github.com/login/oauth/access_token",
                formParameters = Parameters.build {
                    append("client_id", getClientId())
                    append("client_secret", getClientSecret())
                    append("code", code)
                }
            ) {
                headers { append(HttpHeaders.Accept, "application/json") }
            }.body()
            if (resp.access_token.isNotBlank()) {
                saveToken(resp.access_token)
                Result.success(resp.access_token)
            } else {
                Result.failure(Exception("Empty token: ${'$'}{resp.error}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveToken(token: String) {
        context.dataStore.edit { it[TOKEN_KEY] = token }
    }

    suspend fun getToken(): String? {
        return context.dataStore.data.first()[TOKEN_KEY]
    }

    suspend fun clearToken() {
        context.dataStore.edit { it.remove(TOKEN_KEY) }
    }

    suspend fun isLoggedIn(): Boolean = !getToken().isNullOrBlank()

    suspend fun logout() = clearToken()

    @Serializable
    data class TokenResponse(
        val access_token: String = "",
        val token_type: String = "",
        val scope: String = "",
        val error: String = "",
        val error_description: String = ""
    )
}
