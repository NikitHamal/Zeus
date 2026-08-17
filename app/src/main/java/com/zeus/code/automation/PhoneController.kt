package com.zeus.code.automation

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.MediaStore
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.KeyEvent
import android.view.WindowManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

data class PhoneTaskLog(
    val action: String,
    val details: String,
    val isSuccess: Boolean = true,
    val timestamp: Long = System.currentTimeMillis()
)

data class InstalledApp(
    val name: String,
    val packageName: String
)

class PhoneController(private val context: Context) {

    private val _logs = MutableStateFlow<List<PhoneTaskLog>>(emptyList())
    val logs: StateFlow<List<PhoneTaskLog>> = _logs.asStateFlow()

    private val _isServiceActive = MutableStateFlow(false)
    val isServiceActive: StateFlow<Boolean> = _isServiceActive.asStateFlow()

    private var cachedApps: List<InstalledApp>? = null

    // Common app aliases mapping to facilitate natural language commands
    private val appAliases = mapOf(
        "settings" to listOf("com.android.settings"),
        "chrome" to listOf("com.android.chrome", "com.google.android.apps.chrome"),
        "browser" to listOf("com.android.chrome", "com.google.android.browser", "org.mozilla.firefox"),
        "youtube" to listOf("com.google.android.youtube", "com.google.android.youtube.tv"),
        "tiktok" to listOf("com.zhiliaoapp.musically", "com.ss.android.ugc.aweme"),
        "douyin" to listOf("com.ss.android.ugc.aweme"),
        "maps" to listOf("com.google.android.apps.maps"),
        "gmail" to listOf("com.google.android.gm"),
        "camera" to listOf("com.android.camera", "com.google.android.GoogleCamera", "com.sec.android.app.camera"),
        "gallery" to listOf("com.android.gallery3d", "com.google.android.apps.photos", "com.sec.android.gallery3d"),
        "photos" to listOf("com.google.android.apps.photos", "com.sec.android.gallery3d"),
        "calculator" to listOf("com.google.android.calculator", "com.android.calculator2", "com.sec.android.app.popupcalculator"),
        "clock" to listOf("com.google.android.deskclock", "com.android.deskclock", "com.sec.android.app.clockpackage"),
        "files" to listOf("com.google.android.documentsui", "com.android.documentsui", "com.google.android.apps.nbu.files"),
        "play store" to listOf("com.android.vending"),
        "playstore" to listOf("com.android.vending"),
        "whatsapp" to listOf("com.whatsapp"),
        "telegram" to listOf("org.telegram.messenger", "org.thunderdog.challegram"),
        "spotify" to listOf("com.spotify.music"),
        "twitter" to listOf("com.twitter.android"),
        "x" to listOf("com.twitter.android"),
        "instagram" to listOf("com.instagram.android")
    )

    fun checkServiceState() {
        _isServiceActive.value = PhoneAutomationService.isConnected
    }

    fun addLog(action: String, details: String, isSuccess: Boolean = true) {
        val entry = PhoneTaskLog(action = action, details = details, isSuccess = isSuccess)
        _logs.value = (_logs.value + entry).takeLast(200)
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }

    fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    fun getDisplayMetrics(): DisplayMetrics {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        return metrics
    }

    // ==================== SELF-CONTAINED ACTIONS (OpenDroid style) ====================

    /**
     * Directly plays / searches on YouTube in 1 step without fragile multi-tap steps.
     */
    fun playYoutube(query: String): Boolean {
        return try {
            val encQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
            val uri = Uri.parse("https://www.youtube.com/results?search_query=$encQuery")
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage("com.google.android.youtube")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                addLog("PLAY_YOUTUBE", "Opened YouTube search for: \"$query\"", true)
                true
            } else {
                val browserIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(browserIntent)
                addLog("PLAY_YOUTUBE", "Opened YouTube search in browser for: \"$query\"", true)
                true
            }
        } catch (e: Exception) {
            addLog("PLAY_YOUTUBE", "Failed to open YouTube: ${e.message}", false)
            false
        }
    }

    /**
     * Directly searches / plays music on Spotify or YouTube Music in 1 step.
     */
    fun playMusic(query: String, app: String = "spotify"): Boolean {
        return try {
            val encQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
            val intent = when (app.lowercase(Locale.ROOT)) {
                "spotify" -> Intent(Intent.ACTION_VIEW, Uri.parse("spotify:search:$encQuery")).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                "youtube", "yt_music" -> Intent(Intent.ACTION_VIEW, Uri.parse("https://music.youtube.com/search?q=$encQuery")).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                else -> Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
                    putExtra(MediaStore.EXTRA_MEDIA_FOCUS, MediaStore.Audio.Media.ENTRY_CONTENT_TYPE)
                    putExtra(MediaStore.EXTRA_MEDIA_TITLE, query)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                addLog("PLAY_MUSIC", "Opened $app search for: \"$query\"", true)
                true
            } else {
                playYoutube(query)
            }
        } catch (e: Exception) {
            addLog("PLAY_MUSIC", "Failed to play music: ${e.message}", false)
            false
        }
    }

    /**
     * Directly opens specific Android settings subpages in 1 step (Storage, Wi-Fi, Bluetooth, Battery, etc.).
     */
    fun openSettingsSection(section: String): Boolean {
        val action = when (section.trim().lowercase(Locale.ROOT)) {
            "storage", "internal_storage", "disk" -> Settings.ACTION_INTERNAL_STORAGE_SETTINGS
            "wifi", "wi-fi", "wireless", "network" -> Settings.ACTION_WIFI_SETTINGS
            "bluetooth", "bt" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "battery", "power", "battery_saver" -> Settings.ACTION_BATTERY_SAVER_SETTINGS
            "apps", "applications", "installed_apps" -> Settings.ACTION_APPLICATION_SETTINGS
            "display", "screen", "brightness" -> Settings.ACTION_DISPLAY_SETTINGS
            "sound", "volume", "audio" -> Settings.ACTION_SOUND_SETTINGS
            "accessibility" -> Settings.ACTION_ACCESSIBILITY_SETTINGS
            "date", "time", "datetime" -> Settings.ACTION_DATE_SETTINGS
            "location", "gps" -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
            else -> Settings.ACTION_SETTINGS
        }
        return try {
            val intent = Intent(action).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            addLog("OPEN_SETTINGS", "Opened Settings ($section)", true)
            true
        } catch (_: Exception) {
            val fallback = Intent(Settings.ACTION_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(fallback)
            addLog("OPEN_SETTINGS", "Opened Settings main page", true)
            true
        }
    }

    /**
     * Controls playback and system volume via AudioManager.
     */
    fun mediaControl(command: String, volumePercent: Int? = null): Boolean {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return try {
            when (command.lowercase(Locale.ROOT)) {
                "pause" -> sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PAUSE)
                "play", "resume" -> sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PLAY)
                "next", "skip" -> sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_NEXT)
                "previous", "prev" -> sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                "volume", "set_volume" -> {
                    val level = volumePercent ?: 50
                    val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    val targetVolume = (level * maxVolume) / 100
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolume, AudioManager.FLAG_SHOW_UI)
                }
            }
            addLog("MEDIA_CONTROL", "Executed media command: $command", true)
            true
        } catch (e: Exception) {
            addLog("MEDIA_CONTROL", "Failed media control: ${e.message}", false)
            false
        }
    }

    private fun sendMediaKeyEvent(keyCode: Int) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val eventTime = SystemClock.uptimeMillis()
        val downEvent = KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, keyCode, 0)
        audioManager.dispatchMediaKeyEvent(downEvent)
        val upEvent = KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, keyCode, 0)
        audioManager.dispatchMediaKeyEvent(upEvent)
    }

    // ==================== GLOBAL KEYS & NAVIGATION ====================

    suspend fun pressHome(): Boolean = withContext(Dispatchers.Main) {
        val service = PhoneAutomationService.instance ?: return@withContext false
        val success = service.performGlobalKey(AccessibilityService.GLOBAL_ACTION_HOME)
        addLog("HOME", if (success) "Pressed Home key" else "Failed to press Home", success)
        success
    }

    suspend fun pressBack(): Boolean = withContext(Dispatchers.Main) {
        val service = PhoneAutomationService.instance ?: return@withContext false
        val success = service.performGlobalKey(AccessibilityService.GLOBAL_ACTION_BACK)
        addLog("BACK", if (success) "Pressed Back key" else "Failed to press Back", success)
        success
    }

    suspend fun pressRecents(): Boolean = withContext(Dispatchers.Main) {
        val service = PhoneAutomationService.instance ?: return@withContext false
        val success = service.performGlobalKey(AccessibilityService.GLOBAL_ACTION_RECENTS)
        addLog("RECENTS", if (success) "Opened App Switcher (Recents)" else "Failed to open Recents", success)
        success
    }

    suspend fun openNotifications(): Boolean = withContext(Dispatchers.Main) {
        val service = PhoneAutomationService.instance ?: return@withContext false
        val success = service.performGlobalKey(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
        addLog("NOTIFICATIONS", if (success) "Opened Notification shade" else "Failed to open Notifications", success)
        success
    }

    suspend fun openQuickSettings(): Boolean = withContext(Dispatchers.Main) {
        val service = PhoneAutomationService.instance ?: return@withContext false
        val success = service.performGlobalKey(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
        addLog("QUICK_SETTINGS", if (success) "Opened Quick Settings" else "Failed to open Quick Settings", success)
        success
    }

    suspend fun openPowerDialog(): Boolean = withContext(Dispatchers.Main) {
        val service = PhoneAutomationService.instance ?: return@withContext false
        val success = service.performGlobalKey(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG)
        addLog("POWER_DIALOG", if (success) "Opened Power menu" else "Failed to open Power menu", success)
        success
    }

    suspend fun lockScreen(): Boolean = withContext(Dispatchers.Main) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return@withContext false
        val service = PhoneAutomationService.instance ?: return@withContext false
        val success = service.performGlobalKey(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
        addLog("LOCK_SCREEN", if (success) "Locked screen" else "Failed to lock screen", success)
        success
    }

    suspend fun takeScreenshot(): Bitmap? = withContext(Dispatchers.Main) {
        val service = PhoneAutomationService.instance ?: return@withContext null
        val bitmap = service.takeScreenshotBitmap()
        if (bitmap != null) {
            addLog("SCREENSHOT", "Captured screen screenshot (${bitmap.width}x${bitmap.height})", true)
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                service.performGlobalKey(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT)
                addLog("SCREENSHOT", "Triggered system screenshot action", true)
            } else {
                addLog("SCREENSHOT", "Screenshot not supported on this OS level", false)
            }
        }
        bitmap
    }

    // ==================== IN-APP AUTOMATION: GESTURES & ACTIONS ====================

    suspend fun tapCoordinates(x: Float, y: Float): Boolean = withContext(Dispatchers.Main) {
        val service = PhoneAutomationService.instance ?: return@withContext false
        val metrics = getDisplayMetrics()
        val actualX = resolveCoordinate(x, metrics.widthPixels)
        val actualY = resolveCoordinate(y, metrics.heightPixels)
        val success = service.clickCoordinate(actualX, actualY)
        addLog("TAP", "Tapped at (${actualX.toInt()}, ${actualY.toInt()})", success)
        success
    }

    suspend fun doubleTapCoordinates(x: Float, y: Float): Boolean = withContext(Dispatchers.Main) {
        val service = PhoneAutomationService.instance ?: return@withContext false
        val metrics = getDisplayMetrics()
        val actualX = resolveCoordinate(x, metrics.widthPixels)
        val actualY = resolveCoordinate(y, metrics.heightPixels)
        val success = service.doubleTapCoordinate(actualX, actualY)
        addLog("DOUBLE_TAP", "Double tapped at (${actualX.toInt()}, ${actualY.toInt()})", success)
        success
    }

    suspend fun longPress(x: Float, y: Float, durationMs: Long = 1000L): Boolean = withContext(Dispatchers.Main) {
        val service = PhoneAutomationService.instance ?: return@withContext false
        val metrics = getDisplayMetrics()
        val actualX = resolveCoordinate(x, metrics.widthPixels)
        val actualY = resolveCoordinate(y, metrics.heightPixels)
        val success = service.longPressCoordinate(actualX, actualY, durationMs)
        addLog("LONG_PRESS", "Long pressed at (${actualX.toInt()}, ${actualY.toInt()}) for ${durationMs}ms", success)
        success
    }

    suspend fun swipe(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long = 300L
    ): Boolean = withContext(Dispatchers.Main) {
        val service = PhoneAutomationService.instance ?: return@withContext false
        val metrics = getDisplayMetrics()
        val sx = resolveCoordinate(startX, metrics.widthPixels)
        val sy = resolveCoordinate(startY, metrics.heightPixels)
        val ex = resolveCoordinate(endX, metrics.widthPixels)
        val ey = resolveCoordinate(endY, metrics.heightPixels)
        val success = service.swipe(sx, sy, ex, ey, durationMs)
        addLog("SWIPE", "Swiped from (${sx.toInt()}, ${sy.toInt()}) to (${ex.toInt()}, ${ey.toInt()})", success)
        success
    }

    suspend fun scrollDown(): Boolean = withContext(Dispatchers.Main) {
        val service = PhoneAutomationService.instance ?: return@withContext false
        val success = service.performScroll(forward = true)
        addLog("SCROLL", "Scrolled down", success)
        success
    }

    suspend fun scrollUp(): Boolean = withContext(Dispatchers.Main) {
        val service = PhoneAutomationService.instance ?: return@withContext false
        val success = service.performScroll(forward = false)
        addLog("SCROLL", "Scrolled up", success)
        success
    }

    suspend fun scrollLeft(): Boolean = withContext(Dispatchers.Main) {
        val metrics = getDisplayMetrics()
        val cy = metrics.heightPixels * 0.5f
        val startX = metrics.widthPixels * 0.85f
        val endX = metrics.widthPixels * 0.15f
        swipe(startX, cy, endX, cy, 300L)
    }

    suspend fun scrollRight(): Boolean = withContext(Dispatchers.Main) {
        val metrics = getDisplayMetrics()
        val cy = metrics.heightPixels * 0.5f
        val startX = metrics.widthPixels * 0.15f
        val endX = metrics.widthPixels * 0.85f
        swipe(startX, cy, endX, cy, 300L)
    }

    suspend fun clickElement(target: String): Boolean = withContext(Dispatchers.Main) {
        val service = PhoneAutomationService.instance ?: return@withContext false
        val trimmed = target.trim().removeSurrounding("[", "]")
        val index = trimmed.toIntOrNull()

        val success = if (index != null) {
            service.clickElement(index = index)
        } else {
            service.clickElement(targetText = trimmed)
        }

        addLog("CLICK", "Clicked element \"$target\" - Success: $success", success)
        success
    }

    suspend fun clickElementByIndex(index: Int): Boolean = withContext(Dispatchers.Main) {
        val service = PhoneAutomationService.instance ?: return@withContext false
        val success = service.clickElement(index = index)
        addLog("CLICK_INDEX", "Clicked element [$index] - Success: $success", success)
        success
    }

    suspend fun clickElementByText(text: String): Boolean = withContext(Dispatchers.Main) {
        val service = PhoneAutomationService.instance ?: return@withContext false
        val success = service.clickElement(targetText = text)
        addLog("CLICK_TEXT", "Clicked text \"$text\" - Success: $success", success)
        success
    }

    suspend fun inputText(
        text: String,
        target: String? = null,
        clearFirst: Boolean = false,
        submit: Boolean = true
    ): Boolean = withContext(Dispatchers.Main) {
        val service = PhoneAutomationService.instance ?: return@withContext false
        val trimmedTarget = target?.trim()?.removeSurrounding("[", "]")
        val targetIndex = trimmedTarget?.toIntOrNull()
        val targetText = if (targetIndex == null && !trimmedTarget.isNullOrBlank()) trimmedTarget else null

        val success = service.inputText(
            targetIndex = targetIndex,
            targetText = targetText,
            textToType = text,
            clearFirst = clearFirst,
            submit = submit
        )
        addLog("TYPE", "Entered text \"$text\" into ${target ?: "focused field"} - Success: $success", success)
        success
    }

    fun pressEnter(): Boolean {
        val service = PhoneAutomationService.instance ?: return false
        val success = service.performImeEnter()
        addLog("PRESS_ENTER", "Dispatched IME enter/search action", success)
        return success
    }

    // ==================== SCREEN INSPECTION ====================

    suspend fun dumpScreenHierarchy(): ScreenHierarchyDump = withContext(Dispatchers.Main) {
        val service = PhoneAutomationService.instance ?: return@withContext ScreenHierarchyDump("android", "Unknown", emptyList())
        val dump = service.dumpScreenHierarchy()
        addLog("INSPECT_SCREEN", "Captured ${dump.nodes.size} UI elements on ${dump.packageName}", true)
        dump
    }

    suspend fun dumpScreenNodes(): List<UiElementNode> = withContext(Dispatchers.Main) {
        dumpScreenHierarchy().nodes
    }

    // ==================== APP LAUNCHING ====================

    fun getInstalledApps(): List<InstalledApp> {
        cachedApps?.let { return it }
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val list = pm.queryIntentActivities(intent, 0).mapNotNull { resolveInfo ->
            val pkg = resolveInfo.activityInfo.packageName
            val label = resolveInfo.loadLabel(pm).toString()
            if (pkg.isNotBlank() && label.isNotBlank()) InstalledApp(label, pkg) else null
        }.distinctBy { it.packageName }.sortedBy { it.name.lowercase(Locale.ROOT) }

        cachedApps = list
        return list
    }

    fun launchApp(appNameOrPackage: String): Boolean {
        val trimmed = appNameOrPackage.trim()
        val pm = context.packageManager

        // 1. Direct package match
        var targetPkg = trimmed
        var launchIntent = pm.getLaunchIntentForPackage(targetPkg)

        // 2. Check alias map
        if (launchIntent == null) {
            val lowerName = trimmed.lowercase(Locale.ROOT)
            val aliasList = appAliases[lowerName]
            if (aliasList != null) {
                for (cand in aliasList) {
                    val intent = pm.getLaunchIntentForPackage(cand)
                    if (intent != null) {
                        launchIntent = intent
                        targetPkg = cand
                        break
                    }
                }
            }
        }

        // 3. Search installed apps by name
        if (launchIntent == null) {
            val lowerName = trimmed.lowercase(Locale.ROOT)
            val installed = getInstalledApps()
            val match = installed.firstOrNull { it.name.lowercase(Locale.ROOT) == lowerName }
                ?: installed.firstOrNull { it.name.lowercase(Locale.ROOT).contains(lowerName) }
                ?: installed.firstOrNull { it.packageName.lowercase(Locale.ROOT).contains(lowerName) }

            if (match != null) {
                launchIntent = pm.getLaunchIntentForPackage(match.packageName)
                targetPkg = match.packageName
            }
        }

        return if (launchIntent != null) {
            launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(launchIntent)
            addLog("LAUNCH_APP", "Launched app: $targetPkg ($trimmed)", true)
            true
        } else {
            addLog("LAUNCH_APP", "App not found: $trimmed", false)
            false
        }
    }

    fun openUrl(url: String): Boolean {
        val fullUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) "https://$url" else url
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(fullUrl)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            addLog("OPEN_URL", "Opened URL: $fullUrl", true)
            true
        } catch (_: Exception) {
            addLog("OPEN_URL", "Failed to open URL: $fullUrl", false)
            false
        }
    }

    private fun resolveCoordinate(value: Float, maxDimension: Int): Float {
        return when {
            value in 0.0001f..1.0f -> value * maxDimension
            else -> value.coerceIn(0f, maxDimension.toFloat())
        }
    }
}
