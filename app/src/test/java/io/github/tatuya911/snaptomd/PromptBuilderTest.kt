package io.github.tatuya911.snaptomd

import io.github.tatuya911.snaptomd.data.ApiKeyManager
import io.github.tatuya911.snaptomd.worker.MarkdownWorker
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * プロンプト組み立て（固定指示文 + テンプレート）のテスト
 */
class PromptBuilderTest {

    @Test
    fun buildPrompt_includesInstructionAndCustomTemplate() {
        val prompt = MarkdownWorker.buildPrompt("# カスタム\n- 項目")
        assertTrue(prompt.startsWith(MarkdownWorker.FIXED_INSTRUCTION))
        assertTrue(prompt.endsWith("# カスタム\n- 項目"))
    }

    @Test
    fun buildPrompt_blankTemplate_fallsBackToDefault() {
        val prompt = MarkdownWorker.buildPrompt("   ")
        assertEquals(
            MarkdownWorker.FIXED_INSTRUCTION + "\n" + ApiKeyManager.DEFAULT_TEMPLATE,
            prompt
        )
    }
}
