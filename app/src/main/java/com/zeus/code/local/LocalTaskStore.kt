package com.zeus.code.local

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Durable storage + live state for Local Mode tasks.
 *
 * A single instance is shared between the UI and [LocalAgentService]; every
 * task is persisted as pretty JSON under `filesDir/local_agent/` so history
 * survives process death.
 */
object LocalTaskStore {

    private const val DIR = "local_agent"
    private const val MAX_TASKS = 60

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        prettyPrint = true
    }

    private lateinit var root: File
    private val cache = ConcurrentHashMap<String, LocalTask>()

    private val _tasks = MutableStateFlow<List<LocalTask>>(emptyList())
    val tasks: kotlinx.coroutines.flow.StateFlow<List<LocalTask>> = _tasks

    fun init(context: Context) {
        if (::root.isInitialized) return
        root = File(context.applicationContext.filesDir, DIR).apply { mkdirs() }
        reload()
    }

    @Synchronized
    private fun reload() {
        val loaded = root.listFiles()?.mapNotNull { file ->
            runCatching { json.decodeFromString<LocalTask>(file.readText()) }.getOrNull()
        }.orEmpty()
        loaded.forEach { cache[it.id] = it }
        publish()
    }

    /** Newest first; running/queued tasks float above finished ones. */
    private fun publish() {
        _tasks.value = cache.values
            .sortedWith(
                compareByDescending<LocalTask> { LocalTaskStatus.isActive(it.status) }
                    .thenByDescending { it.updatedAt }
            )
    }

    @Synchronized
    fun get(id: String): LocalTask? = cache[id] ?: root.listFiles()
        ?.firstOrNull { it.nameWithoutExtension == id }
        ?.let { file -> runCatching { json.decodeFromString<LocalTask>(file.readText()) }.getOrNull() }
        ?.also { cache[it.id] = it }

    @Synchronized
    fun save(task: LocalTask) {
        val stamped = task.copy(updatedAt = System.currentTimeMillis())
        cache[stamped.id] = stamped
        File(root, "${stamped.id}.json").writeText(json.encodeToString(stamped))
        enforceRetention()
        publish()
    }

    @Synchronized
    fun appendEvent(taskId: String, event: LocalEvent): LocalTask? {
        val current = get(taskId) ?: return null
        val updated = current.copy(events = current.events + event)
        save(updated)
        return updated
    }

    @Synchronized
    fun delete(id: String) {
        cache.remove(id)
        File(root, "$id.json").delete()
        publish()
    }

    @Synchronized
    fun deleteAllFinished() {
        cache.values.filter { !it.isActive }.forEach { delete(it.id) }
        publish()
    }

    /** Any task the service should pick up (queued, oldest first). */
    @Synchronized
    fun nextQueued(): LocalTask? =
        cache.values
            .filter { it.status == LocalTaskStatus.QUEUED }
            .minByOrNull { it.createdAt }
            ?: root.listFiles()?.mapNotNull { file ->
                runCatching { json.decodeFromString<LocalTask>(file.readText()) }.getOrNull()
            }?.filter { it.status == LocalTaskStatus.QUEUED }?.minByOrNull { it.createdAt }

    @Synchronized
    fun anyRunning(): Boolean =
        cache.values.any { it.status == LocalTaskStatus.RUNNING } ||
            root.listFiles()?.mapNotNull { file ->
                runCatching { json.decodeFromString<LocalTask>(file.readText()) }.getOrNull()
            }.orEmpty().any { it.status == LocalTaskStatus.RUNNING }

    /** Marks tasks stuck in `running` (e.g. after a crash) as failed. */
    @Synchronized
    fun recoverOrphans() {
        cache.values.filter { it.status == LocalTaskStatus.RUNNING }.forEach { orphan ->
            save(
                orphan.copy(
                    status = LocalTaskStatus.FAILED,
                    error = "Interrupted (app closed while running).",
                    completedAt = System.currentTimeMillis()
                )
            )
        }
    }

    private fun enforceRetention() {
        val files = root.listFiles()?.sortedBy { it.lastModified() }.orEmpty()
        if (files.size > MAX_TASKS) {
            files.take(files.size - MAX_TASKS).forEach { file ->
                val id = file.nameWithoutExtension
                if (cache[id]?.isActive != true) {
                    cache.remove(id)
                    file.delete()
                }
            }
        }
    }

    fun newId(): String = "lt_" + java.util.UUID.randomUUID().toString().replace("-", "").take(20)

    fun nextEventId(task: LocalTask): Long = (task.events.maxOfOrNull { it.id } ?: 0L) + 1L
}
