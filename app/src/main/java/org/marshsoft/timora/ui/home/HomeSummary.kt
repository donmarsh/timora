package org.marshsoft.timora.ui.home

import org.marshsoft.timora.domain.SegmentType
import org.marshsoft.timora.domain.Timer

/** One piece of the compact "Work 40s · Rest 20s ×8" summary shown on a timer card. */
sealed interface SummaryItem {
    data class SegmentChip(val type: SegmentType, val label: String) : SummaryItem
    data class Repeat(val text: String) : SummaryItem
}

/** 40 -> "40s", 90 -> "1m 30s", 120 -> "2m", 3900 -> "1h 5m". */
fun compactDuration(totalSeconds: Int): String {
    val s = totalSeconds.coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return buildList {
        if (h > 0) add("${h}h")
        if (m > 0) add("${m}m")
        if (sec > 0 || (h == 0 && m == 0)) add("${sec}s")
    }.let { parts ->
        // Hours + minutes only: drop zero seconds; otherwise keep everything.
        parts.joinToString(" ")
    }
}

/** Flattens the timer's blocks into chips and repeat markers, e.g. [Work 40s] [Rest 20s] [×8]. */
fun summarize(timer: Timer): List<SummaryItem> = buildList {
    timer.blocks.forEach { block ->
        block.segments.forEach { seg ->
            add(SummaryItem.SegmentChip(seg.type, "${seg.name} ${compactDuration(seg.durationSeconds)}"))
        }
        if (block.cycles > 1) add(SummaryItem.Repeat("×${block.cycles}"))
    }
}
