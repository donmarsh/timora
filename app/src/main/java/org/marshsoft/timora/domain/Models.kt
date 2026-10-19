package org.marshsoft.timora.domain

enum class SegmentType { WORK, REST, PREP, COOLDOWN }

data class Segment(
    val id: Long = 0,
    val name: String,
    val durationSeconds: Int,
    val type: SegmentType,
)

data class Block(
    val id: Long = 0,
    val name: String? = null,
    val segments: List<Segment>,
    val cycles: Int = 1,
    val skipLastRest: Boolean = false,
)

data class Timer(
    val id: Long = 0,
    val name: String,
    val blocks: List<Block>,
    val prepSeconds: Int = 0,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val colorIndex: Int = 0,
)

/** Total duration in seconds of the timer including prep, honouring skipLastRest. */
fun Timer.totalDurationSeconds(): Int = TimerPlan.from(this).totalDurationSeconds
