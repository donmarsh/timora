package org.marshsoft.timora.domain

/**
 * One step of a flattened timer. Prep step has blockIndex = -1, cycleIndex = 0, totalCycles = 0
 * (see [isPrep]). blockIndex is 0-based, cycleIndex is 1-based.
 */
data class PlannedStep(
    val segment: Segment,
    val blockIndex: Int,
    val cycleIndex: Int,
    val totalCycles: Int,
    val stepIndex: Int,
    val startOffsetMs: Long,
    val totalBlocks: Int,
    /** Name of the owning block, or null if unnamed (always null for the prep step). */
    val blockName: String?,
) {
    val durationMs: Long get() = segment.durationSeconds * 1000L
    val endOffsetMs: Long get() = startOffsetMs + durationMs
    val isPrep: Boolean get() = blockIndex < 0
}

class TimerPlan(val steps: List<PlannedStep>) {
    val totalDurationMs: Long = steps.lastOrNull()?.endOffsetMs ?: 0L
    val totalDurationSeconds: Int = (totalDurationMs / 1000).toInt()

    companion object {
        const val PREP_SEGMENT_ID = -1L
        const val PREP_NAME = "Get ready"

        fun from(timer: Timer): TimerPlan {
            val raw = mutableListOf<Triple<Segment, Pair<Int, Int>, Int>>() // segment, (block, cycle), totalCycles
            if (timer.prepSeconds > 0) {
                raw += Triple(
                    Segment(PREP_SEGMENT_ID, PREP_NAME, timer.prepSeconds, SegmentType.PREP),
                    -1 to 0,
                    0,
                )
            }
            timer.blocks.forEachIndexed { bi, block ->
                for (cycle in 1..block.cycles) {
                    block.segments.forEachIndexed { si, seg ->
                        val isLast = cycle == block.cycles && si == block.segments.lastIndex
                        if (isLast && block.skipLastRest && seg.type == SegmentType.REST) return@forEachIndexed
                        raw += Triple(seg, bi to cycle, block.cycles)
                    }
                }
            }
            var offset = 0L
            val steps = raw.mapIndexed { i, (seg, pos, total) ->
                val blockName = timer.blocks.getOrNull(pos.first)?.name?.takeIf { it.isNotBlank() }
                PlannedStep(seg, pos.first, pos.second, total, i, offset, timer.blocks.size, blockName).also {
                    offset += it.durationMs
                }
            }
            return TimerPlan(steps)
        }
    }
}
