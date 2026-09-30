package com.nicholaston.callscribe.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.app.PendingIntent
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.kitsumed.shizucallrecorder.ShizuApplication
import com.nicholaston.callscribe.data.CallStatus
import com.nicholaston.callscribe.data.ChannelLayout as StoredChannelLayout
import com.nicholaston.callscribe.data.TranscriptSegment
import com.nicholaston.callscribe.data.TranscriptionTiming
import com.nicholaston.callscribe.models.ModelRegistry
import com.nicholaston.callscribe.models.ModelStore
import com.nicholaston.callscribe.transcription.AudioDecoder
import com.nicholaston.callscribe.transcription.ChannelLayout
import com.nicholaston.callscribe.transcription.ChannelSplitDetector
import com.nicholaston.callscribe.transcription.DecodedAudioFormat
import com.nicholaston.callscribe.transcription.FloatPcmChunk
import com.nicholaston.callscribe.transcription.PcmChunkConsumer
import com.nicholaston.callscribe.transcription.SherpaParakeetEngine
import com.nicholaston.callscribe.transcription.SpeechSamples
import com.nicholaston.callscribe.transcription.StreamingResampler
import com.nicholaston.callscribe.transcription.VadSegmenter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

class TranscriptionWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    private val application = context.applicationContext as ShizuApplication
    private val database = application.callScribeContainer.database
    private val calls = database.callDao()
    private val transcripts = database.transcriptDao()

    override suspend fun doWork(): Result {
        val callId = inputData.getLong(KEY_CALL_ID, 0)
        if (callId <= 0) return Result.failure(workDataOf(KEY_ERROR to "Missing call ID"))
        val call = calls.get(callId)
            ?: return Result.failure(workDataOf(KEY_ERROR to "Call $callId no longer exists"))
        val audioUri = call.audioUri?.let(Uri::parse)
            ?: return fail(callId, "Recording has no audio file")
        val settings = application.callScribeContainer.settings.values.first()
        val modelId = settings.defaultModelId
        val modelStore = ModelStore(applicationContext)
        val model = ModelRegistry.require(modelId)
        if (!modelStore.isInstalled(model)) {
            return fail(callId, "Download ${model.displayName} before transcribing")
        }

        setForeground(notification(callId, call.contactName ?: call.phoneNumber, 0))
        calls.updateProgress(callId, CallStatus.TRANSCRIBING, 0, call.lastProcessedMs)
        if (call.lastProcessedMs == 0L) transcripts.deleteForCall(callId)

        return try {
            val outcome = withContext(Dispatchers.IO) {
                transcribe(
                    callId = callId,
                    audioUri = audioUri,
                    resumeAtMs = call.lastProcessedMs,
                    durationMs = call.durationMs,
                    modelStore = modelStore,
                    modelId = modelId,
                    threads = settings.threadCount,
                    provider = settings.provider,
                    firstSegmentIndex = transcripts.nextIndex(callId),
                )
            }
            calls.markCompleted(
                callId = callId,
                lastProcessedMs = outcome.lastProcessedMs,
                modelId = modelId,
                language = outcome.language,
            )
            showCompletedNotification(callId, call.contactName ?: call.phoneNumber)
            Result.success(workDataOf(KEY_CALL_ID to callId))
        } catch (error: Exception) {
            val latestOffset = calls.get(callId)?.lastProcessedMs ?: call.lastProcessedMs
            if (runAttemptCount < MAX_RETRIES) {
                calls.updateProgress(
                    callId,
                    CallStatus.QUEUED,
                    0,
                    latestOffset,
                    error.message,
                )
                Result.retry()
            } else {
                fail(callId, error.message ?: error::class.java.simpleName)
            }
        }
    }

    private fun transcribe(
        callId: Long,
        audioUri: Uri,
        resumeAtMs: Long,
        durationMs: Long?,
        modelStore: ModelStore,
        modelId: String,
        threads: Int,
        provider: String,
        firstSegmentIndex: Int,
    ): TranscriptionOutcome {
        val index = AtomicInteger(firstSegmentIndex)
        var lastProcessedMs = resumeAtMs
        var language: String? = null
        var decodedFormat: DecodedAudioFormat? = null
        var resampler: StreamingResampler? = null
        var layout: ChannelLayout? = null

        SherpaParakeetEngine(modelStore, modelId, threads, provider).use { engine ->
            VadSegmenter(applicationContext, provider = provider).use { monoVad ->
                VadSegmenter(applicationContext, provider = provider).use { leftVad ->
                    VadSegmenter(applicationContext, provider = provider).use { rightVad ->
                        fun persist(speech: SpeechSamples, speaker: String?) {
                            if (speech.startMs + speech.samples.size * 1_000L / SAMPLE_RATE <= resumeAtMs) return
                            val recognized = engine.transcribe(speech.samples, SAMPLE_RATE, speech.startMs)
                            if (recognized.text.isBlank()) return
                            language = recognized.language ?: language
                            lastProcessedMs = maxOf(lastProcessedMs, recognized.endMs)
                            val segment = TranscriptSegment(
                                callId = callId,
                                index = index.getAndIncrement(),
                                startMs = recognized.startMs,
                                endMs = recognized.endMs,
                                speaker = speaker,
                                text = recognized.text,
                            )
                            val progress = progress(lastProcessedMs, durationMs)
                            runBlocking {
                                transcripts.insert(segment)
                                calls.updateProgress(
                                    callId,
                                    CallStatus.TRANSCRIBING,
                                    progress,
                                    lastProcessedMs,
                                )
                                setProgress(workDataOf(KEY_PROGRESS to progress))
                                setForeground(notification(callId, null, progress))
                            }
                        }

                        fun process(samples: FloatArray) {
                            val currentFormat = checkNotNull(decodedFormat)
                            if (samples.isEmpty()) return
                            if (currentFormat.channelCount == 1) {
                                monoVad.accept(samples, resumeAtMs).forEach { persist(it, null) }
                                return
                            }
                            val detected = layout ?: ChannelSplitDetector.classify(
                                samples,
                                currentFormat.channelCount,
                            ).also {
                                layout = it
                                runBlocking {
                                    calls.updateChannelLayout(
                                        callId,
                                        when (it) {
                                            ChannelLayout.MONO -> StoredChannelLayout.MONO
                                            ChannelLayout.STEREO_DUPLICATE ->
                                                StoredChannelLayout.STEREO_DUPLICATE
                                            ChannelLayout.STEREO_UPLINK_DOWNLINK ->
                                                StoredChannelLayout.STEREO_UPLINK_DOWNLINK
                                        },
                                    )
                                }
                            }
                            if (detected == ChannelLayout.STEREO_UPLINK_DOWNLINK) {
                                val channels = ChannelSplitDetector.splitStereo(samples)
                                val speech = leftVad.accept(channels.left, resumeAtMs).map { it to "Me" } +
                                    rightVad.accept(channels.right, resumeAtMs).map { it to "Them" }
                                speech.sortedBy { it.first.startMs }.forEach { persist(it.first, it.second) }
                            } else {
                                val mono = ChannelSplitDetector.downmix(samples, currentFormat.channelCount)
                                monoVad.accept(mono, resumeAtMs).forEach { persist(it, null) }
                            }
                        }

                        AudioDecoder(applicationContext, audioUri).decode(
                            object : PcmChunkConsumer {
                                override fun onFormat(format: DecodedAudioFormat) {
                                    if (decodedFormat == null) {
                                        decodedFormat = format
                                        resampler = StreamingResampler(
                                            inputSampleRate = format.sampleRate,
                                            outputSampleRate = SAMPLE_RATE,
                                            channelCount = format.channelCount,
                                        )
                                    } else {
                                        check(decodedFormat == format) {
                                            "Audio format changed during decoding: $decodedFormat -> $format"
                                        }
                                    }
                                }

                                override fun onChunk(chunk: FloatPcmChunk) {
                                    if (chunk.presentationTimeUs / 1_000 < resumeAtMs) return
                                    process(checkNotNull(resampler).process(chunk.samples))
                                }
                            },
                        )
                        process(checkNotNull(resampler).flush())
                        monoVad.flush(resumeAtMs).forEach { persist(it, null) }
                        val finalStereoSpeech =
                            leftVad.flush(resumeAtMs).map { it to "Me" } +
                                rightVad.flush(resumeAtMs).map { it to "Them" }
                        finalStereoSpeech.sortedBy { it.first.startMs }
                            .forEach { persist(it.first, it.second) }
                    }
                }
            }
        }
        return TranscriptionOutcome(lastProcessedMs, language)
    }

    private suspend fun fail(callId: Long, message: String): Result {
        calls.updateProgress(callId, CallStatus.FAILED, 0, 0, message)
        return Result.failure(workDataOf(KEY_ERROR to message))
    }

    private fun progress(processedMs: Long, durationMs: Long?): Int =
        if (durationMs == null || durationMs <= 0) 0
        else ((processedMs * 100) / durationMs).toInt().coerceIn(0, 99)

    private fun notification(callId: Long, label: String?, progress: Int): ForegroundInfo {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Call transcription", NotificationManager.IMPORTANCE_LOW),
            )
        }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(com.kitsumed.shizucallrecorder.R.drawable.ic_mic)
            .setContentTitle("Transcribing ${label ?: "call #$callId"}")
            .setContentText("$progress%")
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(100, progress, false)
            .build()
        val serviceType = if (Build.VERSION.SDK_INT >= 35) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
        } else {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        }
        return ForegroundInfo(NOTIFICATION_ID + (callId % 1_000).toInt(), notification, serviceType)
    }

    private fun showCompletedNotification(callId: Long, label: String?) {
        val openApp = Intent(
            applicationContext,
            com.kitsumed.shizucallrecorder.MainActivity::class.java,
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            callId.hashCode(),
            openApp,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(com.kitsumed.shizucallrecorder.R.drawable.ic_mic)
            .setContentTitle("Transcript ready")
            .setContentText(label ?: "Open CallScribe to view the transcript")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        applicationContext.getSystemService(NotificationManager::class.java)
            .notify(COMPLETED_NOTIFICATION_ID + (callId % 1_000).toInt(), notification)
    }

    private data class TranscriptionOutcome(val lastProcessedMs: Long, val language: String?)

    companion object {
        const val KEY_CALL_ID = "call_id"
        const val KEY_PROGRESS = "progress"
        const val KEY_ERROR = "error"
        private const val CHANNEL_ID = "transcription"
        private const val NOTIFICATION_ID = 5_000
        private const val COMPLETED_NOTIFICATION_ID = 6_000
        private const val SAMPLE_RATE = 16_000
        private const val MAX_RETRIES = 3
    }
}

object TranscriptionScheduler {
    fun enqueue(context: Context, callId: Long, timing: TranscriptionTiming) {
        if (timing == TranscriptionTiming.MANUAL) return
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .setRequiresStorageNotLow(true)
            .setRequiresCharging(timing == TranscriptionTiming.CHARGING_ONLY)
            .build()
        val request = OneTimeWorkRequestBuilder<TranscriptionWorker>()
            .setInputData(workDataOf(TranscriptionWorker.KEY_CALL_ID to callId))
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "transcribe-call-$callId",
            ExistingWorkPolicy.KEEP,
            request,
        )
    }
}
