package io.github.tatuya911.snaptomd.worker

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 設定したAPIキー・モデルで疎通できるかを確認する（テキストのみの軽量リクエスト）
 */
object GeminiConnectionTester {

    /** 成功時は null、失敗時は日本語のエラーメッセージを返す */
    suspend fun test(apiKey: String, modelName: String): String? =
        withContext(Dispatchers.IO) {
            try {
                val model = modelName.trim().removePrefix("models/")
                val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"
                val body = JSONObject().put(
                    "contents",
                    JSONArray().put(
                        JSONObject().put("role", "user").put(
                            "parts", JSONArray().put(JSONObject().put("text", "Reply with: OK"))
                        )
                    )
                )
                val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("x-goog-api-key", apiKey)
                    doOutput = true
                    connectTimeout = 15000
                    readTimeout = 30000
                }
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                if (connection.responseCode == 200) {
                    null
                } else {
                    val err = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                    val e = GeminiResponseParser.parseError(connection.responseCode, err)
                    "HTTP ${e.httpCode} ${e.status}: ${e.message}"
                }
            } catch (e: Exception) {
                "通信エラー: ${e.localizedMessage}"
            }
        }
}
