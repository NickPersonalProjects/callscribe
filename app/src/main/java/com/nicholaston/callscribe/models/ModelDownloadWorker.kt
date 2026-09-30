package com.nicholaston.callscribe.models

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URI
import kotlin.math.roundToInt

class ModelDownloadWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    private val store = ModelStore(context)

    override suspend fun doWork(): Result {
        val modelId = inputData.getString(KEY_MODEL_ID) ?: return Result.failure()
        val model = runCatching { ModelRegistry.requireDownloadable(modelId) }.getOrElse {
            return Result.failure(workDataOf(KEY_ERROR to it.message))
        }

        if (store.isInstalled(model)) return Result.success()

        val remainingBytes = model.files.sumOf { file ->
            val partial = store.partialFile(model, file).length().coerceAtMost(file.bytes)
            file.bytes - partial
        }
        val safetyMargin = 128L * 1024 * 1024
        if (store.availableBytes() < remainingBytes + safetyMargin) {
            return Result.failure(workDataOf(KEY_ERROR to "Not enough free space for ${model.displayName}"))
        }

        setForeground(foreground(model, 0))
        var completedBytes = 0L

        return try {
            model.files.forEach { modelFile ->
                if (isStopped) return Result.retry()
                val destination = store.file(model, modelFile)
                if (isValid(destination, modelFile)) {
                    completedBytes += modelFile.bytes
                    return@forEach
                }

                destination.parentFile?.mkdirs()
                val partial = store.partialFile(model, modelFile)
                download(model, modelFile, partial) { currentFileBytes ->
                    val total = completedBytes + currentFileBytes
                    val progress = ((total * 100.0) / model.downloadBytes).roundToInt().coerceIn(0, 99)
                    setProgress(workDataOf(KEY_PROGRESS to progress, KEY_DOWNLOADED_BYTES to total))
                    setForeground(foreground(model, progress))
                }

                if (!isValid(partial, modelFile)) {
                    partial.delete()
                    return Result.failure(workDataOf(KEY_ERROR to "Checksum failed for ${modelFile.name}"))
                }
                if (destination.exists() && !destination.delete()) {
                    return Result.failure(workDataOf(KEY_ERROR to "Cannot replace ${destination.name}"))
                }
                if (!partial.renameTo(destination)) {
                    return Result.failure(workDataOf(KEY_ERROR to "Cannot finalize ${destination.name}"))
                }
                completedBytes += modelFile.bytes
            }
            setProgress(workDataOf(KEY_PROGRESS to 100, KEY_DOWNLOADED_BYTES to model.downloadBytes))
            Result.success(workDataOf(KEY_MODEL_ID to model.id))
        } catch (error: java.io.IOException) {
            Result.retry()
        }
    }

    private suspend fun download(
        model: DownloadableModel,
        modelFile: ModelFile,
        partial: File,
        onProgress: suspend (Long) -> Unit,
    ) {
        var existing = partial.length().coerceAtMost(modelFile.bytes)
        var connection = open(model.downloadUrl(modelFile), existing)
        if (existing > 0 && connection.responseCode != HttpURLConnection.HTTP_PARTIAL) {
            connection.disconnect()
            partial.delete()
            existing = 0
            connection = open(model.downloadUrl(modelFile), 0)
        }
        if (connection.responseCode !in 200..299) {
            val code = connection.responseCode
            connection.disconnect()
            throw java.io.IOException("Model server returned HTTP $code")
        }

        connection.inputStream.buffered().use { input ->
            FileOutputStream(partial, existing > 0).buffered().use { output ->
                val buffer = ByteArray(256 * 1024)
                var downloaded = existing
                while (true) {
                    if (isStopped) throw java.io.InterruptedIOException("Download cancelled")
                    val count = input.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                    downloaded += count
                    onProgress(downloaded)
                }
            }
        }
        connection.disconnect()
    }

    private fun open(url: String, offset: Long): HttpURLConnection =
        (URI(url).toURL().openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "CallScribe/1 Android")
            if (offset > 0) setRequestProperty("Range", "bytes=$offset-")
        }

    private fun isValid(file: File, expected: ModelFile): Boolean =
        file.isFile &&
            file.length() == expected.bytes &&
            ModelStore.sha256(file).equals(expected.sha256, ignoreCase = true)

    private fun foreground(model: DownloadableModel, progress: Int): ForegroundInfo {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Speech model downloads",
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
        }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(com.kitsumed.shizucallrecorder.R.drawable.ic_mic)
            .setContentTitle("Downloading ${model.displayName}")
            .setContentText("$progress%")
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(100, progress, false)
            .build()
        return ForegroundInfo(
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
    }

    companion object {
        const val KEY_MODEL_ID = "model_id"
        const val KEY_PROGRESS = "progress"
        const val KEY_DOWNLOADED_BYTES = "downloaded_bytes"
        const val KEY_ERROR = "error"
        private const val CHANNEL_ID = "model_downloads"
        private const val NOTIFICATION_ID = 4101

        fun enqueue(context: Context, modelId: String, wifiOnly: Boolean) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                .setRequiresStorageNotLow(true)
                .build()
            val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
                .setInputData(Data.Builder().putString(KEY_MODEL_ID, modelId).build())
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "model-download-$modelId",
                ExistingWorkPolicy.KEEP,
                request,
            )
        }
    }
}
