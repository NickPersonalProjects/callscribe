package com.nicholaston.callscribe.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        CallRecord::class,
        TranscriptSegment::class,
        TranscriptFts::class,
        WatchedFolder::class,
        ImportedFile::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class CallScribeDatabase : RoomDatabase() {
    abstract fun callDao(): CallDao
    abstract fun transcriptDao(): TranscriptDao
    abstract fun importDao(): ImportDao

    companion object {
        @Volatile private var instance: CallScribeDatabase? = null

        fun get(context: Context): CallScribeDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    CallScribeDatabase::class.java,
                    "callscribe.db",
                ).build().also { instance = it }
            }
    }
}
