package com.nicholaston.callscribe.export

import com.nicholaston.callscribe.data.CallRecord
import com.nicholaston.callscribe.data.TranscriptSegment
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object TranscriptExporter {
    fun text(call: CallRecord, segments: List<TranscriptSegment>): String =
        buildString {
            appendLine(title(call))
            appendLine()
            segments.forEach { segment ->
                appendLine("${speaker(segment)} ${segment.text}".trim())
            }
        }.trimEnd()

    fun markdown(call: CallRecord, segments: List<TranscriptSegment>): String =
        buildString {
            appendLine("# ${escapeMarkdown(title(call))}")
            appendLine()
            appendLine("- **Date:** ${formatDate(call.startedAt)}")
            appendLine("- **Direction:** ${call.direction.name.lowercase()}")
            call.durationMs?.let { appendLine("- **Duration:** ${formatDuration(it)}") }
            call.modelId?.let { appendLine("- **Model:** ${escapeMarkdown(it)}") }
            appendLine()
            segments.forEach { segment ->
                appendLine(
                    "**${escapeMarkdown(speaker(segment))}** " +
                        "[${formatClock(segment.startMs)}] ${escapeMarkdown(segment.text)}",
                )
                appendLine()
            }
        }.trimEnd()

    fun srt(segments: List<TranscriptSegment>): String =
        segments.mapIndexed { index, segment ->
            buildString {
                appendLine(index + 1)
                appendLine("${formatSrtTime(segment.startMs)} --> ${formatSrtTime(segment.endMs)}")
                segment.speaker?.takeIf(String::isNotBlank)?.let { append("[$it] ") }
                append(segment.text.trim())
            }
        }.joinToString("\n\n")

    private fun title(call: CallRecord): String =
        call.contactName?.takeIf(String::isNotBlank)
            ?: call.phoneNumber?.takeIf(String::isNotBlank)
            ?: "Call ${formatDate(call.startedAt)}"

    private fun speaker(segment: TranscriptSegment): String =
        segment.speaker?.takeIf(String::isNotBlank)?.let { "$it:" } ?: ""

    private fun escapeMarkdown(value: String): String =
        value.replace("\\", "\\\\")
            .replace("*", "\\*")
            .replace("_", "\\_")
            .replace("[", "\\[")
            .replace("]", "\\]")

    private fun formatDate(epochMs: Long): String =
        DATE_FORMAT.format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()))

    private fun formatDuration(durationMs: Long): String =
        formatClock(durationMs)

    private fun formatClock(durationMs: Long): String {
        val totalSeconds = durationMs.coerceAtLeast(0) / 1_000
        return "%02d:%02d:%02d".format(
            totalSeconds / 3_600,
            totalSeconds % 3_600 / 60,
            totalSeconds % 60,
        )
    }

    private fun formatSrtTime(milliseconds: Long): String {
        val safe = milliseconds.coerceAtLeast(0)
        val hours = safe / 3_600_000
        val minutes = safe % 3_600_000 / 60_000
        val seconds = safe % 60_000 / 1_000
        val millis = safe % 1_000
        return "%02d:%02d:%02d,%03d".format(hours, minutes, seconds, millis)
    }

    private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")
}
