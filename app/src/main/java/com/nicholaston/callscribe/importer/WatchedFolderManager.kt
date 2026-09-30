package com.nicholaston.callscribe.importer

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.kitsumed.shizucallrecorder.ShizuApplication
import com.kitsumed.shizucallrecorder.utils.AppLogger
import com.nicholaston.callscribe.data.WatchedFolder

class WatchedFolderManager(private val context: Context) {
    private val dao =
        (context.applicationContext as ShizuApplication).callScribeContainer.database.importDao()

    suspend fun add(uri: Uri): Boolean {
        return try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
            val label = DocumentFile.fromTreeUri(context, uri)?.name ?: uri.lastPathSegment ?: "Folder"
            dao.insertFolder(WatchedFolder(treeUri = uri.toString(), label = label)) != -1L
        } catch (error: SecurityException) {
            AppLogger.e("Unable to persist watched-folder permission for $uri", error)
            false
        }
    }

    suspend fun remove(folder: WatchedFolder) {
        dao.deleteFolder(folder.id)
        runCatching {
            context.contentResolver.releasePersistableUriPermission(
                Uri.parse(folder.treeUri),
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }.onFailure { AppLogger.w("Unable to release watched-folder permission", it) }
    }
}
