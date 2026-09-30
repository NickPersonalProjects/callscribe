package com.nicholaston.callscribe.transcription

data class RecognitionSegment(
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val language: String?,
    val tokenTimestampsMs: List<Long>,
)

interface SpeechEngine : AutoCloseable {
    fun transcribe(samples: FloatArray, sampleRate: Int, startMs: Long = 0): RecognitionSegment
}
