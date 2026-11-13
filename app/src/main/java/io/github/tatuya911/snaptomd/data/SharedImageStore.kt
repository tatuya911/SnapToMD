package io.github.tatuya911.snaptomd.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.util.UUID
import kotlin.math.max

/**
 * 共有された画像をアプリ内ストレージへ縮小保存・削除する
 */
object SharedImageStore {
    private const val DIR_NAME = "shared_images"
    private const val MAX_SIZE = 1280

    /** 共有URIの画像を縮小JPEGとして保存し、保存先ファイルのURI文字列を返す。失敗時は null */
    fun saveFromUri(context: Context, source: Uri): String? {
        val bitmap = decodeSampled(context, source, MAX_SIZE) ?: return null
        return try {
            val dir = File(context.filesDir, DIR_NAME).apply { mkdirs() }
            val file = File(dir, "${UUID.randomUUID()}.jpg")
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            Uri.fromFile(file).toString()
        } catch (e: Exception) {
            null
        }
    }

    /** アプリ内保存の画像ファイルであれば削除する（共有元URIなどは対象外） */
    fun delete(context: Context, imageUri: String?) {
        if (imageUri.isNullOrBlank()) return
        try {
            val uri = Uri.parse(imageUri)
            if (uri.scheme != "file") return
            val file = File(uri.path ?: return)
            if (file.parentFile?.canonicalPath == File(context.filesDir, DIR_NAME).canonicalPath) {
                file.delete()
            }
        } catch (e: Exception) {
            // 削除失敗は無視
        }
    }

    /** 長辺が maxSize 程度になるよう縮小して読み込む */
    fun decodeSampled(context: Context, uri: Uri, maxSize: Int): Bitmap? {
        val resolver = context.contentResolver
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            val longSide = max(bounds.outWidth, bounds.outHeight)
            while (longSide / (sample * 2) >= maxSize) sample *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        } catch (e: Exception) {
            null
        }
    }
}
