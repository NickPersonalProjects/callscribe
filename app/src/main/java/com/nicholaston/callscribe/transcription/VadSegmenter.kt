package com.nicholaston.callscribe.transcription

import android.content.Context
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig

data class SpeechSamples(
    val startMs: Long,
    val samples: FloatArray,
)

class VadSegmenter(
    context: Context,
    threads: Int = 1,
    provider: String = "cpu",
) : AutoCloseable {
    private val vad = Vad(
        context.assets,
        VadModelConfig(
            sileroVadModelConfig = SileroVadModelConfig(
                model = "silero_vad.onnx",
                threshold = 0.5f,
                minSilenceDuration = 0.35f,
                minSpeechDuration = 0.2f,
                windowSize = 512,
                maxSpeechDuration = 25f,
            ),
            sampleRate = SAMPLE_RATE,
            numThreads = threads.coerceAtLeast(1),
            provider = provider,
        ),
    )

    fun segment(samples: FloatArray, sourceStartMs: Long = 0): List<SpeechSamples> {
        if (samples.isEmpty()) return emptyList()
        vad.reset()
        val result = accept(samples, sourceStartMs).toMutableList()
        result += flush(sourceStartMs)
        vad.clear()
        return result
    }

    fun accept(samples: FloatArray, sourceStartMs: Long = 0): List<SpeechSamples> {
        if (samples.isEmpty()) return emptyList()
        vad.acceptWaveform(samples)
        return drain(sourceStartMs)
    }

    fun flush(sourceStartMs: Long = 0): List<SpeechSamples> {
        vad.flush()
        return drain(sourceStartMs)
    }

    private fun drain(sourceStartMs: Long): List<SpeechSamples> = buildList {
        while (!vad.empty()) {
            val segment = vad.front()
            val startMs = sourceStartMs + segment.start * 1_000L / SAMPLE_RATE
            add(SpeechSamples(startMs, segment.samples))
            vad.pop()
        }
    }

    override fun close() {
        vad.release()
    }

    private companion object {
        const val SAMPLE_RATE = 16_000
    }
}
