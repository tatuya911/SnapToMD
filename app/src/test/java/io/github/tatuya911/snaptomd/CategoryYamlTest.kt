package io.github.tatuya911.snaptomd

import io.github.tatuya911.snaptomd.data.Category
import io.github.tatuya911.snaptomd.data.CategoryYaml
import io.github.tatuya911.snaptomd.data.ConflictAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryYamlTest {

    private val defaultTemplate = "# Default\n- key: val"

    @Test
    fun testExportAndImportRoundTrip() {
        val original = listOf(
            Category(
                name = "映画",
                template = "# [作品名]\n![[{{image}}]]\n- 公開年: 2026\n- 監督: 山田",
                hint = "映画やアニメのポスター"
            ),
            Category(
                name = "書籍",
                template = "# [書名]\n- 著者:\n- 出版社:",
                hint = "本・雑誌の表紙"
            ),
            Category(
                name = Category.OTHER_NAME,
                template = "# その他テンプレート\n内容",
                hint = ""
            )
        )

        val yamlString = CategoryYaml.export(original)
        assertTrue(yamlString.contains("name: \"映画\""))
        assertTrue(yamlString.contains("template: |"))
        assertTrue(yamlString.contains("![[{{image}}]]"))

        val importResult = CategoryYaml.importYaml(yamlString, defaultTemplate)
        assertTrue(importResult.isSuccess)
        val imported = importResult.getOrThrow()

        assertEquals(3, imported.size)
        assertEquals("映画", imported[0].name)
        assertEquals("映画やアニメのポスター", imported[0].hint)
        assertEquals("# [作品名]\n![[{{image}}]]\n- 公開年: 2026\n- 監督: 山田\n", imported[0].template)
        assertEquals("書籍", imported[1].name)
        assertEquals("その他", imported[2].name)
    }

    @Test
    fun testHandWrittenYamlImport() {
        val yaml = """
            version: 1
            categories:
              - name: 家電
                hint: 家電製品や型番
                template: |
                  # [製品名]
                  - 型番:
              - name: レシート
                template: "# レシート"
        """.trimIndent()

        val result = CategoryYaml.importYaml(yaml, defaultTemplate)
        assertTrue(result.isSuccess)
        val list = result.getOrThrow()
        assertEquals(2, list.size)
        assertEquals("家電", list[0].name)
        assertEquals("家電製品や型番", list[0].hint)
        assertTrue(list[0].template.contains("# [製品名]"))
        assertEquals("レシート", list[1].name)
        assertEquals("", list[1].hint)
        assertEquals("# レシート", list[1].template)
    }

    @Test
    fun testImportValidationErrors() {
        // 空文字
        val emptyRes = CategoryYaml.importYaml("", defaultTemplate)
        assertTrue(emptyRes.isFailure)

        // 構文エラー
        val invalidYaml = "categories: [unclosed"
        val syntaxRes = CategoryYaml.importYaml(invalidYaml, defaultTemplate)
        assertTrue(syntaxRes.isFailure)

        // categories リストが無い
        val noCategories = "version: 1\nother: test"
        val noCatRes = CategoryYaml.importYaml(noCategories, defaultTemplate)
        assertTrue(noCatRes.isFailure)

        // name が空
        val missingName = """
            categories:
              - hint: テスト
                template: "# テスト"
        """.trimIndent()
        val missingNameRes = CategoryYaml.importYaml(missingName, defaultTemplate)
        assertTrue(missingNameRes.isFailure)
    }

    @Test
    fun testFindConflicts() {
        val current = listOf(
            Category("映画", "T1"),
            Category("本", "T2"),
            Category(Category.OTHER_NAME, "T_OTHER")
        )
        val imported = listOf(
            Category("映画", "T1_new"),
            Category("料理", "T3"),
            Category(Category.OTHER_NAME, "T_OTHER_new")
        )

        val conflicts = CategoryYaml.findConflicts(current, imported)
        assertEquals(2, conflicts.size)
        assertEquals("映画", conflicts[0].name)
        assertEquals(Category.OTHER_NAME, conflicts[1].name)
    }

    @Test
    fun testMergeOverwriteAndSkip() {
        val current = listOf(
            Category("映画", "旧映画テンプレート", "旧ヒント"),
            Category("本", "旧本テンプレート"),
            Category(Category.OTHER_NAME, "旧その他テンプレート")
        )

        val imported = listOf(
            Category("映画", "新映画テンプレート", "新ヒント"),
            Category("本", "新本テンプレート", "新ヒント"),
            Category("料理", "新料理テンプレート", "新ヒント"),
            Category(Category.OTHER_NAME, "新その他テンプレート")
        )

        // 映画は OVERWRITE、本とその他は SKIP
        val resolutions = mapOf(
            "映画" to ConflictAction.OVERWRITE,
            "本" to ConflictAction.SKIP,
            Category.OTHER_NAME to ConflictAction.SKIP
        )

        val merged = CategoryYaml.merge(current, imported, resolutions, defaultTemplate)

        assertEquals(4, merged.size)
        // 映画は上書きされた
        assertEquals("映画", merged[0].name)
        assertEquals("新映画テンプレート", merged[0].template)
        assertEquals("新ヒント", merged[0].hint)

        // 本はスキップ（旧版維持）
        assertEquals("本", merged[1].name)
        assertEquals("旧本テンプレート", merged[1].template)

        // 料理は新規追加（その他手前に挿入）
        assertEquals("料理", merged[2].name)
        assertEquals("新料理テンプレート", merged[2].template)

        // その他は末尾に維持
        assertEquals(Category.OTHER_NAME, merged[3].name)
        assertEquals("旧その他テンプレート", merged[3].template)
    }

    @Test
    fun testMergeOverwriteOtherCategory() {
        val current = listOf(
            Category("映画", "映画"),
            Category(Category.OTHER_NAME, "旧その他")
        )
        val imported = listOf(
            Category(Category.OTHER_NAME, "新その他")
        )
        val resolutions = mapOf(
            Category.OTHER_NAME to ConflictAction.OVERWRITE
        )

        val merged = CategoryYaml.merge(current, imported, resolutions, defaultTemplate)
        assertEquals(2, merged.size)
        assertEquals(Category.OTHER_NAME, merged[1].name)
        assertEquals("新その他", merged[1].template)
    }
}
