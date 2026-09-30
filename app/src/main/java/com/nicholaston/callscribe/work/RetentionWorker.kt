package com.nicholaston.callscribe.work

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.kitsumed.shizucallrecorder.ShizuApplication
import com.nicholaston.callscribe.data.CallRecord
import com.nicholaston.callscribe.storage.AppPrivateRecordingStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

data class RetentionDecision(
    val deleteCall: Boolean,
    val deleteAudio: Boolean,
)

object RetentionPolicy {
    private const val DAY_MS = 24L * 60 * 60 * 1_000

    fun decide(
        call: CallRecord,
        now: Long,
        audioDays: Long?,
        callDays: Long?,
    ): RetentionDecision {
        if (call.keepForever) return RetentionDecision(false, false)
        val ageMs = (now - call.startedAt).coerceAtLeast(0)
        val deleteCall = callDays?.let { ageMs >= it * DAY_MS } == true
        val deleteAudio = !deleteCall &&
            call.audioUri != null &&
            audioDays?.let { ageMs >= it * DAY_MS } == true
        return RetentionDecision(deleteCall, deleteAudio)
    }
}

class RetentionWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val application = applicationContext as ShizuApplication
        val container = application.callScribeContainer
        val settings = container.settings.values.first()
        val calls = container.database.callDao()
        val now = System.currentTimeMillis()

        calls.getRetentionCandidates().forEach { call ->
            val decision = RetentionPolicy.decide(
                call,
                now,
                settings.retentionAudioDays,
                settings.retentionCallDays,
            )
            when {
                decision.deleteCall -> {
                    call.audioUri?.let { deleteAudio(applicationContext, Uri.parse(it)) }
                    calls.delete(call.id)
                }
                decision.deleteAudio -> {
                    val uri = Uri.parse(checkNotNull(call.audioUri))
                    if (deleteAudio(applicationContext, uri)) {
                        calls.markAudioDeleted(call.id, now)
                    }
                }
            }
        }
        Result.success()
    }

    private fun deleteAudio(context: Context, uri: Uri): Boolean =
        when (uri.scheme) {
            "file" -> AppPrivateRecordingStorage.delete(context, uri)
            else -> runCatching { context.contentResolver.delete(uri, null, null) > 0 }.getOrDefault(false)
        }

    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<RetentionWorker>(1, TimeUnit.DAYS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "daily-retention",
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }
    }
}
