package com.nicholaston.callscribe.transcription

import com.k2fsa.sherpa.onnx.FastClusteringConfig
import com.k2fsa.sherpa.onnx.OfflineSpeakerDiarization
import com.k2fsa.sherpa.onnx.OfflineSpeakerDiarizationConfig
import com.k2fsa.sherpa.onnx.OfflineSpeakerSegmentationModelConfig
import com.k2fsa.sherpa.onnx.OfflineSpeakerSegmentationPyannoteModelConfig
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractorConfig
import com.nicholaston.callscribe.data.TranscriptSegment
import com.nicholaston.callscribe.models.ModelRegistry
import com.nicholaston.callscribe.models.ModelStore
import java.io.File
import kotlin.math.roundToLong

data class SpeakerTurn(
    val startMs: Long,
    val endMs: Long,
    val speaker: Int,
    val confidence: Float,
)

class SherpaSpeakerDiarizer(
    segmentationModel: File,
    embeddingModel: File,
    threads: Int = 2,
) : AutoCloseable {
    private val diarizer: OfflineSpeakerDiarization

    init {
        require(segmentationModel.isFile) { "Missing segmentation model: $segmentationModel" }
        require(embeddingModel.isFile) { "Missing speaker embedding model: $embeddingModel" }
        diarizer = OfflineSpeakerDiarization(
            config = OfflineSpeakerDiarizationConfig(
                segmentation = OfflineSpeakerSegmentationModelConfig(
                    pyannote = OfflineSpeakerSegmentationPyannoteModelConfig(
                        model = segmentationModel.absolutePath,
                        windowShiftRatio = 0.1f,
                    ),
                    numThreads = threads.coerceIn(1, 4),
                    provider = "cpu",
                ),
                embedding = SpeakerEmbeddingExtractorConfig(
                    model = embeddingModel.absolutePath,
                    numThreads = threads.coerceIn(1, 4),
                    provider = "cpu",
                ),
                clustering = FastClusteringConfig(
                    numClusters = 2,
                    threshold = 0.5f,
                    computeConfidence = true,
                ),
                minDurationOn = 0.2f,
                minDurationOff = 0.5f,
            ),
        )
        check(diarizer.sampleRate() == SAMPLE_RATE) {
            "Unexpected diarization sample rate: ${diarizer.sampleRate()}"
        }
    }

    fun process(samples: FloatArray): List<SpeakerTurn> =
        diarizer.process(samples).map { segment ->
            SpeakerTurn(
                startMs = (segment.start * 1_000).roundToLong(),
                endMs = (segment.end * 1_000).roundToLong(),
                speaker = segment.speaker,
                confidence = segment.confidence,
            )
        }

    override fun close() {
        diarizer.release()
    }

    companion object {
        private const val SAMPLE_RATE = 16_000

        fun fromStore(store: ModelStore, threads: Int = 2): SherpaSpeakerDiarizer {
            val model = ModelRegistry.diarization
            check(store.isInstalled(model)) { "${model.displayName} is not installed" }
            val segmentation = model.files.single { it.name == model.segmentationFile }
            val embedding = model.files.single { it.name == model.embeddingFile }
            return SherpaSpeakerDiarizer(
                segmentationModel = store.file(model, segmentation),
                embeddingModel = store.file(model, embedding),
                threads = threads,
            )
        }
    }
}

object DiarizationAssigner {
    fun assign(
        transcript: List<TranscriptSegment>,
        turns: List<SpeakerTurn>,
    ): List<TranscriptSegment> = transcript.map { segment ->
        val speaker = turns.maxByOrNull { turn -> overlapMs(segment, turn) }
            ?.takeIf { overlapMs(segment, it) > 0 }
            ?.speaker
        segment.copy(speaker = speaker?.let { "Speaker ${'A' + it.coerceAtLeast(0)}" })
    }

    private fun overlapMs(segment: TranscriptSegment, turn: SpeakerTurn): Long =
        (minOf(segment.endMs, turn.endMs) - maxOf(segment.startMs, turn.startMs)).coerceAtLeast(0)
}
