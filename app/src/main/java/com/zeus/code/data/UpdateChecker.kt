package com.zeus.code.data

import com.zeus.code.BuildConfig
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

@Serializable
data class ReleaseAssetDto(
    val name: String = "",
    val browser_download_url: String = "",
    val size: Long = 0L
)

@Serializable
data class ReleaseDto(
    val tag_name: String = "",
    val name: String = "",
    val published_at: String = "",
    val body: String = "",
    val assets: List<ReleaseAssetDto> = emptyList()
)

/** A published build that is newer than the installed one. */
data class AppUpdate(
    val title: String,
    val tag: String,
    val sha: String,
    val apkUrl: String,
    val apkName: String,
    val sizeBytes: Long,
    val publishedAt: String
)

/**
 * Checks GitHub Releases of the Zeus repo for a build newer than the
 * installed one and downloads it. CI tags every release as
 * `build-<commit-sha8>-<run>` and ships `Zeus-<sha8>.apk`, while the app
 * embeds its own commit in BuildConfig.GIT_SHA — prefix comparison decides.
 */
object UpdateChecker {
    private const val REPO = "NikitHamal/Zeus"

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }

    private fun latestRelease(): ReleaseDto {
        val request = Request.Builder()
            .url("https://api.github.com/repos/$REPO/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "Zeus-Android")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("GitHub returned ${response.code}.")
            return json.decodeFromString(ReleaseDto.serializer(), response.body.string())
        }
    }

    /** Returns an [AppUpdate] when the latest release is not this build, else null. */
    fun findUpdate(): AppUpdate {
        val release = latestRelease()
        val asset = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
            ?: error("No APK attached to ${release.tag_name}.")
        val releaseSha = Regex("build-([0-9a-f]+)-", RegexOption.IGNORE_CASE)
            .find(release.tag_name)?.groupValues?.getOrNull(1).orEmpty()
        return AppUpdate(
            title = release.name.ifBlank { release.tag_name },
            tag = release.tag_name,
            sha = releaseSha,
            apkUrl = asset.browser_download_url,
            apkName = asset.name,
            sizeBytes = asset.size,
            publishedAt = release.published_at
        )
    }

    /** True when [update] matches the commit this APK was built from. */
    fun isCurrentBuild(update: AppUpdate): Boolean {
        val current = BuildConfig.GIT_SHA.lowercase()
        if (update.sha.isBlank() || current == "unknown") return false
        val sha = update.sha.lowercase()
        return current.startsWith(sha) || sha.startsWith(current)
    }

    /** Downloads the APK to [target], streaming progress in 0f..1f fractions. */
    fun download(update: AppUpdate, target: File, onProgress: (fraction: Float) -> Unit) {
        val request = Request.Builder()
            .url(update.apkUrl)
            .header("User-Agent", "Zeus-Android")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Download failed (${response.code}).")
            val body = response.body
            val total = body.contentLength().let { if (it > 0) it else update.sizeBytes }
            target.parentFile?.mkdirs()
            body.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    var read = input.read(buffer)
                    while (read != -1) {
                        output.write(buffer, 0, read)
                        done += read
                        if (total > 0) onProgress((done / total.toFloat()).coerceIn(0f, 1f))
                        read = input.read(buffer)
                    }
                    output.flush()
                }
            }
        }
    }
}
