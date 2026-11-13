package io.github.tatuya911.snaptomd

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.tatuya911.snaptomd.data.ApiKeyManager
import io.github.tatuya911.snaptomd.data.Category
import io.github.tatuya911.snaptomd.data.CategoryYaml
import io.github.tatuya911.snaptomd.data.ConflictAction
import kotlinx.coroutines.launch

/**
 * フォルダ選択行。選択結果は永続URI権限を取得してコールバックへ渡す
 */
@Composable
private fun FolderPickerRow(label: String, dirUri: String?, onPicked: (Uri) -> Unit) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            onPicked(uri)
        }
    }
    val displayName = dirUri?.let {
        DocumentFile.fromTreeUri(context, Uri.parse(it))?.name ?: it
    } ?: "未設定"

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.labelLarge)
            Text(
                text = displayName,
                style = MaterialTheme.typography.bodySmall,
                color = if (dirUri == null) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        OutlinedButton(onClick = { launcher.launch(dirUri?.let { Uri.parse(it) }) }) {
            Text("フォルダを選択")
        }
    }
}

/**
 * 外部ブラウザでURLを開くリンクボタン
 */
@Composable
private fun LinkButton(label: String, url: String) {
    val context = LocalContext.current
    TextButton(onClick = {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }) {
        Text(label)
        Spacer(modifier = Modifier.width(4.dp))
        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
    }
}

/**
 * カテゴリ1件分の編集カード（名前・判定のヒント・テンプレート）。「その他」は名前変更・削除不可
 */
@Composable
private fun CategoryEditor(
    category: Category,
    onChange: (Category) -> Unit,
    onDelete: () -> Unit
) {
    var isExpanded by rememberSaveable(category.name) { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
                    .padding(vertical = 4.dp)
            ) {
                Text(
                    text = if (category.isOther) "${category.name}（必須・削除不可）" else category.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (!category.isOther) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "カテゴリを削除")
                    }
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "閉じる" else "開く"
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                ) {
                    if (!category.isOther) {
                        OutlinedTextField(
                            value = category.name,
                            onValueChange = { onChange(category.copy(name = it)) },
                            label = { Text("カテゴリ名") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                    OutlinedTextField(
                        value = category.hint,
                        onValueChange = { onChange(category.copy(hint = it)) },
                        label = { Text("判定のヒント（任意）") },
                        placeholder = {
                            Text(
                                if (category.isOther) "どのカテゴリにも当てはまらない画像"
                                else "例: 映画・ドラマのポスター、パッケージ画像"
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )
                    OutlinedTextField(
                        value = category.template,
                        onValueChange = { onChange(category.copy(template = it)) },
                        label = { Text("テンプレート") },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 8
                    )
                    OutlinedButton(
                        onClick = { onChange(category.copy(template = ApiKeyManager.DEFAULT_TEMPLATE)) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("テンプレートを初期値に戻す")
                    }
                }
            }
        }
    }
}

/**
 * カテゴリ追加ダイアログ。空・重複・「その他」は追加不可
 */
@Composable
private fun AddCategoryDialog(
    existingNames: List<String>,
    onAdd: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    val trimmed = name.trim()
    val error = when {
        trimmed.isEmpty() -> null
        trimmed in existingNames -> "同じ名前のカテゴリが既にあります"
        else -> null
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("カテゴリを追加") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("カテゴリ名（例: 家電）") },
                isError = error != null,
                supportingText = error?.let { { Text(it) } },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                enabled = trimmed.isNotEmpty() && error == null,
                onClick = { onAdd(trimmed) }
            ) { Text("追加") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } }
    )
}

/**
 * インポート時の同名競合解決ダイアログ。各競合カテゴリごとに「上書き」または「スキップ」を選択可能
 */
@Composable
private fun ConflictResolutionDialog(
    conflicts: List<Category>,
    onConfirm: (Map<String, ConflictAction>) -> Unit,
    onDismiss: () -> Unit
) {
    val resolutions = remember {
        mutableStateMapOf<String, ConflictAction>().apply {
            conflicts.forEach { put(it.name, ConflictAction.SKIP) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("同名カテゴリの処理") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "既に存在するカテゴリが見つかりました。カテゴリごとに上書きするかスキップするか選択してください。",
                    style = MaterialTheme.typography.bodyMedium
                )
                HorizontalDivider()
                conflicts.forEach { conflict ->
                    val action = resolutions[conflict.name] ?: ConflictAction.SKIP
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = if (conflict.isOther) "${conflict.name}（必須・削除不可）" else conflict.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable {
                                    resolutions[conflict.name] = ConflictAction.SKIP
                                }
                            ) {
                                RadioButton(
                                    selected = action == ConflictAction.SKIP,
                                    onClick = { resolutions[conflict.name] = ConflictAction.SKIP }
                                )
                                Text("スキップ")
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable {
                                    resolutions[conflict.name] = ConflictAction.OVERWRITE
                                }
                            ) {
                                RadioButton(
                                    selected = action == ConflictAction.OVERWRITE,
                                    onClick = { resolutions[conflict.name] = ConflictAction.OVERWRITE }
                                )
                                Text("上書き")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(resolutions.toMap()) }) {
                Text("インポート")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("キャンセル")
            }
        }
    )
}

/**
 * Gemini API設定画面（APIキー・モデル・カテゴリ別Markdownテンプレート）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val apiKeyManager = remember { ApiKeyManager(context) }

    // 保存済みAPIキーは再確認できないよう、入力欄には読み込まない
    var apiKey by remember { mutableStateOf("") }
    var isApiKeySaved by remember { mutableStateOf(apiKeyManager.getApiKey().isNotBlank()) }
    var modelName by remember { mutableStateOf(apiKeyManager.getModelName()) }
    val categories = remember {
        mutableStateListOf<Category>().apply { addAll(apiKeyManager.getCategories()) }
    }
    var showAddDialog by remember { mutableStateOf(false) }
    var pendingDeleteIndex by remember { mutableStateOf<Int?>(null) }
    var markdownDirUri by remember { mutableStateOf(apiKeyManager.getMarkdownDirUri()) }
    var imageDirUri by remember { mutableStateOf(apiKeyManager.getImageDirUri()) }
    var isTesting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    var pendingImportCategories by remember { mutableStateOf<List<Category>?>(null) }
    var pendingConflicts by remember { mutableStateOf<List<Category>?>(null) }
    var importErrorMessage by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/x-yaml")
    ) { uri ->
        if (uri != null) {
            try {
                val yamlText = CategoryYaml.export(categories.toList())
                context.contentResolver.openOutputStream(uri)?.use { os ->
                    os.write(yamlText.toByteArray(Charsets.UTF_8))
                }
                Toast.makeText(context, "カテゴリ設定をエクスポートしました", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "エクスポートに失敗しました: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                val yamlText = context.contentResolver.openInputStream(uri)?.use { stream ->
                    stream.bufferedReader(Charsets.UTF_8).readText()
                } ?: ""
                val result = CategoryYaml.importYaml(yamlText)
                if (result.isSuccess) {
                    val imported = result.getOrThrow()
                    val conflicts = CategoryYaml.findConflicts(categories.toList(), imported)
                    if (conflicts.isEmpty()) {
                        val merged = CategoryYaml.merge(
                            current = categories.toList(),
                            imported = imported,
                            resolutions = emptyMap()
                        )
                        categories.clear()
                        categories.addAll(merged)
                        Toast.makeText(
                            context,
                            "${imported.size}件のカテゴリを読み込みました（保存ボタンで確定）",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        pendingImportCategories = imported
                        pendingConflicts = conflicts
                    }
                } else {
                    importErrorMessage = result.exceptionOrNull()?.localizedMessage ?: "YAMLの読み込みに失敗しました"
                }
            } catch (e: Exception) {
                importErrorMessage = "ファイルの読み込みに失敗しました: ${e.localizedMessage}"
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("設定") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        // 空欄の場合は既存キーを維持する
                        if (apiKey.isNotBlank()) {
                            apiKeyManager.saveApiKey(apiKey.trim())
                            isApiKeySaved = true
                            apiKey = ""
                        }
                        apiKeyManager.saveModelName(modelName.ifBlank { ApiKeyManager.DEFAULT_MODEL })
                        apiKeyManager.saveCategories(
                            categories.map {
                                it.copy(template = it.template.ifBlank { ApiKeyManager.DEFAULT_TEMPLATE })
                            }
                        )
                        categories.clear()
                        categories.addAll(apiKeyManager.getCategories())
                        Toast.makeText(context, "設定を保存しました", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.Save, contentDescription = "設定を保存")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (showAddDialog) {
            AddCategoryDialog(
                existingNames = categories.map { it.name },
                onAdd = { newName ->
                    // 「その他」は末尾固定のため、その手前に挿入
                    categories.add(
                        categories.size - 1,
                        Category(newName, ApiKeyManager.DEFAULT_TEMPLATE)
                    )
                    showAddDialog = false
                },
                onDismiss = { showAddDialog = false }
            )
        }
        pendingDeleteIndex?.let { index ->
            AlertDialog(
                onDismissRequest = { pendingDeleteIndex = null },
                title = { Text("カテゴリを削除") },
                text = { Text("「${categories.getOrNull(index)?.name.orEmpty()}」を削除しますか？") },
                confirmButton = {
                    TextButton(onClick = {
                        if (index in categories.indices) categories.removeAt(index)
                        pendingDeleteIndex = null
                    }) { Text("削除") }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDeleteIndex = null }) { Text("キャンセル") }
                }
            )
        }

        pendingConflicts?.let { conflicts ->
            ConflictResolutionDialog(
                conflicts = conflicts,
                onConfirm = { resolutions ->
                    val imported = pendingImportCategories.orEmpty()
                    val merged = CategoryYaml.merge(
                        current = categories.toList(),
                        imported = imported,
                        resolutions = resolutions
                    )
                    categories.clear()
                    categories.addAll(merged)
                    pendingConflicts = null
                    pendingImportCategories = null
                    Toast.makeText(
                        context,
                        "インポートを反映しました（右上の保存ボタンで確定してください）",
                        Toast.LENGTH_SHORT
                    ).show()
                },
                onDismiss = {
                    pendingConflicts = null
                    pendingImportCategories = null
                }
            )
        }

        importErrorMessage?.let { message ->
            AlertDialog(
                onDismissRequest = { importErrorMessage = null },
                title = { Text("インポートエラー") },
                text = { Text(message) },
                confirmButton = {
                    TextButton(onClick = { importErrorMessage = null }) {
                        Text("OK")
                    }
                }
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ステータスカード
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "現在のステータス",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        when {
                            apiKeyManager.isMockMode() -> {
                                SuggestionChip(
                                    onClick = {},
                                    label = { Text("モックモード有効 (テスト中)") },
                                    colors = SuggestionChipDefaults.suggestionChipColors(
                                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                        labelColor = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                )
                            }
                            apiKeyManager.isConfigured() -> {
                                SuggestionChip(
                                    onClick = {},
                                    label = { Text("Gemini API 設定済み") },
                                    colors = SuggestionChipDefaults.suggestionChipColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        labelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                )
                            }
                            else -> {
                                SuggestionChip(
                                    onClick = {},
                                    label = { Text("未設定（要登録）") },
                                    colors = SuggestionChipDefaults.suggestionChipColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer,
                                        labelColor = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // APIキー入力
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Gemini API Key",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                LinkButton("キーを取得", "https://aistudio.google.com/apikey")
            }

            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("API Key") },
                placeholder = {
                    Text(if (isApiKeySaved) "設定済み" else "AIzaSy... (モック利用時は MOCK_KEY)")
                },
                leadingIcon = {
                    Icon(Icons.Default.Key, contentDescription = null)
                },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // モデル名入力
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "モデル名",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                LinkButton("モデル一覧", "https://ai.google.dev/gemini-api/docs/models")
            }

            OutlinedTextField(
                value = modelName,
                onValueChange = { modelName = it },
                label = { Text("モデル名 (例: gemini-3.8-flash)") },
                placeholder = { Text("gemini-3.8-flash") },
                leadingIcon = {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null)
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // 接続テストボタン
            OutlinedButton(
                enabled = !isTesting && (apiKey.isNotBlank() || isApiKeySaved),
                onClick = {
                    isTesting = true
                    scope.launch {
                        // 入力欄が空なら保存済みキーを内部的に使用する
                        val error = io.github.tatuya911.snaptomd.worker.GeminiConnectionTester.test(
                            apiKey = apiKey.ifBlank { apiKeyManager.getApiKey() }.trim(),
                            modelName = modelName.ifBlank { ApiKeyManager.DEFAULT_MODEL }
                        )
                        isTesting = false
                        Toast.makeText(
                            context,
                            error ?: "接続に成功しました",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isTesting) "テスト中…" else "接続テスト")
            }

            // 保存先フォルダ
            Text(
                text = "保存先フォルダ",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "生成したMarkdown（見出し.md）と入力画像（タイムスタンプ.jpg）をここに保存します。両方の指定が必須です。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FolderPickerRow(
                label = "Markdown保存先",
                dirUri = markdownDirUri,
                onPicked = {
                    apiKeyManager.saveMarkdownDirUri(it.toString())
                    markdownDirUri = it.toString()
                }
            )
            FolderPickerRow(
                label = "画像保存先",
                dirUri = imageDirUri,
                onPicked = {
                    apiKeyManager.saveImageDirUri(it.toString())
                    imageDirUri = it.toString()
                }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // カテゴリ別Markdownテンプレート
            Text(
                text = "カテゴリ別Markdownテンプレート",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "画像の内容からカテゴリを判定し、そのカテゴリのテンプレートでMarkdownを生成します。「判定のヒント」にはAIがカテゴリを見分ける手がかりを書きます。どれにも当てはまらない画像は「その他」のテンプレートを使います（削除不可）。\n「{{image}}」と書くと保存時の画像ファイル名に置換されます（例: ![[{{image}}]]）。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            categories.forEachIndexed { index, category ->
                CategoryEditor(
                    category = category,
                    onChange = { categories[index] = it },
                    onDelete = { pendingDeleteIndex = index }
                )
            }

            OutlinedButton(
                onClick = { showAddDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("カテゴリを追加")
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { exportLauncher.launch("snaptomd_categories.yaml") },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("エクスポート")
                }
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("*/*")) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("インポート")
                }
            }

        }
    }
}
