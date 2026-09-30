package com.nicholaston.callscribe.work

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.kitsumed.shizucallrecorder.ShizuApplication
import com.kitsumed.shizucallrecorder.utils.AppLogger
import com.nicholaston.callscribe.data.CallSource
import com.nicholaston.callscribe.importer.ImportCandidate
import com.nicholaston.callscribe.importer.ImportPolicy
import com.nicholaston.callscribe.importer.ImportProcessor
import com.nicholaston.callscribe.importer.readText
import java.util.ArrayDeque
import java.util.concurrent.TimeUnit

class ImportScanWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val application = applicationContext as ShizuApplication
        val importDao = application.callScribeContainer.database.importDao()
        val processor = ImportProcessor(applicationContext)
        var failedFolders = 0
        importDao.getFolders().forEach { folder ->
            val root = DocumentFile.fromTreeUri(applicationContext, Uri.parse(folder.treeUri))
            if (root == null || !root.exists() || !root.canRead()) {
                failedFolders++
                AppLogger.e("Watched-folder scan cannot read ${folder.label} (${folder.treeUri})")
                return@forEach
            }
            runCatching {
                scan(root).forEach { processor.import(it, CallSource.IMPORT) }
                importDao.updateLastScan(folder.id, System.currentTimeMillis())
            }.onFailure { error ->
                failedFolders++
                AppLogger.e("Watched-folder scan failed for ${folder.label}", error)
            }
        }
        return if (failedFolders == 0) Result.success() else Result.retry()
    }

    private fun scan(root: DocumentFile): List<ImportCandidate> {
        val result = mutableListOf<ImportCandidate>()
        val folders = ArrayDeque<DocumentFile>().apply { add(root) }
        while (folders.isNotEmpty()) {
            val children = folders.removeFirst().listFiles()
            val sidecars = children.asSequence()
                .filter { it.isFile && it.name?.endsWith(".json", ignoreCase = true) == true }
                .associateBy { ImportPolicy.sidecarBaseName(it.name.orEmpty()) }
            children.forEach { document ->
                when {
                    document.isDirectory -> folders.add(document)
                    document.isFile -> {
                        val name = document.name ?: return@forEach
                        if (!ImportPolicy.isAudio(name, document.type)) return@forEach
                        val sidecar = sidecars[ImportPolicy.sidecarBaseName(name)]
                            ?.uri
                            ?.let(applicationContext.contentResolver::readText)
                        result += ImportCandidate(
                            uri = document.uri,
                            displayName = name,
                            mimeType = document.type,
                            size = document.length(),
                            lastModified = document.lastModified(),
                            sidecarJson = sidecar,
                        )
                    }
                }
            }
        }
        return result
    }
}

object ImportScanScheduler {
    private const val PERIODIC_WORK = "watched-folder-periodic-scan"
    private const val MANUAL_WORK = "watched-folder-manual-scan"
    private const val DELAYED_WORK = "watched-folder-after-call-scan"

    fun schedulePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<ImportScanWorker>(6, TimeUnit.HOURS).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun scanNow(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            MANUAL_WORK,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<ImportScanWorker>().build(),
        )
    }

    fun scanAfterCall(context: Context, delaySeconds: Long = 30) {
        val request = OneTimeWorkRequestBuilder<ImportScanWorker>()
            .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            DELAYED_WORK,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }
}
