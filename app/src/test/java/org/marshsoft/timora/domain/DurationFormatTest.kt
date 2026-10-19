package org.marshsoft.timora.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class DurationFormatTest {
    @Test fun formats() {
        assertEquals("0:00", formatDuration(0))
        assertEquals("0:05", formatDuration(5))
        assertEquals("1:30", formatDuration(90))
        assertEquals("59:59", formatDuration(3599))
        assertEquals("1:00:00", formatDuration(3600))
        assertEquals("1:01:40", formatDuration(3700))
        assertEquals("24:00:00", formatDuration(86_400))
    }

    @Test fun negativeClamps() = assertEquals("0:00", formatDuration(-4))

    @Test fun remainingRoundsUp() {
        assertEquals("0:00", formatRemainingMs(0))
        assertEquals("0:01", formatRemainingMs(1))
        assertEquals("0:01", formatRemainingMs(1000))
        assertEquals("0:02", formatRemainingMs(1001))
        assertEquals("0:00", formatRemainingMs(-50))
    }
}
