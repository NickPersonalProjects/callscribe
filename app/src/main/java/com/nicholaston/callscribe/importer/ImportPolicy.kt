package com.nicholaston.callscribe.importer

import com.nicholaston.callscribe.data.CallDirection
import kotlin.math.abs

data class ImportFingerprint(
    val uri: String,
    val size: Long,
    val lastModified: Long,
)

data class CallLogEntry(
    val startedAt: Long,
    val durationMs: Long?,
    val direction: CallDirection,
    val phoneNumber: String?,
    val contactName: String?,
)

object ImportPolicy {
    private const val CALL_LOG_MATCH_WINDOW_MS = 2 * 60 * 1_000L

    fun isAudio(displayName: String, mimeType: String?): Boolean {
        if (mimeType?.startsWith("audio/", ignoreCase = true) == true) return true
        return displayName.substringAfterLast('.', "").lowercase() in setOf(
            "aac", "amr", "flac", "m4a", "mp3", "ogg", "opus", "wav", "3gp",
        )
    }

    fun sidecarBaseName(displayName: String): String =
        displayName.substringBeforeLast('.')

    fun matchCallLog(startedAt: Long?, entries: List<CallLogEntry>): CallLogEntry? {
        val timestamp = startedAt ?: return null
        return entries
            .asSequence()
            .map { it to abs(it.startedAt - timestamp) }
            .filter { (_, difference) -> difference <= CALL_LOG_MATCH_WINDOW_MS }
            .minByOrNull { (_, difference) -> difference }
            ?.first
    }

    fun merge(parsed: ParsedRecording?, callLog: CallLogEntry?): ParsedRecording {
        return ParsedRecording(
            startedAt = parsed?.startedAt ?: callLog?.startedAt,
            direction = parsed?.direction?.takeUnless { it == CallDirection.UNKNOWN }
                ?: callLog?.direction
                ?: CallDirection.UNKNOWN,
            phoneNumber = parsed?.phoneNumber ?: callLog?.phoneNumber,
            contactName = parsed?.contactName ?: callLog?.contactName,
            simSlot = parsed?.simSlot,
        )
    }
}
