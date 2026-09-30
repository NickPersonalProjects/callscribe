package com.nicholaston.callscribe.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "calls")
data class CallRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val source: CallSource,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "ended_at") val endedAt: Long? = null,
    @ColumnInfo(name = "duration_ms") val durationMs: Long? = null,
    val direction: CallDirection,
    @ColumnInfo(name = "phone_number") val phoneNumber: String? = null,
    @ColumnInfo(name = "contact_name") val contactName: String? = null,
    @ColumnInfo(name = "sim_slot") val simSlot: Int? = null,
    @ColumnInfo(name = "audio_uri") val audioUri: String? = null,
    @ColumnInfo(name = "audio_mime") val audioMime: String? = null,
    @ColumnInfo(name = "audio_bytes") val audioBytes: Long? = null,
    @ColumnInfo(name = "channel_layout") val channelLayout: ChannelLayout = ChannelLayout.UNKNOWN,
    val status: CallStatus = CallStatus.RECORDED,
    val progress: Int = 0,
    @ColumnInfo(name = "model_id") val modelId: String? = null,
    val language: String? = null,
    val error: String? = null,
    @ColumnInfo(name = "last_processed_ms") val lastProcessedMs: Long = 0,
    @ColumnInfo(name = "speaker_names_json") val speakerNamesJson: String = "{}",
    @ColumnInfo(name = "keep_forever") val keepForever: Boolean = false,
    @ColumnInfo(name = "audio_deleted_at") val audioDeletedAt: Long? = null,
)

enum class CallSource { SHIZUKU, IMPORT, SHARE }

enum class CallDirection { INCOMING, OUTGOING, CONFERENCE, UNKNOWN }

enum class ChannelLayout { UNKNOWN, MONO, STEREO_DUPLICATE, STEREO_UPLINK_DOWNLINK }

enum class CallStatus { RECORDED, QUEUED, TRANSCRIBING, DONE, FAILED, SKIPPED }
