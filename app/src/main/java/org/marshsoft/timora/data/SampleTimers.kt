package org.marshsoft.timora.data

import org.marshsoft.timora.domain.Block
import org.marshsoft.timora.domain.Segment
import org.marshsoft.timora.domain.SegmentType
import org.marshsoft.timora.domain.Timer

object SampleTimers {
    fun tabata(now: Long): Timer = Timer(
        name = "HIIT 20/10 Tabata",
        prepSeconds = 10,
        colorIndex = 0,
        createdAt = now,
        updatedAt = now,
        blocks = listOf(
            Block(
                name = "Tabata",
                cycles = 8,
                skipLastRest = true,
                segments = listOf(
                    Segment(name = "Work", durationSeconds = 20, type = SegmentType.WORK),
                    Segment(name = "Rest", durationSeconds = 10, type = SegmentType.REST),
                ),
            ),
            Block(
                name = "Cool-down",
                segments = listOf(Segment(name = "Cool-down", durationSeconds = 60, type = SegmentType.COOLDOWN)),
            ),
        ),
    )

    fun pomodoro(now: Long): Timer = Timer(
        name = "Pomodoro",
        prepSeconds = 0,
        colorIndex = 1,
        createdAt = now - 1,
        updatedAt = now - 1,
        blocks = listOf(
            Block(
                name = "Focus",
                cycles = 4,
                skipLastRest = true,
                segments = listOf(
                    Segment(name = "Focus", durationSeconds = 25 * 60, type = SegmentType.WORK),
                    Segment(name = "Short break", durationSeconds = 5 * 60, type = SegmentType.REST),
                ),
            ),
            Block(
                name = "Long break",
                segments = listOf(Segment(name = "Long break", durationSeconds = 15 * 60, type = SegmentType.COOLDOWN)),
            ),
        ),
    )

    fun all(now: Long): List<Timer> = listOf(pomodoro(now), tabata(now))
}
