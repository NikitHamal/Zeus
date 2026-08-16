package com.zeus.code.memory

import android.content.Context
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MemoryManager(context: Context) {
    private val db = KnowledgeDatabase(context)
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _items = MutableStateFlow<List<KnowledgeItem>>(emptyList())
    val items: StateFlow<List<KnowledgeItem>> = _items.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        scope.launch {
            _items.value = db.getAll()
        }
    }

    fun saveMemory(
        title: String,
        content: String,
        type: MemoryType = MemoryType.CODING_RULE,
        tags: List<String> = emptyList(),
        repository: String = "",
        existingId: String? = null
    ) {
        scope.launch {
            val now = System.currentTimeMillis()
            val item = KnowledgeItem(
                id = existingId ?: UUID.randomUUID().toString(),
                title = title.trim(),
                content = content.trim(),
                type = type,
                tags = tags,
                repository = repository.trim(),
                createdAt = now,
                updatedAt = now
            )
            db.insertOrUpdate(item)
            _items.value = db.getAll()
        }
    }

    fun deleteMemory(id: String) {
        scope.launch {
            db.delete(id)
            _items.value = db.getAll()
        }
    }

    suspend fun searchMemories(query: String): List<KnowledgeItem> = withContext(Dispatchers.IO) {
        db.search(query)
    }

    /** Ingests long markdown or text documentation by chunking it into smaller indexed memories. */
    fun ingestDocument(title: String, fullText: String, repository: String = "", tags: List<String> = emptyList()) {
        scope.launch {
            val paragraphs = fullText.split("\n\n")
            val chunks = mutableListOf<String>()
            var currentChunk = StringBuilder()

            for (p in paragraphs) {
                if (currentChunk.length + p.length > 1500) {
                    if (currentChunk.isNotEmpty()) chunks.add(currentChunk.toString().trim())
                    currentChunk = StringBuilder()
                }
                currentChunk.append(p).append("\n\n")
            }
            if (currentChunk.isNotEmpty()) chunks.add(currentChunk.toString().trim())

            chunks.forEachIndexed { index, chunkText ->
                val chunkTitle = if (chunks.size > 1) "$title (Part ${index + 1}/${chunks.size})" else title
                val item = KnowledgeItem(
                    id = UUID.randomUUID().toString(),
                    title = chunkTitle,
                    content = chunkText,
                    type = MemoryType.DOCUMENT_CHUNK,
                    tags = tags + listOf("doc", "chunk"),
                    repository = repository,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
                db.insertOrUpdate(item)
            }
            _items.value = db.getAll()
        }
    }

    /** Assembles relevant memories into a structured markdown prompt snippet for agent sessions. */
    suspend fun buildMemoryContextPrompt(query: String, repository: String = ""): String = withContext(Dispatchers.IO) {
        val matches = if (query.isNotBlank()) db.search(query) else db.getAll().take(5)
        val relevant = matches.filter {
            it.repository.isBlank() || repository.isBlank() || it.repository.equals(repository, ignoreCase = true)
        }.take(5)

        if (relevant.isEmpty()) return@withContext ""

        buildString {
            appendLine("## Zeus Long-Term Memory & Knowledge Context")
            relevant.forEach { item ->
                appendLine("### [${item.type.name}] ${item.title}")
                appendLine(item.content)
                appendLine()
            }
        }
    }
}
