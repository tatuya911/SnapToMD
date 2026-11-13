package io.github.tatuya911.snaptomd

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Markdown解析やタイトル抽出処理のテスト
 */
class MarkdownExtractionTest {

    @Test
    fun extractTitle_withValidH1Header_returnsHeaderTitle() {
        val markdown = """
            # ソニー ワイヤレスヘッドホン WH-1000XM5
            
            ## 概要
            - 価格: 49,500円
        """.trimIndent()

        val title = markdown.lines().firstOrNull { it.trim().startsWith("# ") }
            ?.trim()?.removePrefix("#")?.trim()

        assertEquals("ソニー ワイヤレスヘッドホン WH-1000XM5", title)
    }

    @Test
    fun extractTitle_withoutHeader_returnsFirstLine() {
        val markdown = """
            商品メモ: モバイルバッテリー
            
            Ankerの急速充電器
        """.trimIndent()

        val header = markdown.lines().firstOrNull { it.trim().startsWith("# ") }
            ?.trim()?.removePrefix("#")?.trim()

        val title = header ?: markdown.lines().firstOrNull { it.isNotBlank() }?.take(30)

        assertEquals("商品メモ: モバイルバッテリー", title)
    }
}
