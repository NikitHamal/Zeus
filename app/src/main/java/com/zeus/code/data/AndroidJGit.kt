package com.zeus.code.data

import android.content.Context
import android.util.Log
import org.eclipse.jgit.lib.Config
import org.eclipse.jgit.storage.file.FileBasedConfig
import org.eclipse.jgit.util.FS
import org.eclipse.jgit.util.SystemReader
import java.io.File
import java.net.InetAddress
import java.util.TimeZone

/**
 * Installs an Android-safe [SystemReader] before JGit is first used.
 *
 * The stock SystemReader assumes a desktop environment: a real `user.home`,
 * readable `/etc/gitconfig`, a hostname that always resolves, and an always
 * available environment. On Android some of those assumptions fail deep
 * inside JGit with opaque, message-less exceptions (the "Git could not clone
 * the repository" dead end this app shipped before). Anchoring JGit's home to
 * app-private storage and making every lookup fail-safe removes that entire
 * class of failures.
 */
object AndroidJGit {
    private const val TAG = "Zeus/JGit"

    @Volatile
    private var installed = false

    @Synchronized
    fun install(context: Context) {
        if (installed) return
        SystemReader.setInstance(AndroidSystemReader(context.applicationContext))
        installed = true
        Log.i(TAG, "Android-safe JGit environment installed")
    }

    private class AndroidSystemReader(context: Context) : SystemReader() {
        private val appHome = File(context.filesDir, "jgit-home").apply { mkdirs() }
        private val userGitConfig = File(appHome, ".gitconfig")
        private val systemGitConfig = File(appHome, "gitconfig")
        private val jgitConfig = File(appHome, ".jgitconfig")

        @Volatile
        private var hostname: String? = null

        override fun getHostname(): String = hostname
            ?: runCatching { InetAddress.getLocalHost().hostName }
                .getOrDefault("localhost")
                .also { hostname = it }

        override fun getenv(variable: String?): String? =
            runCatching { System.getenv(variable) }.getOrNull()

        override fun getProperty(key: String?): String? = when (key) {
            "user.home" -> appHome.absolutePath
            else -> runCatching { System.getProperty(key) }.getOrNull()
        }

        override fun getCurrentTime(): Long = System.currentTimeMillis()

        override fun getTimezone(whenTime: Long): Int =
            TimeZone.getDefault().getOffset(whenTime) / 60_000

        override fun openSystemConfig(parent: Config?, fs: FS?): FileBasedConfig =
            FileBasedConfig(parent, systemGitConfig, fs ?: FS.DETECTED)

        override fun openUserConfig(parent: Config?, fs: FS?): FileBasedConfig =
            FileBasedConfig(parent, userGitConfig, fs ?: FS.DETECTED)

        override fun openJGitConfig(parent: Config?, fs: FS?): FileBasedConfig =
            FileBasedConfig(parent, jgitConfig, fs ?: FS.DETECTED)
    }
}
