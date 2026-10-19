package org.marshsoft.timora.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [TimerEntity::class, BlockEntity::class, SegmentEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class TimoraDatabase : RoomDatabase() {
    abstract fun timerDao(): TimerDao

    companion object {
        const val NAME = "timora.db"
        fun create(context: Context): TimoraDatabase =
            Room.databaseBuilder(context, TimoraDatabase::class.java, NAME).build()
    }
}
