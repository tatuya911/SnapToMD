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
    fun buildDecisionCriteria_mapsNormalCategoriesAndOther() {
        val criteria = MarkdownWorker.buildDecisionCriteria(categories)
        assertEquals(listOf("cat_0", "cat_1", "other"), criteria.keys.toList())
        assertEquals("家電", criteria["cat_0"]?.first?.name)
        assertEquals("家電: 家電製品・ガジェット", criteria["cat_0"]?.second)
        assertEquals("映画", criteria["cat_1"]?.first?.name)
        assertEquals("映画", criteria["cat_1"]?.second)
        assertEquals(Category.OTHER_NAME, criteria["other"]?.first?.name)
    }

    @Test
    fun buildDecisionRequestJson_containsModelLowResImageAndChoiceCriteria() {
        val criteria = MarkdownWorker.buildDecisionCriteria(categories)
        val json = MarkdownWorker.buildDecisionRequestJson("BASE64_DATA", criteria)

        assertEquals(MarkdownWorker.DECISION_MODEL, json.getString("model"))
        val input = json.getJSONArray("input")
        assertEquals(1, input.length())
        val imageBlock = input.getJSONObject(0)
        assertEquals("image", imageBlock.getString("type"))
        assertEquals("BASE64_DATA", imageBlock.getString("data"))
        assertEquals("image/jpeg", imageBlock.getString("mime_type"))
        assertEquals("low", imageBlock.getString("resolution"))

        val categoryQ = json.getJSONObject("questions").getJSONObject("category")
        assertEquals("choice", categoryQ.getString("type"))
        val criteriaJson = categoryQ.getJSONObject("criteria")
        assertEquals("家電: 家電製品・ガジェット", criteriaJson.getString("cat_0"))
        assertEquals("映画", criteriaJson.getString("cat_1"))
        assertTrue(criteriaJson.has("other"))
    }

    @Test
    fun resolveDecisionCategory_resolvesKnownKeyAndFallsBackToOther() {
        val criteria = MarkdownWorker.buildDecisionCriteria(categories)
        assertEquals("家電", MarkdownWorker.resolveDecisionCategory("cat_0", criteria, categories).name)
        assertEquals("映画", MarkdownWorker.resolveDecisionCategory(" cat_1 ", criteria, categories).name)
        assertTrue(MarkdownWorker.resolveDecisionCategory("other", criteria, categories).isOther)
        assertTrue(MarkdownWorker.resolveDecisionCategory("unknown_key", criteria, categories).isOther)
        assertTrue(MarkdownWorker.resolveDecisionCategory("", criteria, categories).isOther)
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
