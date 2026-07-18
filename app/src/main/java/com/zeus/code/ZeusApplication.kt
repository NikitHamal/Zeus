package com.zeus.code

import android.app.Application
import android.util.Log
import com.zeus.code.data.AndroidJGit

class ZeusApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        runCatching { AndroidJGit.install(this) }
            .onFailure { Log.w("Zeus", "JGit environment bootstrap failed; falling back to JGit defaults", it) }
    }
}
