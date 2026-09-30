package com.nicholaston.callscribe.ui.calls

import com.nicholaston.callscribe.data.CallDirection
import com.nicholaston.callscribe.data.CallRecord
import com.nicholaston.callscribe.data.CallStatus
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val callDateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy • h:mm a")

fun CallRecord.displayName(): String =
    contactName?.takeIf(String::isNotBlank)
        ?: phoneNumber?.takeIf(String::isNotBlank)
        ?: when (direction) {
            CallDirection.INCOMING -> "Unknown incoming call"
            CallDirection.OUTGOING -> "Unknown outgoing call"
            CallDirection.CONFERENCE -> "Conference call"
            CallDirection.UNKNOWN -> "Unknown call"
        }

fun formatCallDate(timestamp: Long, zoneId: ZoneId = ZoneId.systemDefault()): String =
    callDateFormatter.format(Instant.ofEpochMilli(timestamp).atZone(zoneId))

fun formatDuration(durationMs: Long?): String {
    if (durationMs == null) return "Unknown duration"
    val totalSeconds = durationMs.coerceAtLeast(0) / 1_000
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}

fun formatPosition(positionMs: Long): String = formatDuration(positionMs).removePrefix("Unknown ")

fun CallStatus.displayLabel(): String = when (this) {
    CallStatus.RECORDED -> "Recorded"
    CallStatus.QUEUED -> "Queued"
    CallStatus.TRANSCRIBING -> "Transcribing"
    CallStatus.DONE -> "Transcribed"
    CallStatus.FAILED -> "Failed"
    CallStatus.SKIPPED -> "Skipped"
}
