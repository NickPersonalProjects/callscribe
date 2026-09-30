package com.nicholaston.callscribe.transcription

import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import com.nicholaston.callscribe.models.ModelArchitecture
import com.nicholaston.callscribe.models.ModelRegistry
import com.nicholaston.callscribe.models.ModelStore
import kotlin.math.roundToLong

class SherpaParakeetEngine(
    modelStore: ModelStore,
    modelId: String = ModelRegistry.DEFAULT_MODEL_ID,
    threads: Int = 4,
    provider: String = "cpu",
) : SpeechEngine {
    private val recognizer: OfflineRecognizer

    init {
        val model = ModelRegistry.require(modelId)
        require(modelStore.isInstalled(model)) { "${model.displayName} is not installed" }
        val directory = modelStore.directory(model)
        val modelConfig = when (model.architecture) {
            ModelArchitecture.PARAKEET_TRANSDUCER -> OfflineModelConfig(
                transducer = OfflineTransducerModelConfig(
                    encoder = directory.resolve(model.encoderFile).absolutePath,
                    decoder = directory.resolve(model.decoderFile).absolutePath,
                    joiner = directory.resolve(checkNotNull(model.joinerFile)).absolutePath,
                ),
                numThreads = threads.coerceIn(1, 8),
                provider = provider,
                modelType = model.modelType,
                tokens = directory.resolve(model.tokensFile).absolutePath,
            )
            ModelArchitecture.WHISPER -> OfflineModelConfig(
                whisper = OfflineWhisperModelConfig(
                    encoder = directory.resolve(model.encoderFile).absolutePath,
                    decoder = directory.resolve(model.decoderFile).absolutePath,
                    language = model.languageHint,
                    task = "transcribe",
                    enableTokenTimestamps = true,
                    enableSegmentTimestamps = true,
                ),
                numThreads = threads.coerceIn(1, 8),
                provider = provider,
                modelType = model.modelType,
                tokens = directory.resolve(model.tokensFile).absolutePath,
            )
        }
        recognizer = OfflineRecognizer(
            config = OfflineRecognizerConfig(modelConfig = modelConfig),
        )
    }

    override fun transcribe(
        samples: FloatArray,
        sampleRate: Int,
        startMs: Long,
    ): RecognitionSegment {
        require(sampleRate == SAMPLE_RATE) { "Parakeet requires 16 kHz audio, got $sampleRate Hz" }
        if (samples.isEmpty()) {
            return RecognitionSegment(startMs, startMs, "", null, emptyList())
        }

        val stream = recognizer.createStream()
        return try {
            stream.acceptWaveform(samples, sampleRate)
            recognizer.decode(stream)
            val result = recognizer.getResult(stream)
            val tokenTimes = result.timestamps.map { timestamp ->
                startMs + (timestamp * 1_000).roundToLong()
            }
            val audioEnd = startMs + samples.size * 1_000L / sampleRate
            val timedEnd = result.timestamps.lastOrNull()?.let { timestamp ->
                val duration = result.durations.lastOrNull() ?: 0f
                startMs + ((timestamp + duration) * 1_000).roundToLong()
            }
            RecognitionSegment(
                startMs = startMs,
                endMs = maxOf(audioEnd, timedEnd ?: startMs),
                text = result.text.trim(),
                language = result.lang.ifBlank { null },
                tokenTimestampsMs = tokenTimes,
            )
        } finally {
            stream.release()
        }
    }

    override fun close() {
        recognizer.release()
    }

    private companion object {
        const val SAMPLE_RATE = 16_000
    }
}
