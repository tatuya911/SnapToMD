package io.github.tatuya911.snaptomd

import io.github.tatuya911.snaptomd.data.LocalExporter
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

/**
 * 保存ファイル名・プレースホルダ置換のテスト
 */
class LocalExporterTest {

    @Test
    fun sanitize_replacesInvalidChars() {
        assertEquals("a_b_c_d_e", LocalExporter.sanitizeFileName("a/b:c*d?e"))
        assertEquals("_x_", LocalExporter.sanitizeFileName("[x]"))
    }

    @Test
    fun sanitize_blankFallsBack() {
        assertEquals("商品メモ", LocalExporter.sanitizeFileName("  ..  "))
    }

    @Test
    fun sanitize_truncates() {
        assertEquals(80, LocalExporter.sanitizeFileName("あ".repeat(200)).length)
    }

    @Test
    fun imageName_isTimestamp() {
        val millis = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 25, 13, 27, 44) }.timeInMillis
        assertEquals("20261025_132744.jpg", LocalExporter.timestampImageName(millis))
    }

    @Test
    fun placeholder_replacedEverywhere() {
        assertEquals(
            "![[a.jpg]] a.jpg",
            LocalExporter.applyImagePlaceholder("![[{{image}}]] {{image}}", "a.jpg")
        )
    }

    @Test
    fun uniqueName_addsSequence() {
        val existing = setOf("t.md", "t (1).md")
        assertEquals("t (2).md", LocalExporter.uniqueName("t", "md") { it in existing })
        assertEquals("u.md", LocalExporter.uniqueName("u", "md") { it in existing })
    }
}
