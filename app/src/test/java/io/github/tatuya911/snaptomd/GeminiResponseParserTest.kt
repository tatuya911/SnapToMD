package io.github.tatuya911.snaptomd

import io.github.tatuya911.snaptomd.worker.GeminiResponseParser
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

/**
 * Gemini APIレスポンス解析のテスト
 */
class GeminiResponseParserTest {

    @Test
    fun parseText_concatenatesPartsAndSkipsThought() {
        val json = """{"candidates":[{"content":{"parts":[
            {"text":"考え中","thought":true},
            {"text":"# タイトル\n"},
            {"text":"本文"}]}}]}"""
        assertEquals("# タイトル\n本文", GeminiResponseParser.parseText(json))
    }

    @Test
    fun parseText_blocked_throws() {
        val json = """{"promptFeedback":{"blockReason":"SAFETY"}}"""
        try {
            GeminiResponseParser.parseText(json)
            fail("例外が発生するはず")
        } catch (e: IllegalStateException) {
            assertEquals(true, e.message!!.contains("SAFETY"))
        }
    }

    @Test
    fun parseText_emptyCandidates_throws() {
        try {
            GeminiResponseParser.parseText("""{"candidates":[]}""")
            fail("例外が発生するはず")
        } catch (_: IllegalStateException) {
        }
    }

    @Test
    fun parseError_extractsStatusAndMessage() {
        val body = """{"error":{"code":403,"message":"API disabled","status":"PERMISSION_DENIED"}}"""
        val e = GeminiResponseParser.parseError(403, body)
        assertEquals(403, e.httpCode)
        assertEquals("PERMISSION_DENIED", e.status)
        assertEquals("API disabled", e.message)
    }

    @Test
    fun parseError_nonJson_usesRawBody() {
        val e = GeminiResponseParser.parseError(500, "oops")
        assertEquals("oops", e.message)
    }

    @Test
    fun parseDecisionChoice_extractsChoice() {
        val json = """{
            "answers": {
                "category": {
                    "type": "choice",
                    "choice": "cat_0",
                    "confidence": 0.95,
                    "probabilities": {"cat_0": 0.95, "other": 0.05}
                }
            },
            "usage": {"total_input_tokens": 280, "total_output_tokens": 0}
        }"""
        assertEquals("cat_0", GeminiResponseParser.parseDecisionChoice(json))
    }

    @Test
    fun parseDecisionChoice_missingAnswers_throws() {
        try {
            GeminiResponseParser.parseDecisionChoice("""{}""")
            fail("例外が発生するはず")
        } catch (_: IllegalStateException) {
        }
    }

    @Test
    fun parseDecisionChoice_missingQuestion_throws() {
        try {
            GeminiResponseParser.parseDecisionChoice("""{"answers":{}}""")
            fail("例外が発生するはず")
        } catch (_: IllegalStateException) {
        }
    }

    @Test
    fun parseDecisionChoice_emptyChoice_throws() {
        try {
            GeminiResponseParser.parseDecisionChoice("""{"answers":{"category":{"type":"choice","choice":""}}}""")
            fail("例外が発生するはず")
        } catch (_: IllegalStateException) {
        }
    }
}
