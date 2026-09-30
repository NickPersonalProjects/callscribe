package com.nicholaston.callscribe.hooks

import android.content.Context
import android.net.Uri
import com.kitsumed.shizucallrecorder.ShizuApplication
import com.kitsumed.shizucallrecorder.data.call.EnrichedCallData
import com.nicholaston.callscribe.data.CallDirection
import com.nicholaston.callscribe.data.CallRecord
import com.nicholaston.callscribe.data.CallSource
import com.nicholaston.callscribe.data.CallStatus
import com.nicholaston.callscribe.notifications.CallScribeNotificationHelper
import com.nicholaston.callscribe.policy.CallStartPolicy
import com.nicholaston.callscribe.storage.AppPrivateRecordingStorage
import com.nicholaston.callscribe.work.TranscriptionScheduler
import com.nicholaston.callscribe.work.ImportScanScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

object CallScribeHooks {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun onCallWillBeRecorded(context: Context, metadata: EnrichedCallData) {
        val application = context.applicationContext as ShizuApplication
        val settings = application.callScribeContainer.settings.values.first()
        val effects = CallStartPolicy.effects(
            shouldRecord = true,
            consentReminderEnabled = settings.consentReminder,
        )
        if (effects.showConsentReminder) {
            CallScribeNotificationHelper.showConsentReminder(context, metadata)
        }
    }

    fun onRecordingSkippedBecauseShizukuUnavailable(
        context: Context,
        metadata: EnrichedCallData,
        startedAt: Long,
    ) {
        val application = context.applicationContext as ShizuApplication
        scope.launch {
            val endedAt = System.currentTimeMillis()
            application.callScribeContainer.database.callDao().insert(
                CallRecord(
                    source = CallSource.SHIZUKU,
                    startedAt = startedAt,
                    endedAt = endedAt,
                    durationMs = (endedAt - startedAt).coerceAtLeast(0),
                    direction = metadata.toCallScribeDirection(),
                    phoneNumber = metadata.getBestNumber().ifBlank { null },
                    contactName = metadata.callerName,
                    status = CallStatus.SKIPPED,
                    error = SHIZUKU_NOT_RUNNING_ERROR,
                ),
            )
            CallScribeNotificationHelper.showShizukuNotRunning(context, metadata)
        }
    }

    fun onRecordingFinalized(
        context: Context,
        uri: Uri,
        metadata: EnrichedCallData,
        startedAt: Long,
        endedAt: Long,
        mimeType: String,
    ) {
        val application = context.applicationContext as ShizuApplication
        scope.launch {
            val size = when (uri.scheme) {
                "file" -> AppPrivateRecordingStorage.size(uri)
                else -> context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: 0
            }
            if (size <= 0) return@launch
            val call = CallRecord(
                source = CallSource.SHIZUKU,
                startedAt = startedAt,
                endedAt = endedAt,
                durationMs = (endedAt - startedAt).coerceAtLeast(0),
                direction = metadata.toCallScribeDirection(),
                phoneNumber = metadata.getBestNumber().ifBlank { null },
                contactName = metadata.callerName,
                audioUri = uri.toString(),
                audioMime = mimeType,
                audioBytes = size,
                status = CallStatus.RECORDED,
            )
            val callId = application.callScribeContainer.database.callDao().insert(call)
            val timing = application.callScribeContainer.settings.values.first().transcriptionTiming
            TranscriptionScheduler.enqueue(context, callId, timing)
            ImportScanScheduler.scanAfterCall(context)
        }
    }

    private fun EnrichedCallData.toCallScribeDirection() = when (direction) {
        com.kitsumed.shizucallrecorder.data.call.CallDirection.INCOMING -> CallDirection.INCOMING
        com.kitsumed.shizucallrecorder.data.call.CallDirection.OUTGOING -> CallDirection.OUTGOING
    }

    const val SHIZUKU_NOT_RUNNING_ERROR = "Call NOT recorded — Shizuku not running"
}
