package org.marshsoft.timora.domain

object Limits {
    const val MIN_DURATION_SECONDS = 1
    const val MAX_DURATION_SECONDS = 86_400
    const val MIN_CYCLES = 1
    const val MAX_CYCLES = 999
    const val MAX_PREP_SECONDS = 3_600
}

sealed interface ValidationError {
    data object BlankName : ValidationError
    data object NoBlocks : ValidationError
    data class InvalidPrep(val seconds: Int) : ValidationError
    data class EmptyBlock(val blockIndex: Int) : ValidationError
    data class InvalidCycles(val blockIndex: Int, val cycles: Int) : ValidationError
    data class InvalidDuration(val blockIndex: Int, val segmentIndex: Int, val seconds: Int) : ValidationError
    data class BlankSegmentName(val blockIndex: Int, val segmentIndex: Int) : ValidationError
}

/** Returns all validation errors; empty list means valid. */
fun Timer.validate(): List<ValidationError> {
    val errors = mutableListOf<ValidationError>()
    if (name.isBlank()) errors += ValidationError.BlankName
    if (prepSeconds !in 0..Limits.MAX_PREP_SECONDS) errors += ValidationError.InvalidPrep(prepSeconds)
    if (blocks.isEmpty()) errors += ValidationError.NoBlocks
    blocks.forEachIndexed { bi, block ->
        if (block.segments.isEmpty()) errors += ValidationError.EmptyBlock(bi)
        if (block.cycles !in Limits.MIN_CYCLES..Limits.MAX_CYCLES) {
            errors += ValidationError.InvalidCycles(bi, block.cycles)
        }
        block.segments.forEachIndexed { si, seg ->
            if (seg.durationSeconds !in Limits.MIN_DURATION_SECONDS..Limits.MAX_DURATION_SECONDS) {
                errors += ValidationError.InvalidDuration(bi, si, seg.durationSeconds)
            }
            if (seg.name.isBlank()) errors += ValidationError.BlankSegmentName(bi, si)
        }
    }
    return errors
}

fun Timer.isValid(): Boolean = validate().isEmpty()
