package io.github.tatuya911.snaptomd.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 生成したMarkdownと入力画像をユーザー指定フォルダ(SAF)へ保存する
 */
object LocalExporter {
    private const val MAX_FILE_NAME_LENGTH = 80
    private const val FALLBACK_TITLE = "商品メモ"
    private val INVALID_CHARS = Regex("[\\\\/:*?\"<>|\\[\\]#^\\p{Cntrl}]")

    /** タイムスタンプの画像ファイル名（例: 20261025_132744.jpg） */
    fun timestampImageName(now: Long = System.currentTimeMillis()): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(now))
        return "$stamp.jpg"
    }

    /** ファイル名に使えない文字を置換し、長さを制限する */
    fun sanitizeFileName(title: String): String {
        val cleaned = title
            .replace(INVALID_CHARS, "_")
            .trim()
            .trim('.')
            .trim()
            .take(MAX_FILE_NAME_LENGTH)
            .trim()
            .trimEnd('.')
        return cleaned.ifBlank { FALLBACK_TITLE }
    }

    /** テキスト中の {{image}} を画像ファイル名に置換する */
    fun applyImagePlaceholder(text: String, imageName: String): String {
        return text.replace(ApiKeyManager.IMAGE_PLACEHOLDER, imageName)
    }

    /** 既存名と衝突しない名前を返す（例: base.ext → base (1).ext） */
    fun uniqueName(base: String, ext: String, exists: (String) -> Boolean): String {
        var candidate = "$base.$ext"
        var index = 1
        while (exists(candidate)) {
            candidate = "$base ($index).$ext"
            index++
        }
        return candidate
    }

    /** 画像をフォルダへコピーし、保存したファイル名を返す。失敗時は例外 */
    fun saveImage(context: Context, treeUri: Uri, imageName: String, sourceImageUri: String): String {
        val dir = requireDir(context, treeUri)
        val base = imageName.substringBeforeLast('.')
        val ext = imageName.substringAfterLast('.', "jpg")
        val finalName = uniqueName(base, ext) { dir.findFile(it) != null }
        // 拡張子の自動付与を避けるためバイナリ型で作成し、拡張子なしの名前を渡す
        val file = dir.createFile("image/jpeg", finalName.removeSuffix(".$ext"))
            ?: throw IllegalStateException("画像ファイルを作成できませんでした")
        val resolver = context.contentResolver
        val input = resolver.openInputStream(Uri.parse(sourceImageUri))
            ?: throw IllegalStateException("元の画像を読み込めませんでした")
        input.use { src ->
            val output = resolver.openOutputStream(file.uri)
                ?: throw IllegalStateException("画像の保存先を開けませんでした")
            output.use { src.copyTo(it) }
        }
        return file.name ?: finalName
    }

    /** Markdownを「タイトル.md」でフォルダへ保存し、保存したファイル名を返す。失敗時は例外 */
    fun saveMarkdown(context: Context, treeUri: Uri, title: String, content: String): String {
        val dir = requireDir(context, treeUri)
        val finalName = uniqueName(sanitizeFileName(title), "md") { dir.findFile(it) != null }
        // MIMEが text/markdown だと端末により拡張子が補正されるため、拡張子ごと名前で指定する
        val file = dir.createFile("application/octet-stream", finalName)
            ?: throw IllegalStateException("Markdownファイルを作成できませんでした")
        val output = context.contentResolver.openOutputStream(file.uri)
            ?: throw IllegalStateException("Markdownの保存先を開けませんでした")
        output.use { it.write(content.toByteArray(Charsets.UTF_8)) }
        return file.name ?: finalName
    }

    private fun requireDir(context: Context, treeUri: Uri): DocumentFile {
        val dir = DocumentFile.fromTreeUri(context, treeUri)
        if (dir == null || !dir.exists() || !dir.canWrite()) {
            throw IllegalStateException("保存先フォルダにアクセスできません。設定で再指定してください。")
        }
        return dir
    }
}
