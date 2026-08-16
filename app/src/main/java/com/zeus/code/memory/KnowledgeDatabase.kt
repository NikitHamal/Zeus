package com.zeus.code.memory

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.UUID

class KnowledgeDatabase(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "zeus_knowledge.db"
        const val DATABASE_VERSION = 1

        const val TABLE_ITEMS = "knowledge_items"
        const val COL_ID = "id"
        const val COL_TITLE = "title"
        const val COL_CONTENT = "content"
        const val COL_TYPE = "type"
        const val COL_TAGS = "tags"
        const val COL_REPO = "repository"
        const val COL_CREATED_AT = "created_at"
        const val COL_UPDATED_AT = "updated_at"

        const val TABLE_FTS = "knowledge_fts"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE $TABLE_ITEMS (
                $COL_ID TEXT PRIMARY KEY,
                $COL_TITLE TEXT NOT NULL,
                $COL_CONTENT TEXT NOT NULL,
                $COL_TYPE TEXT NOT NULL,
                $COL_TAGS TEXT,
                $COL_REPO TEXT,
                $COL_CREATED_AT INTEGER,
                $COL_UPDATED_AT INTEGER
            )
        """.trimIndent())

        // FTS virtual table for fast full-text search
        db.execSQL("""
            CREATE VIRTUAL TABLE $TABLE_FTS USING fts4(
                content="$TABLE_ITEMS",
                $COL_TITLE,
                $COL_CONTENT,
                $COL_TAGS,
                $COL_REPO
            )
        """.trimIndent())
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_FTS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_ITEMS")
        onCreate(db)
    }

    fun insertOrUpdate(item: KnowledgeItem) {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put(COL_ID, item.id)
            put(COL_TITLE, item.title)
            put(COL_CONTENT, item.content)
            put(COL_TYPE, item.type.name)
            put(COL_TAGS, item.tags.joinToString(","))
            put(COL_REPO, item.repository)
            put(COL_CREATED_AT, item.createdAt)
            put(COL_UPDATED_AT, item.updatedAt)
        }
        db.insertWithOnConflict(TABLE_ITEMS, null, cv, SQLiteDatabase.CONFLICT_REPLACE)

        // Re-index FTS
        db.execSQL("DELETE FROM $TABLE_FTS WHERE docid IN (SELECT rowid FROM $TABLE_ITEMS WHERE $COL_ID = ?)", arrayOf(item.id))
        db.execSQL(
            "INSERT INTO $TABLE_FTS(docid, $COL_TITLE, $COL_CONTENT, $COL_TAGS, $COL_REPO) SELECT rowid, $COL_TITLE, $COL_CONTENT, $COL_TAGS, $COL_REPO FROM $TABLE_ITEMS WHERE $COL_ID = ?",
            arrayOf(item.id)
        )
    }

    fun delete(id: String) {
        val db = writableDatabase
        db.execSQL("DELETE FROM $TABLE_FTS WHERE docid IN (SELECT rowid FROM $TABLE_ITEMS WHERE $COL_ID = ?)", arrayOf(id))
        db.delete(TABLE_ITEMS, "$COL_ID = ?", arrayOf(id))
    }

    fun getAll(): List<KnowledgeItem> {
        val db = readableDatabase
        val cursor = db.query(TABLE_ITEMS, null, null, null, null, null, "$COL_UPDATED_AT DESC")
        val items = mutableListOf<KnowledgeItem>()
        cursor.use {
            while (it.moveToNext()) {
                items.add(mapCursor(it))
            }
        }
        return items
    }

    fun getByRepository(repository: String): List<KnowledgeItem> {
        if (repository.isBlank()) return getAll()
        val db = readableDatabase
        val cursor = db.query(TABLE_ITEMS, null, "$COL_REPO = ? OR $COL_REPO = ''", arrayOf(repository), null, null, "$COL_UPDATED_AT DESC")
        val items = mutableListOf<KnowledgeItem>()
        cursor.use {
            while (it.moveToNext()) {
                items.add(mapCursor(it))
            }
        }
        return items
    }

    fun search(query: String): List<KnowledgeItem> {
        if (query.isBlank()) return getAll()
        val db = readableDatabase
        val formattedQuery = query.trim().split("\\s+".toRegex()).joinToString(" ") { "$it*" }
        val cursor = db.rawQuery("""
            SELECT i.* FROM $TABLE_ITEMS i
            JOIN $TABLE_FTS f ON i.rowid = f.docid
            WHERE $TABLE_FTS MATCH ?
            ORDER BY i.$COL_UPDATED_AT DESC
            LIMIT 50
        """.trimIndent(), arrayOf(formattedQuery))

        val items = mutableListOf<KnowledgeItem>()
        cursor.use {
            while (it.moveToNext()) {
                items.add(mapCursor(it))
            }
        }
        return items
    }

    fun searchByRepository(query: String, repository: String): List<KnowledgeItem> {
        if (query.isBlank()) return getByRepository(repository)
        val allMatches = search(query)
        return allMatches.filter { it.repository.isBlank() || it.repository.equals(repository, ignoreCase = true) }
    }

    private fun mapCursor(cursor: android.database.Cursor): KnowledgeItem {
        val id = cursor.getString(cursor.getColumnIndexOrThrow(COL_ID))
        val title = cursor.getString(cursor.getColumnIndexOrThrow(COL_TITLE))
        val content = cursor.getString(cursor.getColumnIndexOrThrow(COL_CONTENT))
        val typeStr = cursor.getString(cursor.getColumnIndexOrThrow(COL_TYPE))
        val tagsStr = cursor.getString(cursor.getColumnIndexOrThrow(COL_TAGS)).orEmpty()
        val repo = cursor.getString(cursor.getColumnIndexOrThrow(COL_REPO)).orEmpty()
        val createdAt = cursor.getLong(cursor.getColumnIndexOrThrow(COL_CREATED_AT))
        val updatedAt = cursor.getLong(cursor.getColumnIndexOrThrow(COL_UPDATED_AT))

        val type = try { MemoryType.valueOf(typeStr) } catch (_: Exception) { MemoryType.CODING_RULE }
        val tags = if (tagsStr.isNotBlank()) tagsStr.split(",").map { it.trim() } else emptyList()

        return KnowledgeItem(
            id = id,
            title = title,
            content = content,
            type = type,
            tags = tags,
            repository = repo,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
}
