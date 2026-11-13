package io.github.tatuya911.snaptomd

import io.github.tatuya911.snaptomd.data.Category
import io.github.tatuya911.snaptomd.worker.MarkdownWorker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * カテゴリのシリアライズ・正規化・分類ロジックのテスト
 */
class CategoryTest {

    private val categories = listOf(
        Category("家電", "T-家電", "家電製品・ガジェット"),
        Category("映画", "T-映画"),
        Category(Category.OTHER_NAME, "T-その他")
    )

    @Test
    fun resolveCategory_exactMatch() {
        assertEquals("映画", MarkdownWorker.resolveCategory("映画", categories).name)
    }

    @Test
    fun resolveCategory_trimsWhitespaceAndBrackets() {
        assertEquals("家電", MarkdownWorker.resolveCategory("  「家電」\n", categories).name)
    }

    @Test
    fun resolveCategory_partialMatch() {
        assertEquals("映画", MarkdownWorker.resolveCategory("カテゴリは映画です", categories).name)
    }

    @Test
    fun resolveCategory_unknownOrBlank_fallsBackToOther() {
        assertTrue(MarkdownWorker.resolveCategory("食べ物", categories).isOther)
        assertTrue(MarkdownWorker.resolveCategory("", categories).isOther)
    }

    @Test
    fun buildClassificationPrompt_includesNamesHintsAndOther() {
        val prompt = MarkdownWorker.buildClassificationPrompt(categories)
        assertTrue(prompt.contains("- 家電: 家電製品・ガジェット"))
        assertTrue(prompt.contains("- 映画\n"))
        assertTrue(prompt.endsWith("- その他"))
    }

    @Test
    fun normalize_addsOtherAndRemovesDuplicatesAndBlank() {
        val result = Category.normalize(
            listOf(Category("A", "a"), Category("A", "dup"), Category(" ", "x")),
            "default"
        )
        assertEquals(listOf("A", Category.OTHER_NAME), result.map { it.name })
        assertEquals("default", result.last().template)
    }

    @Test
    fun normalize_keepsOtherAtEnd() {
        val result = Category.normalize(
            listOf(Category(Category.OTHER_NAME, "o"), Category("B", "b")),
            "default"
        )
        assertEquals(listOf("B", Category.OTHER_NAME), result.map { it.name })
        assertEquals("o", result.last().template)
    }

    @Test
    fun json_roundTrip_andMissingHint() {
        val restored = Category.fromJson(Category.toJson(categories))
        assertEquals(categories, restored)
        val noHint = Category.fromJson("""[{"name":"X","template":"t"}]""")
        assertEquals("", noHint[0].hint)
    }
}
