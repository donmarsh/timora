package org.marshsoft.timora.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import org.marshsoft.timora.domain.Timer

@Dao
abstract class TimerDao {
    @Transaction
    @Query("SELECT * FROM timers ORDER BY updatedAt DESC, id DESC")
    abstract fun observeAll(): Flow<List<TimerWithBlocks>>

    @Transaction
    @Query("SELECT * FROM timers WHERE id = :id")
    abstract fun observe(id: Long): Flow<TimerWithBlocks?>

    @Transaction
    @Query("SELECT * FROM timers WHERE id = :id")
    abstract suspend fun get(id: Long): TimerWithBlocks?

    @Query("SELECT COUNT(*) FROM timers")
    abstract suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertTimer(timer: TimerEntity): Long

    @Update
    protected abstract suspend fun updateTimer(timer: TimerEntity)

    @Insert
    protected abstract suspend fun insertBlock(block: BlockEntity): Long

    @Insert
    protected abstract suspend fun insertSegments(segments: List<SegmentEntity>)

    @Query("DELETE FROM blocks WHERE timerId = :timerId")
    protected abstract suspend fun deleteBlocksOf(timerId: Long)

    @Query("DELETE FROM timers WHERE id = :id")
    abstract suspend fun delete(id: Long)

    /** Inserts (id == 0 or unknown id) or fully replaces the timer's children. Returns the timer id. */
    @Transaction
    open suspend fun upsert(timer: Timer): Long {
        val exists = timer.id != 0L && get(timer.id) != null
        val timerId = if (exists) {
            updateTimer(timer.toEntity())
            deleteBlocksOf(timer.id)
            timer.id
        } else {
            insertTimer(timer.toEntity().copy(id = 0))
        }
        timer.blocks.forEachIndexed { bi, block ->
            val blockId = insertBlock(block.toEntity(timerId, bi))
            insertSegments(block.segments.mapIndexed { si, seg -> seg.toEntity(blockId, si) })
        }
        return timerId
    }

    /** Inserts all timers in one transaction (all or nothing). */
    @Transaction
    open suspend fun insertAll(timers: List<Timer>) {
        timers.forEach { upsert(it.copy(id = 0)) }
    }
}
