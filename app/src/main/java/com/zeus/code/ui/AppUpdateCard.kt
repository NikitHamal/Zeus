@file:OptIn(ExperimentalMaterial3Api::class)

package com.zeus.code.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.zeus.code.BuildConfig
import com.zeus.code.data.AppUpdate
import com.zeus.code.data.UpdateChecker
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class UpdatePhase { IDLE, CHECKING, UP_TO_DATE, AVAILABLE, DOWNLOADING, READY, ERROR }

/**
 * "Check for updates" card — queries GitHub Releases, downloads the newest
 * APK with a progress bar and hands it to the system package installer.
 */
@Composable
fun AppUpdateCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var phase by remember { mutableStateOf(UpdatePhase.IDLE) }
    var update by remember { mutableStateOf<AppUpdate?>(null) }
    var errorMessage by remember { mutableStateOf("") }
    var progress by remember { mutableStateOf(0f) }
    var apkFile by remember { mutableStateOf<File?>(null) }

    fun runCheck(silent: Boolean) {
        if (phase == UpdatePhase.CHECKING || phase == UpdatePhase.DOWNLOADING) return
        scope.launch {
            if (!silent) phase = UpdatePhase.CHECKING
            runCatching { withContext(Dispatchers.IO) { UpdateChecker.findUpdate() } }
                .onSuccess { info ->
                    update = info
                    phase = if (UpdateChecker.isCurrentBuild(info)) UpdatePhase.UP_TO_DATE else UpdatePhase.AVAILABLE
                }
                .onFailure {
                    errorMessage = it.message ?: "Update check failed."
                    if (!silent) phase = UpdatePhase.ERROR
                }
        }
    }

    fun downloadAndInstall(info: AppUpdate) {
        if (phase == UpdatePhase.DOWNLOADING) return
        scope.launch {
            phase = UpdatePhase.DOWNLOADING
            progress = 0f
            val target = File(context.cacheDir, "updates/${info.apkName}")
            runCatching {
                withContext(Dispatchers.IO) {
                    UpdateChecker.download(info, target) { fraction -> progress = fraction }
                }
            }.onSuccess {
                apkFile = target
                phase = UpdatePhase.READY
                runCatching { installApk(context, target) }
            }.onFailure {
                target.delete()
                errorMessage = it.message ?: "Download failed."
                phase = UpdatePhase.ERROR
            }
        }
    }

    // Check quietly once when Settings opens.
    LaunchedEffect(Unit) { runCheck(silent = true) }

    ElevatedCard(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    Modifier.size(36.dp),
                    shape = RoundedCornerShape(11.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (phase == UpdatePhase.CHECKING || phase == UpdatePhase.DOWNLOADING) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                if (phase == UpdatePhase.UP_TO_DATE) Icons.Rounded.CheckCircle else Icons.Rounded.SystemUpdate,
                                null,
                                Modifier.size(19.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        when (phase) {
                            UpdatePhase.IDLE -> "Check for updates"
                            UpdatePhase.CHECKING -> "Checking for updates…"
                            UpdatePhase.UP_TO_DATE -> "Zeus is up to date"
                            UpdatePhase.AVAILABLE -> "Update available"
                            UpdatePhase.DOWNLOADING -> "Downloading update… ${(progress * 100).toInt()}%"
                            UpdatePhase.READY -> "Update downloaded"
                            UpdatePhase.ERROR -> "Update check failed"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        when (phase) {
                            UpdatePhase.IDLE -> "This device runs ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
                            UpdatePhase.CHECKING -> "Talking to GitHub Releases…"
                            UpdatePhase.UP_TO_DATE -> "You're on the latest build · ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
                            UpdatePhase.AVAILABLE -> update?.let { "${it.title} · ${formatUpdateSize(it.sizeBytes)} · ${it.publishedAt.take(10)}" } ?: ""
                            UpdatePhase.DOWNLOADING -> update?.apkName ?: ""
                            UpdatePhase.READY -> "Saved to device — ready to install"
                            UpdatePhase.ERROR -> errorMessage
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (phase == UpdatePhase.UP_TO_DATE || phase == UpdatePhase.ERROR || phase == UpdatePhase.AVAILABLE) {
                    IconButton(onClick = { runCheck(silent = false) }, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Rounded.Refresh, "Check again", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            when (phase) {
                UpdatePhase.IDLE -> {
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = { runCheck(silent = false) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.SystemUpdate, null, Modifier.size(17.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Check for updates", maxLines = 1)
                    }
                }
                UpdatePhase.DOWNLOADING -> {
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                }
                UpdatePhase.AVAILABLE -> {
                    update?.let { info ->
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = { downloadAndInstall(info) }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Rounded.CloudDownload, null, Modifier.size(17.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Download & install", maxLines = 1)
                        }
                    }
                }
                UpdatePhase.READY -> {
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = { apkFile?.let { runCatching { installApk(context, it) } } },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.SystemUpdate, null, Modifier.size(17.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Install update", maxLines = 1)
                    }
                }
                UpdatePhase.ERROR -> {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { runCheck(silent = false) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Retry", maxLines = 1)
                    }
                }
                else -> Unit
            }
        }
    }
}

private fun formatUpdateSize(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> "%.1f MB".format(bytes / (1024f * 1024f))
    bytes >= 1024L -> "%.0f KB".format(bytes / 1024f)
    else -> "$bytes B"
}

/**
 * Hands the downloaded APK to the system package installer via FileProvider.
 * On API 26+ we first make sure "install unknown apps" is allowed for Zeus.
 */
private fun installApk(context: Context, file: File) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
        !context.packageManager.canRequestPackageInstalls()
    ) {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}
