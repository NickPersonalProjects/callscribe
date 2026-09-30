package com.nicholaston.callscribe.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.nicholaston.callscribe.data.CallRecord
import com.nicholaston.callscribe.data.TranscriptSegment
import java.io.File

enum class TranscriptExportFormat(val extension: String, val mimeType: String) {
    TEXT("txt", "text/plain"),
    MARKDOWN("md", "text/markdown"),
    SRT("srt", "application/x-subrip"),
}

object CallShareManager {
    fun shareTranscript(
        context: Context,
        call: CallRecord,
        segments: List<TranscriptSegment>,
        format: TranscriptExportFormat,
    ) {
        val content = when (format) {
            TranscriptExportFormat.TEXT -> TranscriptExporter.text(call, segments)
            TranscriptExportFormat.MARKDOWN -> TranscriptExporter.markdown(call, segments)
            TranscriptExportFormat.SRT -> TranscriptExporter.srt(segments)
        }
        val directory = File(context.cacheDir, "exports").apply { mkdirs() }
        val output = File(directory, "call-${call.id}.${format.extension}")
        output.writeText(content)
        shareUri(
            context,
            FileProvider.getUriForFile(context, "${context.packageName}.files", output),
            format.mimeType,
        )
    }

    fun shareAudio(context: Context, call: CallRecord) {
        val source = call.audioUri?.let(Uri::parse) ?: error("The recording audio was deleted")
        val shareable = if (source.scheme == "file") {
            val file = source.path?.let(::File)?.takeIf(File::isFile)
                ?: error("The recording audio file no longer exists")
            FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        } else {
            source
        }
        shareUri(context, shareable, call.audioMime ?: "audio/*")
    }

    private fun shareUri(context: Context, uri: Uri, mimeType: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share call").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
