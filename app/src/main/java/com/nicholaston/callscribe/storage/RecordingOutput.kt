package com.nicholaston.callscribe.storage

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.InputStream
import java.util.UUID

data class RecordingOutput(
    val uri: Uri,
    val descriptor: ParcelFileDescriptor,
    val displayName: String,
)

object AppPrivateRecordingStorage {
    fun create(context: Context, relativePath: String): RecordingOutput {
        val target = resolveRecordingFile(context, relativePath)
        check(target.parentFile?.mkdirs() != false) { "Unable to create recording directory" }
        val descriptor = ParcelFileDescriptor.open(
            target,
            ParcelFileDescriptor.MODE_CREATE or
                ParcelFileDescriptor.MODE_TRUNCATE or
                ParcelFileDescriptor.MODE_READ_WRITE,
        )
        return RecordingOutput(
            uri = Uri.fromFile(target),
            descriptor = descriptor,
            displayName = target.absolutePath,
        )
    }

    fun delete(context: Context, uri: Uri): Boolean {
        val target = recordingFileFromUri(context, uri) ?: return false
        return !target.exists() || target.delete()
    }

    fun size(uri: Uri): Long =
        uri.takeIf { it.scheme == "file" }?.path?.let(::File)?.takeIf(File::isFile)?.length() ?: 0

    fun copyImport(context: Context, displayName: String, input: InputStream): Uri {
        val extension = displayName.substringAfterLast('.', "")
            .takeIf { it.matches(Regex("[A-Za-z0-9]{1,8}")) }
            ?.lowercase()
        val fileName = buildString {
            append(System.currentTimeMillis())
            append('-')
            append(UUID.randomUUID())
            extension?.let { append('.').append(it) }
        }
        val target = resolveRecordingFile(context, "imports${File.separator}$fileName")
        check(target.parentFile?.mkdirs() != false) { "Unable to create import directory" }
        try {
            target.outputStream().use { output -> input.copyTo(output) }
        } catch (error: Exception) {
            target.delete()
            throw error
        }
        return Uri.fromFile(target)
    }

    private fun resolveRecordingFile(context: Context, relativePath: String): File =
        validateRecordingFile(context, File(recordingRoot(context), relativePath))

    private fun recordingFileFromUri(context: Context, uri: Uri): File? {
        if (uri.scheme != "file") return null
        val path = uri.path ?: return null
        return validateRecordingFile(context, File(path))
    }

    private fun validateRecordingFile(context: Context, target: File): File {
        val root = recordingRoot(context).canonicalFile
        val canonicalTarget = target.canonicalFile
        require(canonicalTarget.path.startsWith(root.path + File.separator)) {
            "Recording path escapes app-private storage"
        }
        return canonicalTarget
    }

    private fun recordingRoot(context: Context): File =
        File(context.filesDir, "recordings")
}
