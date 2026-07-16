
package com.zeus.di

import android.content.Context
import com.zeus.data.auth.AuthManager
import com.zeus.data.git.GitManager
import com.zeus.data.git.RepoManager
import com.zeus.data.local.PreferencesManager
import com.zeus.data.remote.GitHubApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideKtorClient(): HttpClient = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; isLenient = true })
        }
        install(Logging) { level = LogLevel.NONE }
    }

    @Provides
    @Singleton
    fun provideAuthManager(@ApplicationContext ctx: Context, client: HttpClient): AuthManager = AuthManager(ctx, client)

    @Provides
    @Singleton
    fun provideGitHubApi(client: HttpClient, auth: AuthManager): GitHubApi = GitHubApi(client, auth)

    @Provides
    @Singleton
    fun providePrefs(@ApplicationContext ctx: Context) = PreferencesManager(ctx)

    @Provides
    @Singleton
    fun provideGitManager(@ApplicationContext ctx: Context) = GitManager(ctx)

    @Provides
    @Singleton
    fun provideRepoManager(@ApplicationContext ctx: Context, gitManager: GitManager) = RepoManager(ctx, gitManager)
}
