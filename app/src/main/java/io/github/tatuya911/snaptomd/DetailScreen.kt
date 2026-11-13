package io.github.tatuya911.snaptomd

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.tatuya911.snaptomd.data.AppDatabase
import io.github.tatuya911.snaptomd.data.NoteEntity
import io.github.tatuya911.snaptomd.data.SharedImageStore
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 生成されたMarkdownノートの詳細表示画面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    noteId: Long,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val noteDao = remember { AppDatabase.getDatabase(context).noteDao() }
    val noteState by noteDao.getNoteByIdFlow(noteId).collectAsState(initial = null)

    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = noteState?.title ?: "ノート詳細",
                        maxLines = 1
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
                actions = {
                    noteState?.let { note ->
                        // クリップボードへ全コピー
                        IconButton(onClick = {
                            val clipboard =
                                context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Markdown Content", note.content)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "クリップボードにコピーしました", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "クリップボードにコピー")
                        }

                        // 削除アイコン
                        IconButton(onClick = { showDeleteConfirmDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "削除")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        val currentNote = noteState
        if (currentNote == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 作成日時
                val dateString = remember(currentNote.createdAt) {
                    val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
                    sdf.format(Date(currentNote.createdAt))
                }
                Text(
                    text = "カテゴリ: ${currentNote.category}　生成日時: $dateString",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Markdownコンテンツ表示
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    SelectionContainer {
                        Text(
                            text = currentNote.content,
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
        }
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("ノートの削除") },
            text = { Text("このMarkdownノートを削除しますか？この操作は取り消せません。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmDialog = false
                        coroutineScope.launch {
                            SharedImageStore.delete(context, noteState?.imageUri)
                            noteDao.deleteNoteById(noteId)
                            Toast.makeText(context, "ノートを削除しました", Toast.LENGTH_SHORT).show()
                            onBack()
                        }
                    }
                ) {
                    Text("削除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("キャンセル")
                }
            }
        )
    }
}
