package com.nicholaston.callscribe.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transcript_segments",
    foreignKeys = [
        ForeignKey(
            entity = CallRecord::class,
            parentColumns = ["id"],
            childColumns = ["call_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("call_id"), Index(value = ["call_id", "segment_index"], unique = true)],
)
data class TranscriptSegment(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "rowid")
    val id: Long = 0,
    @ColumnInfo(name = "call_id") val callId: Long,
    @ColumnInfo(name = "segment_index") val index: Int,
    @ColumnInfo(name = "start_ms") val startMs: Long,
    @ColumnInfo(name = "end_ms") val endMs: Long,
    val speaker: String? = null,
    val text: String,
)

@Fts4(contentEntity = TranscriptSegment::class)
@Entity(tableName = "transcript_fts")
data class TranscriptFts(
    @PrimaryKey
    @ColumnInfo(name = "rowid")
    val rowId: Long,
    val text: String,
)
