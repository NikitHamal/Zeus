package com.zeus.code.memory

import kotlinx.serialization.Serializable

@Serializable
enum class MemoryType {
    USER_PREFERENCE,
    PROJECT_ARCHITECTURE,
    CODING_RULE,
    SNIPPET,
    DOCUMENT_CHUNK,
    SESSION_SUMMARY
}

@Serializable
data class KnowledgeItem(
    val id: String,
    val title: String,
    val content: String,
    val type: MemoryType = MemoryType.CODING_RULE,
    val tags: List<String> = emptyList(),
    val repository: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class MemorySearchResult(
    val item: KnowledgeItem,
    val score: Float
)
