package io.github.tatuya911.snaptomd.worker

import org.json.JSONObject

/**
 * Gemini API のHTTPエラーを表す例外
 */
class GeminiApiException(
    val httpCode: Int,
    val status: String,
    message: String
) : Exception(message)

/**
 * Gemini API のレスポンスJSONを解析する（Android依存なし）
 */
object GeminiResponseParser {

    /**
     * 正常応答からテキストを抽出する。thought パートは除外し、全パートを連結する。
     * ブロックされた場合や空応答の場合は IllegalStateException を投げる。
     */
    fun parseText(responseText: String): String {
        val json = JSONObject(responseText)

        val blockReason = json.optJSONObject("promptFeedback")?.optString("blockReason", "") ?: ""
        if (blockReason.isNotBlank()) {
            throw IllegalStateException("リクエストが安全性ポリシーによりブロックされました ($blockReason)。")
        }

        val candidates = json.optJSONArray("candidates")
        if (candidates == null || candidates.length() == 0) {
            throw IllegalStateException("APIからの応答テキストが空でした。")
        }
        val candidate = candidates.getJSONObject(0)
        val parts = candidate.optJSONObject("content")?.optJSONArray("parts")

        val builder = StringBuilder()
        if (parts != null) {
            for (i in 0 until parts.length()) {
                val part = parts.getJSONObject(i)
                if (part.optBoolean("thought", false)) continue
                builder.append(part.optString("text", ""))
            }
        }

        val text = builder.toString()
        if (text.isBlank()) {
            val finishReason = candidate.optString("finishReason", "")
            throw IllegalStateException(
                "APIからの応答テキストが空でした。" +
                    if (finishReason.isNotBlank()) " (finishReason: $finishReason)" else ""
            )
        }
        return text
    }

    /**
     * エラー応答のJSONから GeminiApiException を生成する
     */
    fun parseError(httpCode: Int, body: String): GeminiApiException {
        var status = ""
        var message = body
        try {
            val error = JSONObject(body).optJSONObject("error")
            if (error != null) {
                status = error.optString("status", "")
                message = error.optString("message", body)
            }
        } catch (_: Exception) {
            // JSONでない場合は本文をそのまま使用
        }
        return GeminiApiException(httpCode, status, message)
    }
}
