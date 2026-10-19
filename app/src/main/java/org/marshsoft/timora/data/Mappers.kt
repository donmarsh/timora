package org.marshsoft.timora.data

import org.marshsoft.timora.domain.Block
import org.marshsoft.timora.domain.Segment
import org.marshsoft.timora.domain.SegmentType
import org.marshsoft.timora.domain.Timer

fun TimerWithBlocks.toDomain(): Timer = Timer(
    id = timer.id,
    name = timer.name,
    blocks = blocks.sortedBy { it.block.position }.map { it.toDomain() },
    prepSeconds = timer.prepSeconds,
    createdAt = timer.createdAt,
    updatedAt = timer.updatedAt,
    colorIndex = timer.colorIndex,
)

fun BlockWithSegments.toDomain(): Block = Block(
    id = block.id,
    name = block.name,
    segments = segments.sortedBy { it.position }.map { it.toDomain() },
    cycles = block.cycles,
    skipLastRest = block.skipLastRest,
)

fun SegmentEntity.toDomain(): Segment = Segment(
    id = id,
    name = name,
    durationSeconds = durationSeconds,
    type = runCatching { SegmentType.valueOf(type) }.getOrDefault(SegmentType.WORK),
)

fun Timer.toEntity(): TimerEntity = TimerEntity(id, name, prepSeconds, createdAt, updatedAt, colorIndex)

fun Block.toEntity(timerId: Long, position: Int): BlockEntity =
    BlockEntity(timerId = timerId, position = position, name = name, cycles = cycles, skipLastRest = skipLastRest)

fun Segment.toEntity(blockId: Long, position: Int): SegmentEntity =
    SegmentEntity(blockId = blockId, position = position, name = name, durationSeconds = durationSeconds, type = type.name)
