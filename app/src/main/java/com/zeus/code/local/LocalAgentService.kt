package com.zeus.code.local

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.zeus.code.MainActivity
import com.zeus.code.R
import com.zeus.code.data.BackgroundAgentApi
import com.zeus.code.data.SecureTokenStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File

/**
 * Foreground service that executes Local Mode coding tasks on-device, one at
 * a time, even when Zeus is in the background or the screen is off.
 */
class LocalAgentService : Service() {

    companion object {
        const val CHANNEL_ID = "zeus_local_agent"
        const val NOTIFICATION_ID = 47001
        const val ACTION_RUN = "com.zeus.code.local.RUN"
        const val ACTION_STOP_CURRENT = "com.zeus.code.local.STOP_CURRENT"

        /** Queues [taskId] (already persisted as QUEUED) and starts the worker. */
        fun enqueue(context: Context, taskId: String) {
            LocalTaskStore.init(context)
            val intent = Intent(context, LocalAgentService::class.java).apply {
                setAction(ACTION_RUN)
                putExtra("taskId", taskId)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        /** Requests cancellation of the currently running task (and drains no further queue). */
        fun stopCurrent(context: Context) {
            val intent = Intent(context, LocalAgentService::class.java).apply {
                setAction(ACTION_STOP_CURRENT)
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var worker: Job? = null

    @Volatile
    private var stopRequested = false
    private var wakeLock: PowerManager.WakeLock? = null

    private lateinit var providerStore: LocalProviderStore
    private lateinit var nebiansTokenStore: SecureTokenStore
    private lateinit var llm: LocalLlmClient

    override fun onCreate() {
        super.onCreate()
        providerStore = LocalProviderStore(this)
        nebiansTokenStore = SecureTokenStore(this, "background_agent")
        llm = LocalLlmClient(
            nebiansApi = BackgroundAgentApi(this),
            nebiansToken = { nebiansTokenStore.read() }
        ).apply {
            zenKeyProvider = { providerStore.zenKey() }
            customConfigProvider = { id -> providerStore.customProvider(id) }
            customKeyProvider = { id -> providerStore.customKey(id) }
        }
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_CURRENT -> {
                stopRequested = true
                startAsForeground("Stopping…")
                // Cancel the in-flight model round-trip so the stop is prompt.
                runCatching { worker?.cancel() }
                // If no worker restarts (nothing new queued afterwards), end
                // the service instead of stranding a "Stopping…" notification.
                scope.launch {
                    kotlinx.coroutines.delay(2_000)
                    if (worker?.isActive != true) stopSelf()
                }
            }
            else -> {
                stopRequested = false
                startAsForeground("Starting…")
                if (worker?.isActive != true) {
                    worker = scope.launch { drainQueue() }
                }
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        worker?.cancel()
        scope.cancel()
        releaseWakeLock()
        super.onDestroy()
    }

    // ------------------------------------------------------------------
    // Queue processing
    // ------------------------------------------------------------------

    private suspend fun drainQueue() {
        try {
            while (!stopRequested) {
                val task = LocalTaskStore.nextQueued() ?: break
                runTask(task)
            }
        } finally {
            // Runs even when the worker was cancelled by a stop request.
            stopSelf()
        }
    }

    private suspend fun runTask(task: LocalTask) {
        val workspaceDir = File(task.workspacePath)
        if (!workspaceDir.isDirectory) {
            complete(
                task.copy(
                    status = LocalTaskStatus.FAILED,
                    error = "Workspace '${task.workspaceName}' no longer exists.",
                    completedAt = System.currentTimeMillis()
                )
            )
            return
        }

        LocalTaskStore.save(
            task.copy(status = LocalTaskStatus.RUNNING, startedAt = System.currentTimeMillis(), steps = 0)
        )
        acquireWakeLock()
        startAsForeground("Working on ${task.workspaceName}")

        try {
            val engine = LocalAgentEngine(
                llm = llm,
                onEvent = { kind, text ->
                    onEngineEvent(task.id, kind, text)
                },
                shouldStop = {
                    stopRequested || !isActiveTask(task.id)
                }
            )

            val outcome = engine.run(
                task = LocalTaskStore.get(task.id) ?: task,
                workspace = workspaceDir
            ) { step, changed ->
                onStepProgress(task.id, step)
            }

            val finalStatus = when {
                outcome.error == "stopped" -> LocalTaskStatus.STOPPED
                outcome.successful -> LocalTaskStatus.COMPLETED
                else -> LocalTaskStatus.FAILED
            }
            val finished = (LocalTaskStore.get(task.id) ?: task).copy(
                status = finalStatus,
                summary = outcome.summary,
                error = if (outcome.error == "stopped") "Stopped by user." else outcome.error.orEmpty(),
                changedFiles = outcome.changedFiles.toList(),
                completedAt = System.currentTimeMillis()
            )
            complete(finished)
            notifyCompletion(finished)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            val stopped = (LocalTaskStore.get(task.id) ?: task).copy(
                status = LocalTaskStatus.STOPPED,
                error = "Stopped by user.",
                completedAt = System.currentTimeMillis()
            )
            complete(stopped)
            notifyCompletion(stopped)
            throw cancelled
        } finally {
            releaseWakeLock()
        }
    }

    private fun isActiveTask(taskId: String): Boolean {
        val status = LocalTaskStore.get(taskId)?.status ?: return false
        return LocalTaskStatus.isActive(status)
    }

    private suspend fun onEngineEvent(taskId: String, kind: String, text: String) {
        val current = LocalTaskStore.get(taskId) ?: return
        LocalTaskStore.save(
            current.copy(
                events = current.events + LocalEvent(
                    id = LocalTaskStore.nextEventId(current),
                    at = System.currentTimeMillis(),
                    kind = kind,
                    text = text
                )
            )
        )
        when (kind) {
            LocalEventKind.TOOL -> updateNotification("⚙ ${text.take(80)}")
            LocalEventKind.ERROR -> updateNotification(text.take(120))
        }
    }

    private suspend fun onStepProgress(taskId: String, step: Int) {
        val current = LocalTaskStore.get(taskId) ?: return
        LocalTaskStore.save(current.copy(steps = step))
        updateNotification(current.progressLabel)
    }

    private suspend fun complete(task: LocalTask) {
        LocalTaskStore.save(task)
    }

    // ------------------------------------------------------------------
    // Notification plumbing
    // ------------------------------------------------------------------

    private fun createChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Local Agent",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Progress of on-device coding tasks"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_zeus_bolt)
            .setContentTitle("Zeus Local Agent")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent)
            .build()
    }

    private fun startAsForeground(text: String) {
        runCatching { startForeground(NOTIFICATION_ID, buildNotification(text)) }
    }

    private fun updateNotification(text: String) {
        runCatching {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIFICATION_ID, buildNotification(text))
        }
    }

    private fun notifyCompletion(task: LocalTask) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val title = when (task.status) {
            LocalTaskStatus.COMPLETED -> "Task completed — ${task.workspaceName}"
            LocalTaskStatus.STOPPED -> "Task stopped — ${task.workspaceName}"
            else -> "Task failed — ${task.workspaceName}"
        }
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_zeus_bolt)
            .setContentTitle(title)
            .setContentText(task.summary.ifBlank { task.error }.take(180))
            .setAutoCancel(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()
        runCatching { manager.notify(NOTIFICATION_ID + 1, notification) }
    }

    // ------------------------------------------------------------------
    // Wake lock
    // ------------------------------------------------------------------

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Zeus:LocalAgent")
            .apply { acquire(30 * 60 * 1000L) }
    }

    private fun releaseWakeLock() {
        runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
        wakeLock = null
    }
}
