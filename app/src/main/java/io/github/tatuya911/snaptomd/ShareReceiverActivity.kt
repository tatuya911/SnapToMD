package io.github.tatuya911.snaptomd

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import io.github.tatuya911.snaptomd.data.SharedImageStore
import io.github.tatuya911.snaptomd.worker.MarkdownWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 他アプリからの画像共有を受け取り、バックグラウンド処理を開始してすぐ終了する透明Activity。
 * SnapToMD本体の画面は起動しない。
 */
class ShareReceiverActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedUri = extractImageUri(intent)
        if (sharedUri == null) {
            finishSilently()
            return
        }

        lifecycleScope.launch {
            // 共有元URIの権限はこのActivity終了後に失われるため、先にアプリ内へコピーする
            val localUri = withContext(Dispatchers.IO) {
                SharedImageStore.saveFromUri(applicationContext, sharedUri)
            }
            if (localUri == null) {
                Toast.makeText(
                    this@ShareReceiverActivity,
                    "画像を読み込めませんでした",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                val workRequest = OneTimeWorkRequestBuilder<MarkdownWorker>()
                    .setInputData(workDataOf(MarkdownWorker.KEY_IMAGE_URI to localUri))
                    .build()
                WorkManager.getInstance(applicationContext).enqueue(workRequest)
                Toast.makeText(
                    this@ShareReceiverActivity,
                    "バックグラウンドでMarkdown生成を開始しました",
                    Toast.LENGTH_LONG
                ).show()
            }
            finishSilently()
        }
    }

    private fun extractImageUri(intent: Intent?): Uri? {
        if (intent?.action != Intent.ACTION_SEND) return null
        if (intent.type?.startsWith("image/") != true) return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        }
    }

    private fun finishSilently() {
        finish()
        if (Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }
}
