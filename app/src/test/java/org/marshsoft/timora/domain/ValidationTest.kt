package org.marshsoft.timora.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidationTest {
    private val ok = timer(listOf(workRest()))

    @Test fun validTimerHasNoErrors() {
        assertTrue(ok.isValid())
        assertEquals(emptyList<ValidationError>(), ok.validate())
    }

    @Test fun blankName() {
        assertEquals(listOf(ValidationError.BlankName), ok.copy(name = "  ").validate())
    }

    @Test fun noBlocks() {
        assertEquals(listOf(ValidationError.NoBlocks), ok.copy(blocks = emptyList()).validate())
    }

    @Test fun emptyBlock() {
        val t = ok.copy(blocks = listOf(workRest(), Block(segments = emptyList())))
        assertEquals(listOf(ValidationError.EmptyBlock(1)), t.validate())
    }

    @Test fun cyclesBounds() {
        assertFalse(ok.copy(blocks = listOf(workRest(cycles = 0))).isValid())
        assertFalse(ok.copy(blocks = listOf(workRest(cycles = 1000))).isValid())
        assertTrue(ok.copy(blocks = listOf(workRest(cycles = 999))).isValid())
        assertEquals(
            listOf(ValidationError.InvalidCycles(0, 0)),
            ok.copy(blocks = listOf(workRest(cycles = 0))).validate(),
        )
    }

    @Test fun durationBounds() {
        assertEquals(
            listOf(ValidationError.InvalidDuration(0, 0, 0)),
            ok.copy(blocks = listOf(workRest(work = 0))).validate(),
        )
        assertFalse(ok.copy(blocks = listOf(workRest(rest = 86_401))).isValid())
        assertTrue(ok.copy(blocks = listOf(workRest(work = 1, rest = 86_400))).isValid())
    }

    @Test fun blankSegmentName() {
        val b = Block(segments = listOf(seg(" ", 5)))
        assertEquals(listOf(ValidationError.BlankSegmentName(0, 0)), ok.copy(blocks = listOf(b)).validate())
    }

    @Test fun prepBounds() {
        assertEquals(listOf(ValidationError.InvalidPrep(-1)), ok.copy(prepSeconds = -1).validate())
        assertFalse(ok.copy(prepSeconds = Limits.MAX_PREP_SECONDS + 1).isValid())
        assertTrue(ok.copy(prepSeconds = Limits.MAX_PREP_SECONDS).isValid())
    }

    @Test fun multipleErrorsAccumulate() {
        val t = Timer(name = "", blocks = emptyList())
        assertEquals(listOf(ValidationError.BlankName, ValidationError.NoBlocks), t.validate())
    }
}
