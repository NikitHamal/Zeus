package com.zeus.code

import android.app.Application
import android.util.Log
import com.zeus.code.data.AndroidJGit
import com.zeus.code.local.LocalTaskStore

class ZeusApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        runCatching { AndroidJGit.install(this) }
            .onFailure { Log.w("Zeus", "JGit environment bootstrap failed; falling back to JGit defaults", it) }
        // Local Mode: load persisted tasks and mark crashed runs as failed.
        runCatching {
            LocalTaskStore.init(this)
            LocalTaskStore.recoverOrphans()
        }.onFailure { Log.w("Zeus", "Local task store bootstrap failed", it) }
    }
}
