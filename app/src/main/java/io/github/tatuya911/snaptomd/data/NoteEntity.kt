package io.github.tatuya911.snaptomd.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 生成されたMarkdownノートを保持するRoom Entity
 */
@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val title: String,
    val content: String,
    val imageUri: String? = null,
    /** 生成時に判定されたカテゴリ名 */
    val category: String = Category.OTHER_NAME,
    val createdAt: Long = System.currentTimeMillis()
)
