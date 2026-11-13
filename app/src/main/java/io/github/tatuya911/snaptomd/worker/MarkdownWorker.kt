package io.github.tatuya911.snaptomd.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Base64
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import io.github.tatuya911.snaptomd.MainActivity
import io.github.tatuya911.snaptomd.data.ApiKeyManager
import io.github.tatuya911.snaptomd.data.AppDatabase
import io.github.tatuya911.snaptomd.data.Category
import io.github.tatuya911.snaptomd.data.LocalExporter
import io.github.tatuya911.snaptomd.data.NoteEntity
import io.github.tatuya911.snaptomd.data.SharedImageStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import kotlin.math.max

/**
 * 共有された画像をバックグラウンドで解析し、Markdownノートを生成するWorker
 * Gemini API (Generative Language API) を利用する
 */
class MarkdownWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val KEY_IMAGE_URI = "IMAGE_URI"
        const val CHANNEL_ID_PROCESSING = "processing_channel"
        const val CHANNEL_ID_COMPLETED = "completed_channel"
        const val NOTIFICATION_ID_PROGRESS = 1001
        const val NOTIFICATION_ID_COMPLETED = 1002

        const val FIXED_INSTRUCTION = """入力された画像（商品・パッケージ・スクショ）を認識し、Web検索を行って正確な情報を取得してください。
出力は必ず以下のMarkdownフォーマットに従って構造化して出力してください。
"""

        /** 固定の指示文とユーザー設定のテンプレートからプロンプトを組み立てる */
        fun buildPrompt(template: String): String {
            val body = template.ifBlank { ApiKeyManager.DEFAULT_TEMPLATE }
            return "$FIXED_INSTRUCTION\n$body"
        }

        /** カテゴリ判定用のプロンプトを組み立てる（「その他」は常に末尾） */
        fun buildClassificationPrompt(categories: List<Category>): String {
            val lines = categories.filter { !it.isOther }.joinToString("\n") {
                if (it.hint.isBlank()) "- ${it.name}" else "- ${it.name}: ${it.hint.trim()}"
            }
            return "この画像が次のどのカテゴリに最も当てはまるか判定し、カテゴリ名のみを出力してください。\n" +
                "どれにも当てはまらない場合は「${Category.OTHER_NAME}」と出力してください。\n" +
                (if (lines.isEmpty()) "" else "$lines\n") +
                "- ${Category.OTHER_NAME}"
        }

        /** 判定結果の文字列からカテゴリを解決する。解決できなければ「その他」 */
        fun resolveCategory(response: String, categories: List<Category>): Category {
            val other = categories.firstOrNull { it.isOther }
                ?: Category(Category.OTHER_NAME, ApiKeyManager.DEFAULT_TEMPLATE)
            val text = response.trim()
            if (text.isEmpty()) return other
            categories.firstOrNull { it.name == text }?.let { return it }
            // 前後の記号（「」・句読点など）を除去して再比較
            val stripped = text.trim { !it.isLetterOrDigit() }
            categories.firstOrNull { it.name == stripped }?.let { return it }
            // 部分一致（長い名前を優先）
            return categories.filter { !it.isOther }
                .sortedByDescending { it.name.length }
                .firstOrNull { text.contains(it.name) }
                ?: other
        }
    }

    private val notificationManager =
        appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override suspend fun doWork(): Result {
        createNotificationChannels()

        // 1. フォアグラウンド通知の表示
        try {
            setForeground(createForegroundInfo("AI解析・Web検索中…"))
        } catch (e: Exception) {
            showProgressNotification("AI解析・Web検索中…")
        }

        val imageUriString = inputData.getString(KEY_IMAGE_URI)

        // 2. APIキーのチェック
        val apiKeyManager = ApiKeyManager(appContext)
        val apiKey = apiKeyManager.getApiKey()

        if (!apiKeyManager.isConfigured()) {
            showErrorNotification("APIキーが未設定です。設定画面から登録してください。")
            // 履歴に紐づかない画像はアプリ内に残り続けるため削除する
            SharedImageStore.delete(appContext, imageUriString)
            return Result.failure()
        }

        // 3. 保存先フォルダのチェック（未設定なら生成せず終了）
        if (!apiKeyManager.isStorageConfigured()) {
            showErrorNotification("保存先フォルダが未設定です。設定画面でMarkdownと画像の保存先を指定してください。")
            SharedImageStore.delete(appContext, imageUriString)
            return Result.failure()
        }

        // 画像ファイル名（タイムスタンプ）。テンプレート・保存で同じ値を使う
        val imageName = LocalExporter.timestampImageName()
        // 履歴(DB)へ保存済みか。保存後は画像を履歴のサムネイルとして使うため削除しない
        var isNoteSaved = false

        return try {
            // カテゴリ判定（カテゴリが「その他」のみ、またはモックモードでは省略）
            val categories = apiKeyManager.getCategories()
            val category = if (apiKeyManager.isMockMode() || categories.size < 2) {
                categories.first { it.isOther }
            } else {
                showProgressNotification("画像を分類中…")
                classifyCategory(apiKeyManager, categories, imageUriString)
            }
            showProgressNotification("AI解析・Web検索中…")

            val markdownText = if (apiKeyManager.isMockMode()) {
                // モックモード: 疑似ディレイ後にテスト用Markdownを生成
                delay(1500)
                generateMockMarkdown()
            } else {
                // 本番通信: Gemini API（テンプレートの {{image}} は事前に画像名へ置換）
                processWithGeminiRest(
                    apiKey = apiKey,
                    modelName = apiKeyManager.getModelName(),
                    template = LocalExporter.applyImagePlaceholder(
                        category.template,
                        if (imageUriString.isNullOrBlank()) "" else imageName
                    ),
                    imageUriString = imageUriString
                )
            }.let {
                // AI出力に {{image}} が残っていた場合も置換
                LocalExporter.applyImagePlaceholder(
                    it,
                    if (imageUriString.isNullOrBlank()) "" else imageName
                )
            }

            // タイトル抽出（最初の見出し "# " を探す）
            val title = extractTitleFromMarkdown(markdownText)

            // Room DB へ保存
            val database = AppDatabase.getDatabase(appContext)
            val note = NoteEntity(
                title = title,
                content = markdownText,
                imageUri = imageUriString,
                category = category.name
            )
            val insertedId = database.noteDao().insertNote(note)
            isNoteSaved = true

            // 指定フォルダへ画像・Markdownを保存（失敗しても履歴は残す）
            try {
                if (!imageUriString.isNullOrBlank()) {
                    LocalExporter.saveImage(
                        appContext,
                        Uri.parse(apiKeyManager.getImageDirUri()),
                        imageName,
                        imageUriString
                    )
                }
                LocalExporter.saveMarkdown(
                    appContext,
                    Uri.parse(apiKeyManager.getMarkdownDirUri()),
                    title,
                    markdownText
                )
            } catch (e: Exception) {
                showErrorNotification("ノートは履歴に保存しましたが、フォルダへの保存に失敗しました: ${e.localizedMessage}")
                return Result.failure()
            }

            // 完了通知を発行
            showCompletedNotification(insertedId, title)

            Result.success()
        } catch (e: CancellationException) {
            // システムによる停止（WorkManagerが再実行する可能性がある）では画像を残したまま中断する
            throw e
        } catch (e: Exception) {
            val friendlyMessage = getFriendlyErrorMessage(e)
            showErrorNotification(friendlyMessage)
            // 生成に失敗し履歴に保存されなかった場合は、アプリ内にコピーした画像を削除する
            if (!isNoteSaved) {
                SharedImageStore.delete(appContext, imageUriString)
            }
            Result.failure()
        }
    }

    /**
     * 画像のカテゴリを判定する。通信・解析に失敗した場合は「その他」にフォールバックする
     */
    private suspend fun classifyCategory(
        apiKeyManager: ApiKeyManager,
        categories: List<Category>,
        imageUriString: String?
    ): Category {
        return try {
            val response = callGemini(
                apiKey = apiKeyManager.getApiKey(),
                modelName = apiKeyManager.getModelName(),
                prompt = buildClassificationPrompt(categories),
                imageUriString = imageUriString,
                useSearch = false,
                temperature = 0.0,
                maxImageSize = 512
            )
            resolveCategory(response, categories)
        } catch (e: Exception) {
            categories.first { it.isOther }
        }
    }

    /**
     * 生成用: テンプレートを含むプロンプトでWeb検索付きのMarkdown生成を行う
     */
    private suspend fun processWithGeminiRest(
        apiKey: String,
        modelName: String,
        template: String,
        imageUriString: String?
    ): String = callGemini(
        apiKey = apiKey,
        modelName = modelName,
        prompt = buildPrompt(template),
        imageUriString = imageUriString,
        useSearch = true,
        temperature = 0.4,
        maxImageSize = 1280
    )

    /**
     * Gemini REST API を直接呼び出して画像解析を行う共通処理
     */
    private suspend fun callGemini(
        apiKey: String,
        modelName: String,
        prompt: String,
        imageUriString: String?,
        useSearch: Boolean,
        temperature: Double,
        maxImageSize: Int
    ): String = withContext(Dispatchers.IO) {
        val cleanModel = modelName.trim().removePrefix("models/")

        // 1. エンドポイントURLの構築
        // APIキーはURLに含めず x-goog-api-key ヘッダのみで送る（URLがログに残るのを防ぐ）
        // Gemini API (Generative Language API) エンドポイント
        val endpointUrl =
            "https://generativelanguage.googleapis.com/v1beta/models/${cleanModel}:generateContent"

        // 2. リクエストJSONの構築
        val partsArray = JSONArray()
        partsArray.put(JSONObject().put("text", prompt))

        if (!imageUriString.isNullOrBlank()) {
            val bitmap = decodeSampledBitmapFromUri(Uri.parse(imageUriString), maxImageSize, maxImageSize)
                ?: throw IllegalStateException("画像を読み込めませんでした。もう一度共有してください。")
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
            val base64Data = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)

            val inlineData = JSONObject().apply {
                put("mimeType", "image/jpeg")
                put("data", base64Data)
            }
            partsArray.put(JSONObject().put("inlineData", inlineData))
        }

        val contentObj = JSONObject().apply {
            put("role", "user")
            put("parts", partsArray)
        }
        val requestJson = JSONObject().apply {
            put("contents", JSONArray().put(contentObj))
            if (useSearch) {
                // Google検索によるグラウンディング（Web検索）を有効化
                put("tools", JSONArray().put(JSONObject().put("google_search", JSONObject())))
            }
            put("generationConfig", JSONObject().put("temperature", temperature))
        }

        // 3. HTTP通信の実行
        val connection = (URL(endpointUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("x-goog-api-key", apiKey)
            doOutput = true
            connectTimeout = 30000
            readTimeout = 60000
        }

        connection.outputStream.use { os ->
            os.write(requestJson.toString().toByteArray(Charsets.UTF_8))
            os.flush()
        }

        val responseCode = connection.responseCode
        if (responseCode == 200) {
            val responseText = connection.inputStream.bufferedReader().use { it.readText() }
            return@withContext GeminiResponseParser.parseText(responseText)
        } else {
            val errorResponse = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            throw GeminiResponseParser.parseError(responseCode, errorResponse)
        }
    }

    /**
     * テスト・開発用のモックレスポンス
     */
    private fun generateMockMarkdown(): String {
        return """# ソニー ワイヤレスノイズキャンセリングヘッドホン WH-1000XM5

## 概要
- **価格 (実売/定価):** 約49,500円（税込）
- **メーカー/ブランド:** ソニー (SONY)
- **分類/カテゴリ:** オーバーイヤー型ワイヤレスヘッドホン

## 特徴・主なスペック
- 統合プロセッサーV1と高音質ノイズキャンセリングプロセッサーQN1のデュアルチップ構成により、業界最高クラスのノイズキャンセリング性能を実現
- 新開発30mmドライバーユニットと軽量かつ高剛性なドームによりクリアで広がりのあるサウンド再生
- 最長30時間の長時間バッテリー駆動および3分充電で約3時間再生の急速充電対応

## リンク・参考情報
- [ソニー公式製品ページ](https://www.sony.jp/headphone/products/WH-1000XM5/)
- [Amazon販売ページ](https://www.amazon.co.jp/dp/B09X5K6M6R)
"""
    }

    /**
     * Markdown文字列からタイトルを抽出
     */
    private fun extractTitleFromMarkdown(markdown: String): String {
        val firstHeaderLine = markdown.lines().firstOrNull { it.trim().startsWith("# ") }
        if (firstHeaderLine != null) {
            val title = firstHeaderLine.trim().removePrefix("#").trim()
            if (title.isNotBlank()) return title
        }
        val firstNonEmptyLine = markdown.lines().firstOrNull { it.isNotBlank() }
        return firstNonEmptyLine?.take(30) ?: "商品メモ"
    }

    /**
     * 画像URIから適切なサイズに縮小したBitmapを読み込む
     */
    private fun decodeSampledBitmapFromUri(uri: Uri, reqWidth: Int, reqHeight: Int): Bitmap? {
        val resolver = appContext.contentResolver
        var stream: InputStream? = null
        try {
            stream = resolver.openInputStream(uri) ?: return null
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeStream(stream, null, options)
            stream.close()

            var inSampleSize = 1
            if (options.outHeight > reqHeight || options.outWidth > reqWidth) {
                val heightRatio = Math.round(options.outHeight.toFloat() / reqHeight.toFloat())
                val widthRatio = Math.round(options.outWidth.toFloat() / reqWidth.toFloat())
                inSampleSize = max(heightRatio, widthRatio)
            }

            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
            }
            stream = resolver.openInputStream(uri) ?: return null
            return BitmapFactory.decodeStream(stream, null, decodeOptions)
        } catch (e: Exception) {
            return null
        } finally {
            stream?.close()
        }
    }

    /**
     * 通知チャンネルの登録
     */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val progressChannel = NotificationChannel(
                CHANNEL_ID_PROCESSING,
                "Markdown生成中",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "AI解析とMarkdown生成の進行状況を表示します"
            }

            val completedChannel = NotificationChannel(
                CHANNEL_ID_COMPLETED,
                "Markdown作成完了",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "ノートの生成完了通知を表示します"
            }

            notificationManager.createNotificationChannel(progressChannel)
            notificationManager.createNotificationChannel(completedChannel)
        }
    }

    /**
     * WorkManager用のForegroundInfoを作成 (Android 14+ 対応のFGSタイプ設定)
     */
    private fun createForegroundInfo(progressText: String): ForegroundInfo {
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID_PROCESSING)
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentTitle("SnapToMD")
            .setContentText(progressText)
            .setProgress(0, 0, true)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NOTIFICATION_ID_PROGRESS,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID_PROGRESS, notification)
        }
    }

    private fun showProgressNotification(progressText: String) {
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID_PROCESSING)
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentTitle("SnapToMD")
            .setContentText(progressText)
            .setProgress(0, 0, true)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        notificationManager.notify(NOTIFICATION_ID_PROGRESS, notification)
    }

    /**
     * 完了通知の発行（タップで該当詳細画面へ遷移）
     */
    private fun showCompletedNotification(noteId: Long, title: String) {
        notificationManager.cancel(NOTIFICATION_ID_PROGRESS)

        val intent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_NOTE_ID, noteId)
        }

        val pendingIntent = PendingIntent.getActivity(
            appContext,
            noteId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID_COMPLETED)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Markdownノート作成完了")
            .setContentText("$title の整理が完了しました。タップして表示")
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        notificationManager.notify(NOTIFICATION_ID_COMPLETED, notification)
    }

    /**
     * エラー発生時に原因がわかりやすい日本語メッセージへ整形
     */
    private fun getFriendlyErrorMessage(e: Exception): String {
        when (e) {
            is UnknownHostException -> return "ネットワークに接続できません。通信状態を確認してください。"
            is SocketTimeoutException -> return "通信がタイムアウトしました。時間を置いて再試行してください。"
            is GeminiApiException -> {
                val detail = e.message ?: ""
                return when {
                    e.httpCode == 400 && (detail.contains("API key not valid", ignoreCase = true) ||
                        detail.contains("API_KEY_INVALID", ignoreCase = true)) ->
                        "APIキーが無効です。設定画面で正しいAPIキーを入力してください。"
                    e.httpCode == 400 ->
                        "リクエストが不正です (400): $detail"
                    e.httpCode == 401 ->
                        "認証に失敗しました (401)。APIキーをご確認ください。"
                    e.httpCode == 403 ->
                        "アクセス権限エラー (403): Google Cloudで「Generative Language API」を有効化し、APIキーの制限（API/アプリ制限）をご確認ください。\n$detail"
                    e.httpCode == 404 ->
                        "モデルまたはエンドポイントが見つかりません (404)。モデル名（gemini-3.8-flash等）をご確認ください。"
                    e.httpCode == 429 || e.status == "RESOURCE_EXHAUSTED" ->
                        "APIの利用制限（レートリミット/無料枠）に達しました。少し時間を置いてから再試行してください。"
                    else -> "生成に失敗しました (HTTP ${e.httpCode}): $detail"
                }
            }
        }
        val rawMessage = e.localizedMessage ?: return "不明なエラーが発生しました"
        return "生成に失敗しました: $rawMessage"
    }

    /**
     * エラー通知の発行
     */
    private fun showErrorNotification(message: String) {
        notificationManager.cancel(NOTIFICATION_ID_PROGRESS)

        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID_COMPLETED)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("SnapToMD エラー")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        notificationManager.notify(NOTIFICATION_ID_COMPLETED, notification)
    }
}
