package com.nicholaston.callscribe.models

data class ModelFile(
    val name: String,
    val bytes: Long,
    val sha256: String,
    val url: String? = null,
)

interface DownloadableModel {
    val id: String
    val displayName: String
    val license: String
    val files: List<ModelFile>
    val details: String
    val downloadBytes: Long

    fun downloadUrl(file: ModelFile): String
}

data class SpeechModel(
    override val id: String,
    override val displayName: String,
    val repository: String,
    val revision: String,
    override val license: String,
    override val files: List<ModelFile>,
    val modelType: String,
    val languages: String,
    val architecture: ModelArchitecture,
    val encoderFile: String,
    val decoderFile: String,
    val joinerFile: String? = null,
    val tokensFile: String,
    val languageHint: String = "",
) : DownloadableModel {
    override val details: String = languages
    override val downloadBytes: Long = files.sumOf(ModelFile::bytes)

    override fun downloadUrl(file: ModelFile): String =
        file.url ?: "https://huggingface.co/$repository/resolve/$revision/${file.name}"
}

data class DiarizationModel(
    override val id: String,
    override val displayName: String,
    override val license: String,
    override val files: List<ModelFile>,
    val segmentationFile: String,
    val embeddingFile: String,
) : DownloadableModel {
    override val details: String = "Two-speaker labels for mono recordings"
    override val downloadBytes: Long = files.sumOf(ModelFile::bytes)

    override fun downloadUrl(file: ModelFile): String =
        requireNotNull(file.url) { "Missing download URL for ${file.name}" }
}

enum class ModelArchitecture { PARAKEET_TRANSDUCER, WHISPER }

object ModelRegistry {
    const val DEFAULT_MODEL_ID = "parakeet-tdt-0.6b-v3-int8"
    const val DIARIZATION_MODEL_ID = "pyannote-wespeaker-diarization"

    val models: List<SpeechModel> = listOf(
        SpeechModel(
            id = DEFAULT_MODEL_ID,
            displayName = "Parakeet TDT 0.6B v3 (int8)",
            repository = "csukuangfj/sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8",
            revision = "2bda32ec70b097a55adaa07d9a7173915b43cc78",
            license = "CC-BY-4.0",
            modelType = "nemo_transducer",
            languages = "25 European languages",
            architecture = ModelArchitecture.PARAKEET_TRANSDUCER,
            encoderFile = "encoder.int8.onnx",
            decoderFile = "decoder.int8.onnx",
            joinerFile = "joiner.int8.onnx",
            tokensFile = "tokens.txt",
            files = listOf(
                ModelFile(
                    name = "encoder.int8.onnx",
                    bytes = 652_184_281,
                    sha256 = "acfc2b4456377e15d04f0243af540b7fe7c992f8d898d751cf134c3a55fd2247",
                ),
                ModelFile(
                    name = "decoder.int8.onnx",
                    bytes = 11_845_275,
                    sha256 = "179e50c43d1a9de79c8a24149a2f9bac6eb5981823f2a2ed88d655b24248db4e",
                ),
                ModelFile(
                    name = "joiner.int8.onnx",
                    bytes = 6_355_277,
                    sha256 = "3164c13fc2821009440d20fcb5fdc78bff28b4db2f8d0f0b329101719c0948b3",
                ),
                ModelFile(
                    name = "tokens.txt",
                    bytes = 93_939,
                    sha256 = "d58544679ea4bc6ac563d1f545eb7d474bd6cfa467f0a6e2c1dc1c7d37e3c35d",
                ),
            ),
        ),
        SpeechModel(
            id = "whisper-tiny-int8",
            displayName = "Whisper tiny multilingual (int8)",
            repository = "csukuangfj/sherpa-onnx-whisper-tiny",
            revision = "65176e2deb88badc814a94058666cadccc29b61c",
            license = "MIT",
            modelType = "whisper",
            languages = "Multilingual",
            architecture = ModelArchitecture.WHISPER,
            encoderFile = "tiny-encoder.int8.onnx",
            decoderFile = "tiny-decoder.int8.onnx",
            tokensFile = "tiny-tokens.txt",
            files = listOf(
                ModelFile(
                    name = "tiny-encoder.int8.onnx",
                    bytes = 12_937_772,
                    sha256 = "d24fb083ae3b1041fc24e97971d60e280c9342201fbb67b0ab428a8b4a51a434",
                ),
                ModelFile(
                    name = "tiny-decoder.int8.onnx",
                    bytes = 89_855_401,
                    sha256 = "d2fece8dd42771f1df975c6c0445770d0c292bf7547c2cae04a6c0cc57540925",
                ),
                ModelFile(
                    name = "tiny-tokens.txt",
                    bytes = 816_730,
                    sha256 = "b34b360dbb493e781e479794586d661700670d65564001f23024971d1f2fa126",
                ),
            ),
        ),
    )

    val diarization = DiarizationModel(
        id = DIARIZATION_MODEL_ID,
        displayName = "Two-speaker diarization",
        license = "MIT / Apache-2.0",
        segmentationFile = "segmentation.int8.onnx",
        embeddingFile = "embedding.onnx",
        files = listOf(
            ModelFile(
                name = "segmentation.int8.onnx",
                bytes = 1_540_506,
                sha256 = "d582f4b4c6b48205de7e0643c57df0df5615a3c176189be3fc461e9d18827b5d",
                url = "https://huggingface.co/csukuangfj/" +
                    "sherpa-onnx-pyannote-segmentation-3-0/resolve/main/model.int8.onnx",
            ),
            ModelFile(
                name = "embedding.onnx",
                bytes = 26_530_550,
                sha256 = "e9848563da86f263117134dfd7ad63c92355b37de492b55e325400c9d9c39012",
                url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/" +
                    "speaker-recongition-models/wespeaker_en_voxceleb_resnet34_LM.onnx",
            ),
        ),
    )

    val downloadableModels: List<DownloadableModel> = models + diarization

    fun require(id: String): SpeechModel =
        models.firstOrNull { it.id == id } ?: error("Unknown speech model: $id")

    fun requireDownloadable(id: String): DownloadableModel =
        downloadableModels.firstOrNull { it.id == id } ?: error("Unknown model: $id")
}
