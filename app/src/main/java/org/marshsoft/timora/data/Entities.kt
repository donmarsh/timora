package org.marshsoft.timora.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "timers")
data class TimerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val prepSeconds: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val colorIndex: Int,
)

@Entity(
    tableName = "blocks",
    foreignKeys = [ForeignKey(TimerEntity::class, ["id"], ["timerId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("timerId")],
)
data class BlockEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timerId: Long,
    val position: Int,
    val name: String?,
    val cycles: Int,
    val skipLastRest: Boolean,
)

@Entity(
    tableName = "segments",
    foreignKeys = [ForeignKey(BlockEntity::class, ["id"], ["blockId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("blockId")],
)
data class SegmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val blockId: Long,
    val position: Int,
    val name: String,
    val durationSeconds: Int,
    val type: String,
)

data class BlockWithSegments(
    @Embedded val block: BlockEntity,
    @Relation(parentColumn = "id", entityColumn = "blockId") val segments: List<SegmentEntity>,
)

data class TimerWithBlocks(
    @Embedded val timer: TimerEntity,
    @Relation(entity = BlockEntity::class, parentColumn = "id", entityColumn = "timerId")
    val blocks: List<BlockWithSegments>,
)
