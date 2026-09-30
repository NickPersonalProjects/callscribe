package com.nicholaston.callscribe.importer

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.CallLog
import androidx.core.content.ContextCompat
import com.kitsumed.shizucallrecorder.ShizuApplication
import com.kitsumed.shizucallrecorder.utils.AppLogger
import com.nicholaston.callscribe.data.CallDirection
import com.nicholaston.callscribe.data.CallRecord
import com.nicholaston.callscribe.data.CallSource
import com.nicholaston.callscribe.data.CallStatus
import com.nicholaston.callscribe.data.ImportedFile
import com.nicholaston.callscribe.storage.AppPrivateRecordingStorage
import com.nicholaston.callscribe.work.TranscriptionScheduler
import kotlinx.coroutines.flow.first

fun interface CallLogLookup {
    fun findNear(startedAt: Long): List<CallLogEntry>
}

class AndroidCallLogLookup(private val context: Context) : CallLogLookup {
    override fun findNear(startedAt: Long): List<CallLogEntry> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            AppLogger.w("Import call-log matching skipped: READ_CALL_LOG is not granted")
            return emptyList()
        }
        val window = 2 * 60 * 1_000L
        return try {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(
                    CallLog.Calls.DATE,
                    CallLog.Calls.DURATION,
                    CallLog.Calls.TYPE,
                    CallLog.Calls.NUMBER,
                    CallLog.Calls.CACHED_NAME,
                ),
                "${CallLog.Calls.DATE} BETWEEN ? AND ?",
                arrayOf((startedAt - window).toString(), (startedAt + window).toString()),
                "${CallLog.Calls.DATE} ASC",
            )?.use { cursor ->
                val date = cursor.getColumnIndexOrThrow(CallLog.Calls.DATE)
                val duration = cursor.getColumnIndexOrThrow(CallLog.Calls.DURATION)
                val type = cursor.getColumnIndexOrThrow(CallLog.Calls.TYPE)
                val number = cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
                val name = cursor.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME)
                buildList {
                    while (cursor.moveToNext()) {
                        add(
                            CallLogEntry(
                                startedAt = cursor.getLong(date),
                                durationMs = cursor.getLong(duration).takeIf { it > 0 }?.times(1_000),
                                direction = when (cursor.getInt(type)) {
                                    CallLog.Calls.INCOMING_TYPE -> CallDirection.INCOMING
                                    CallLog.Calls.OUTGOING_TYPE -> CallDirection.OUTGOING
                                    else -> CallDirection.UNKNOWN
                                },
                                phoneNumber = cursor.getString(number)?.takeIf(String::isNotBlank),
                                contactName = cursor.getString(name)?.takeIf(String::isNotBlank),
                            ),
                        )
                    }
                }
            } ?: emptyList()
        } catch (error: SecurityException) {
            AppLogger.e("Import call-log query denied despite permission check", error)
            emptyList()
        } catch (error: RuntimeException) {
            AppLogger.e("Import call-log query failed", error)
            emptyList()
        }.also {
            if (it.isEmpty()) AppLogger.i("Import call-log matching found no data near the recording")
        }
    }
}

data class ImportCandidate(
    val uri: Uri,
    val displayName: String,
    val mimeType: String?,
    val size: Long,
    val lastModified: Long,
    val sidecarJson: String? = null,
)

class ImportProcessor(
    private val context: Context,
    private val callLogLookup: CallLogLookup = AndroidCallLogLookup(context),
) {
    private val application = context.applicationContext as ShizuApplication
    private val database = application.callScribeContainer.database

    suspend fun import(candidate: ImportCandidate, source: CallSource): Long? {
        if (!ImportPolicy.isAudio(candidate.displayName, candidate.mimeType)) return null
        val fingerprint = ImportFingerprint(
            uri = candidate.uri.toString(),
            size = candidate.size,
            lastModified = candidate.lastModified,
        )
        val reservation = database.importDao().markImported(
            ImportedFile(
                documentUri = fingerprint.uri,
                size = fingerprint.size,
                lastModified = fingerprint.lastModified,
            ),
        )
        if (reservation == -1L) {
            AppLogger.d("Skipping duplicate import: ${candidate.displayName}")
            return null
        }

        var privateUri: Uri? = null
        try {
            privateUri = context.contentResolver.openInputStream(candidate.uri)?.use { input ->
                AppPrivateRecordingStorage.copyImport(context, candidate.displayName, input)
            } ?: error("Unable to open shared recording")

            val parsed = if (candidate.sidecarJson != null) {
                BcrRecordingParser.mergeSidecar(candidate.displayName, candidate.sidecarJson)
            } else {
                RecordingParsers.parse(candidate.displayName)
            }
            val matchTimestamp = parsed?.startedAt ?: candidate.lastModified.takeIf { it > 0 }
            val callLog = matchTimestamp?.let(callLogLookup::findNear)
                ?.let { ImportPolicy.matchCallLog(matchTimestamp, it) }
            val metadata = ImportPolicy.merge(parsed, callLog)
            val duration = readDuration(privateUri)
            val startedAt = metadata.startedAt
                ?: candidate.lastModified.takeIf { it > 0 }
                ?: System.currentTimeMillis()
            val actualSize = AppPrivateRecordingStorage.size(privateUri)
            val callId = database.callDao().insert(
                CallRecord(
                    source = source,
                    startedAt = startedAt,
                    endedAt = duration?.let(startedAt::plus),
                    durationMs = duration ?: callLog?.durationMs,
                    direction = metadata.direction,
                    phoneNumber = metadata.phoneNumber,
                    contactName = metadata.contactName,
                    simSlot = metadata.simSlot,
                    audioUri = privateUri.toString(),
                    audioMime = candidate.mimeType,
                    audioBytes = actualSize,
                    status = CallStatus.RECORDED,
                ),
            )
            database.importDao().attachCall(reservation, callId)
            val timing = application.callScribeContainer.settings.values.first().transcriptionTiming
            TranscriptionScheduler.enqueue(context, callId, timing)
            AppLogger.i("Imported ${candidate.displayName} as call $callId")
            return callId
        } catch (error: Exception) {
            privateUri?.let { AppPrivateRecordingStorage.delete(context, it) }
            database.importDao().deleteReservation(reservation)
            AppLogger.e("Failed to import ${candidate.displayName}", error)
            return null
        }
    }

    private fun readDuration(uri: Uri): Long? = runCatching {
        MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        }
    }.getOrNull()
}

fun ContentResolver.readText(uri: Uri, maxBytes: Int = 1_048_576): String? =
    runCatching {
        openInputStream(uri)?.bufferedReader()?.use { reader ->
            val buffer = CharArray(maxBytes)
            val count = reader.read(buffer)
            if (count < 0) "" else String(buffer, 0, count)
        }
    }.getOrNull()
