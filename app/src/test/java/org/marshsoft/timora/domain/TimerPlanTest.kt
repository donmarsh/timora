package org.marshsoft.timora.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimerPlanTest {
    @Test fun flattensHiitExample() {
        val t = timer(
            listOf(
                Block(segments = listOf(seg("Warm-up", 60))),
                workRest(40, 20, cycles = 8),
                Block(segments = listOf(seg("Cool", 120, SegmentType.COOLDOWN))),
            ),
        )
        val plan = TimerPlan.from(t)
        assertEquals(1 + 16 + 1, plan.steps.size)
        assertEquals(60 + 8 * 60 + 120, plan.totalDurationSeconds)
        assertEquals((60 + 480 + 120) * 1000L, plan.totalDurationMs)
        assertEquals(plan.steps.indices.toList(), plan.steps.map { it.stepIndex })
        val s = plan.steps[3] // block1 cycle 2 work
        assertEquals(1, s.blockIndex)
        assertEquals(2, s.cycleIndex)
        assertEquals(8, s.totalCycles)
        assertEquals(3, s.totalBlocks)
        assertEquals(120_000L, s.startOffsetMs)
    }

    @Test fun offsetsAreContiguous() {
        val plan = TimerPlan.from(timer(listOf(workRest(3, 2, 3)), prep = 5))
        var expected = 0L
        plan.steps.forEach {
            assertEquals(expected, it.startOffsetMs)
            expected += it.durationMs
            assertEquals(expected, it.endOffsetMs)
        }
        assertEquals(expected, plan.totalDurationMs)
    }

    @Test fun prepStepIncludedFirst() {
        val plan = TimerPlan.from(timer(listOf(workRest(cycles = 1)), prep = 10))
        val p = plan.steps.first()
        assertTrue(p.isPrep)
        assertEquals(SegmentType.PREP, p.segment.type)
        assertEquals(10_000L, p.durationMs)
        assertEquals(0, p.cycleIndex)
        assertEquals(3, plan.steps.size)
        assertFalse(plan.steps[1].isPrep)
    }

    @Test fun noPrepWhenZero() {
        val plan = TimerPlan.from(timer(listOf(workRest(cycles = 1)), prep = 0))
        assertEquals(2, plan.steps.size)
        assertFalse(plan.steps.any { it.isPrep })
    }

    @Test fun skipLastRestOmitsOnlyFinalRest() {
        val plan = TimerPlan.from(timer(listOf(workRest(10, 5, cycles = 3, skip = true))))
        assertEquals(5, plan.steps.size)
        assertEquals(SegmentType.WORK, plan.steps.last().segment.type)
        assertEquals(3 * 10 + 2 * 5, plan.totalDurationSeconds)
    }

    @Test fun skipLastRestIgnoredWhenLastIsNotRest() {
        val b = Block(segments = listOf(seg("a", 5, SegmentType.REST), seg("b", 7)), cycles = 2, skipLastRest = true)
        assertEquals(4, TimerPlan.from(timer(listOf(b))).steps.size)
    }

    @Test fun skipLastRestSingleCycle() {
        val plan = TimerPlan.from(timer(listOf(workRest(10, 5, cycles = 1, skip = true))))
        assertEquals(1, plan.steps.size)
    }

    @Test fun skipLastRestOnlyRestSegmentYieldsNothing() {
        val b = Block(segments = listOf(seg("r", 5, SegmentType.REST)), skipLastRest = true)
        assertEquals(0, TimerPlan.from(timer(listOf(b))).steps.size)
    }

    @Test fun skipLastRestPerBlockOnly() {
        val plan = TimerPlan.from(timer(listOf(workRest(10, 5, 1, skip = true), workRest(10, 5, 1, skip = false))))
        assertEquals(3, plan.steps.size)
        assertEquals(SegmentType.REST, plan.steps.last().segment.type)
    }

    @Test fun emptyTimerEmptyPlan() {
        val plan = TimerPlan.from(timer(emptyList()))
        assertTrue(plan.steps.isEmpty())
        assertEquals(0L, plan.totalDurationMs)
    }

    @Test fun totalDurationExtension() {
        assertEquals(10 + 2 * 60, timer(listOf(workRest(40, 20, 2)), prep = 10).totalDurationSeconds())
    }

    @Test fun cycleIndexIsOneBased() {
        val plan = TimerPlan.from(timer(listOf(workRest(cycles = 3))))
        assertEquals(listOf(1, 1, 2, 2, 3, 3), plan.steps.map { it.cycleIndex })
    }

    @Test fun blockNameIsCopiedToStepsAndNullForPrepAndBlank() {
        val named = workRest(cycles = 1).copy(name = "Tabata")
        val blank = Block(segments = listOf(seg("Solo", 10)), name = "  ")
        val plan = TimerPlan.from(timer(listOf(named, blank), prep = 5))
        assertEquals(listOf(null, "Tabata", "Tabata", null), plan.steps.map { it.blockName })
    }
}
