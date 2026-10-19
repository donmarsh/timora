package org.marshsoft.timora.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeLogicTest {
    @Test fun themeModeResolution() {
        assertTrue(ThemeMode.SYSTEM.isDark(true))
        assertFalse(ThemeMode.SYSTEM.isDark(false))
        assertFalse(ThemeMode.LIGHT.isDark(true))
        assertTrue(ThemeMode.DARK.isDark(false))
    }

    @Test fun accentIndexWraps() {
        assertEquals(8, CardAccentCount)
        assertEquals(0, accentIndex(0))
        assertEquals(7, accentIndex(7))
        assertEquals(0, accentIndex(8))
        assertEquals(3, accentIndex(19))
        assertEquals(7, accentIndex(-1))
        assertEquals(cardAccentColor(2), cardAccentColor(10))
    }

    @Test fun segmentLookupCoversAllTypesAndThemes() {
        for (dark in listOf(false, true)) {
            val all = SegmentTypeKey.entries.map { segmentColorsFor(it, dark) }
            assertEquals(4, all.map { it.accent }.toSet().size)
            all.forEach { assertNotEquals(it.container, it.onContainer) }
        }
        assertNotEquals(
            segmentColorsFor(SegmentTypeKey.WORK, false),
            segmentColorsFor(SegmentTypeKey.WORK, true),
        )
    }

    @Test fun segmentContainerTextContrastIsReadable() {
        for (dark in listOf(false, true)) for (t in SegmentTypeKey.entries) {
            val c = segmentColorsFor(t, dark)
            assertTrue("$t dark=$dark", contrast(c.container, c.onContainer) >= 4.5)
        }
    }

    private fun lum(c: androidx.compose.ui.graphics.Color): Double {
        fun ch(v: Float): Double = v.toDouble().let { if (it <= 0.03928) it / 12.92 else Math.pow((it + 0.055) / 1.055, 2.4) }
        return 0.2126 * ch(c.red) + 0.7152 * ch(c.green) + 0.0722 * ch(c.blue)
    }
    private fun contrast(a: androidx.compose.ui.graphics.Color, b: androidx.compose.ui.graphics.Color): Double {
        val (x, y) = lum(a) to lum(b)
        return (maxOf(x, y) + 0.05) / (minOf(x, y) + 0.05)
    }
}
