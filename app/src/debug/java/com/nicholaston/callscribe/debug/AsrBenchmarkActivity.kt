package com.nicholaston.callscribe.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig
import com.k2fsa.sherpa.onnx.WaveReader
import com.nicholaston.callscribe.transcription.StreamingResampler
import com.nicholaston.callscribe.transcription.VadSegmenter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.system.measureNanoTime

class AsrBenchmarkActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = File(getExternalFilesDir(null), "benchmark")
        val defaultModel = File(root, "models/parakeet-tdt-0.6b-v3-int8")
        val defaultWave = File(root, "input.wav")

        setContent {
            MaterialTheme {
                var output by remember {
                    mutableStateOf(
                        "Push a 16-bit WAV to ${defaultWave.absolutePath}\n" +
                            "and model files to ${defaultModel.absolutePath}",
                    )
                }
                var running by remember { mutableStateOf(false) }
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text("CallScribe ASR benchmark", style = MaterialTheme.typography.headlineSmall)
                        Text(output)
                        Button(
                            enabled = !running,
                            onClick = {
                                running = true
                                lifecycleScope.launch {
                                    output = runCatching {
                                        withContext(Dispatchers.Default) {
                                            benchmark(
                                                modelDirectory = File(
                                                    intent.getStringExtra(EXTRA_MODEL_DIR)
                                                        ?: defaultModel.absolutePath,
                                                ),
                                                waveFile = File(
                                                    intent.getStringExtra(EXTRA_WAVE_FILE)
                                                        ?: defaultWave.absolutePath,
                                                ),
                                                threads = intent.getIntExtra(EXTRA_THREADS, 4),
                                                provider = intent.getStringExtra(EXTRA_PROVIDER) ?: "cpu",
                                            )
                                        }
                                    }.fold(
                                        onSuccess = { it },
                                        onFailure = { "${it::class.java.simpleName}: ${it.message}" },
                                    )
                                    running = false
                                }
                            },
                        ) {
                            Text(if (running) "Running..." else "Run benchmark")
                        }
                    }
                }
            }
        }
    }

    private fun benchmark(
        modelDirectory: File,
        waveFile: File,
        threads: Int,
        provider: String,
    ): String {
        require(waveFile.isFile) { "Missing WAV: $waveFile" }
        val required = listOf(
            "encoder.int8.onnx",
            "decoder.int8.onnx",
            "joiner.int8.onnx",
            "tokens.txt",
        )
        required.forEach { require(File(modelDirectory, it).isFile) { "Missing model file: $it" } }

        val wave = WaveReader.readWave(waveFile.absolutePath)
        val samples = if (wave.sampleRate == SAMPLE_RATE) {
            wave.samples
        } else {
            val resampler = StreamingResampler(wave.sampleRate, SAMPLE_RATE)
            resampler.process(wave.samples) + resampler.flush()
        }

        lateinit var recognizer: OfflineRecognizer
        val loadNanos = measureNanoTime {
            recognizer = OfflineRecognizer(
                config = OfflineRecognizerConfig(
                    modelConfig = OfflineModelConfig(
                        transducer = OfflineTransducerModelConfig(
                            encoder = File(modelDirectory, "encoder.int8.onnx").absolutePath,
                            decoder = File(modelDirectory, "decoder.int8.onnx").absolutePath,
                            joiner = File(modelDirectory, "joiner.int8.onnx").absolutePath,
                        ),
                        numThreads = threads.coerceIn(1, 8),
                        provider = provider,
                        modelType = "nemo_transducer",
                        tokens = File(modelDirectory, "tokens.txt").absolutePath,
                    ),
                ),
            )
        }

        val transcript = StringBuilder()
        val decodeNanos = measureNanoTime {
            VadSegmenter(this, provider = provider).use { vad ->
                vad.segment(samples).forEach { speech ->
                    val stream = recognizer.createStream()
                    try {
                        stream.acceptWaveform(speech.samples, SAMPLE_RATE)
                        recognizer.decode(stream)
                        val text = recognizer.getResult(stream).text.trim()
                        if (text.isNotEmpty()) transcript.appendLine(text)
                    } finally {
                        stream.release()
                    }
                }
            }
        }
        recognizer.release()

        val audioSeconds = samples.size.toDouble() / SAMPLE_RATE
        val decodeSeconds = decodeNanos / 1_000_000_000.0
        val rtf = if (audioSeconds == 0.0) 0.0 else decodeSeconds / audioSeconds
        return buildString {
            appendLine("Model: ${modelDirectory.name}")
            appendLine("Threads/provider: $threads / $provider")
            appendLine("Audio: %.2f s".format(audioSeconds))
            appendLine("Load: %.2f s".format(loadNanos / 1_000_000_000.0))
            appendLine("Decode: %.2f s".format(decodeSeconds))
            appendLine("RTF: %.3f".format(rtf))
            appendLine("Peak RSS: ${peakRss() ?: "unknown"}")
            appendLine()
            append(transcript.toString().ifBlank { "(no speech recognized)" })
        }
    }

    private fun peakRss(): String? =
        runCatching {
            File("/proc/self/status").useLines { lines ->
                lines.firstOrNull { it.startsWith("VmHWM:") }?.substringAfter(':')?.trim()
            }
        }.getOrNull()

    companion object {
        const val EXTRA_MODEL_DIR = "model_dir"
        const val EXTRA_WAVE_FILE = "wave_file"
        const val EXTRA_THREADS = "threads"
        const val EXTRA_PROVIDER = "provider"
        private const val SAMPLE_RATE = 16_000
    }
}
