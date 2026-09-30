package com.nicholaston.callscribe.importer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import com.kitsumed.shizucallrecorder.utils.AppLogger
import com.nicholaston.callscribe.data.CallSource

object ShareIntentImporter {
    suspend fun handle(context: Context, intent: Intent): Int {
        if (intent.action !in setOf(Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE)) return 0
        val uris = buildList {
            intent.clipData?.let { clip ->
                repeat(clip.itemCount) { index -> clip.getItemAt(index).uri?.let(::add) }
            }
            @Suppress("DEPRECATION")
            if (intent.action == Intent.ACTION_SEND) {
                intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.let(::add)
            }
            @Suppress("DEPRECATION")
            if (intent.action == Intent.ACTION_SEND_MULTIPLE) {
                intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.let(::addAll)
            }
        }.distinct()
        if (uris.isEmpty()) {
            AppLogger.w("Audio share intent contained no readable content URI")
            return 0
        }
        val processor = ImportProcessor(context.applicationContext)
        return uris.count { uri ->
            val candidate = metadata(context, uri, intent.type)
            candidate != null && processor.import(candidate, CallSource.SHARE) != null
        }
    }

    private fun metadata(context: Context, uri: Uri, fallbackMime: String?): ImportCandidate? {
        var name = uri.lastPathSegment ?: "shared-audio"
        var size = -1L
        var modified = 0L
        runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(
                    OpenableColumns.DISPLAY_NAME,
                    OpenableColumns.SIZE,
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                ),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        .takeIf { it >= 0 }
                        ?.let { name = cursor.getString(it) ?: name }
                    cursor.getColumnIndex(OpenableColumns.SIZE)
                        .takeIf { it >= 0 && !cursor.isNull(it) }
                        ?.let { size = cursor.getLong(it) }
                    cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                        .takeIf { it >= 0 && !cursor.isNull(it) }
                        ?.let { modified = cursor.getLong(it) }
                }
            }
        }.onFailure { AppLogger.w("Unable to query shared audio metadata for $uri", it) }
        if (size < 0) {
            size = context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: -1L
        }
        if (size < 0) {
            AppLogger.e("Shared audio has no readable size: $uri")
            return null
        }
        return ImportCandidate(
            uri = uri,
            displayName = name,
            mimeType = context.contentResolver.getType(uri) ?: fallbackMime,
            size = size,
            lastModified = modified,
        )
    }
}
