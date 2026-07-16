package com.zeus.data.repository

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor() {
    suspend fun exchangeCodeForToken(code: String) {
        // Ktor call to GitHub token endpoint or your backend
    }
    suspend fun getStoredToken(): String? = null
    suspend fun clearToken() {}
}

@Singleton
class GitHubRepository @Inject constructor() {
    suspend fun getUserRepos() = emptyList<com.zeus.data.model.GitHubRepo>()
    suspend fun searchRepos(query: String) = emptyList<com.zeus.data.model.GitHubRepo>()
    suspend fun getStarredRepos() = emptyList<com.zeus.data.model.GitHubRepo>()
}

@Singleton
class LocalRepoRepository @Inject constructor() {
    suspend fun getLocalRepos() = emptyList<com.zeus.data.model.LocalRepo>()
    suspend fun cloneRepo(url: String, dir: java.io.File) {}
    suspend fun importFromSAF(uri: android.net.Uri, dest: java.io.File) {}
}
