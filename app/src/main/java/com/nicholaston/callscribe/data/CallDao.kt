package com.nicholaston.callscribe.data

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class CallWithSnippet(
    @Embedded val call: CallRecord,
    val snippet: String?,
)

@Dao
interface CallDao {
    @Query("SELECT * FROM calls ORDER BY started_at DESC")
    fun observeAll(): Flow<List<CallRecord>>

    @Query("SELECT * FROM calls WHERE id = :id")
    fun observe(id: Long): Flow<CallRecord?>

    @Query("SELECT * FROM calls WHERE id = :id")
    suspend fun get(id: Long): CallRecord?

    @Insert
    suspend fun insert(call: CallRecord): Long

    @Update
    suspend fun update(call: CallRecord)

    @Query(
        """
        UPDATE calls
        SET status = :status, progress = :progress, error = :error,
            last_processed_ms = :lastProcessedMs
        WHERE id = :callId
        """,
    )
    suspend fun updateProgress(
        callId: Long,
        status: CallStatus,
        progress: Int,
        lastProcessedMs: Long,
        error: String? = null,
    )

    @Query(
        """
        UPDATE calls
        SET status = :status, progress = 100, error = NULL,
            last_processed_ms = :lastProcessedMs, model_id = :modelId, language = :language
        WHERE id = :callId
        """,
    )
    suspend fun markCompleted(
        callId: Long,
        status: CallStatus = CallStatus.DONE,
        lastProcessedMs: Long,
        modelId: String,
        language: String?,
    )

    @Query("DELETE FROM calls WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM calls WHERE keep_forever = 0")
    suspend fun getRetentionCandidates(): List<CallRecord>

    @Query("UPDATE calls SET audio_uri = NULL, audio_deleted_at = :deletedAt WHERE id = :callId")
    suspend fun markAudioDeleted(callId: Long, deletedAt: Long)

    @Query("UPDATE calls SET channel_layout = :layout WHERE id = :callId")
    suspend fun updateChannelLayout(callId: Long, layout: ChannelLayout)

    @Query(
        """
        SELECT calls.*, snippet(transcript_fts, '[', ']', '...', 0, 32) AS snippet
        FROM transcript_fts
        JOIN transcript_segments ON transcript_segments.rowid = transcript_fts.rowid
        JOIN calls ON calls.id = transcript_segments.call_id
        WHERE transcript_fts MATCH :ftsQuery
        ORDER BY calls.started_at DESC
        """,
    )
    fun searchTranscript(ftsQuery: String): Flow<List<CallWithSnippet>>

    @Query(
        """
        SELECT calls.*, NULL AS snippet FROM calls
        WHERE contact_name LIKE '%' || :query || '%' OR phone_number LIKE '%' || :query || '%'
        ORDER BY started_at DESC
        """,
    )
    fun searchMetadata(query: String): Flow<List<CallWithSnippet>>
}

@Dao
interface TranscriptDao {
    @Query("SELECT * FROM transcript_segments WHERE call_id = :callId ORDER BY start_ms, segment_index")
    fun observeForCall(callId: Long): Flow<List<TranscriptSegment>>

    @Query("SELECT COALESCE(MAX(segment_index) + 1, 0) FROM transcript_segments WHERE call_id = :callId")
    suspend fun nextIndex(callId: Long): Int

    @Insert
    suspend fun insert(segment: TranscriptSegment): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(segments: List<TranscriptSegment>)

    @Query("DELETE FROM transcript_segments WHERE call_id = :callId")
    suspend fun deleteForCall(callId: Long)

    @Transaction
    suspend fun replaceForCall(callId: Long, segments: List<TranscriptSegment>) {
        deleteForCall(callId)
        insertAll(segments)
    }
}

@Dao
interface ImportDao {
    @Query("SELECT * FROM watched_folders ORDER BY label")
    fun observeFolders(): Flow<List<WatchedFolder>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFolder(folder: WatchedFolder): Long

    @Query("SELECT * FROM watched_folders ORDER BY label")
    suspend fun getFolders(): List<WatchedFolder>

    @Query("DELETE FROM watched_folders WHERE id = :id")
    suspend fun deleteFolder(id: Long)

    @Query("UPDATE watched_folders SET last_scan_at = :scannedAt WHERE id = :id")
    suspend fun updateLastScan(id: Long, scannedAt: Long)

    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM imported_files
            WHERE document_uri = :uri AND size = :size AND last_modified = :lastModified
        )
        """,
    )
    suspend fun isImported(uri: String, size: Long, lastModified: Long): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun markImported(file: ImportedFile): Long

    @Query("UPDATE imported_files SET call_id = :callId WHERE id = :id")
    suspend fun attachCall(id: Long, callId: Long)

    @Query("DELETE FROM imported_files WHERE id = :id AND call_id IS NULL")
    suspend fun deleteReservation(id: Long)
}
