package com.nicholaston.callscribe.transcription

import com.nicholaston.callscribe.data.TranscriptSegment
import org.junit.Assert.assertEquals
import org.junit.Test

class DiarizationAssignerTest {
    @Test
    fun `assigns speaker with greatest time overlap`() {
        val transcript = listOf(segment(0, 1_000), segment(1_000, 2_000))
        val turns = listOf(
            SpeakerTurn(0, 800, speaker = 0, confidence = 0.9f),
            SpeakerTurn(700, 2_000, speaker = 1, confidence = 0.8f),
        )

        assertEquals(
            listOf("Speaker A", "Speaker B"),
            DiarizationAssigner.assign(transcript, turns).map { it.speaker },
        )
    }

    private fun segment(start: Long, end: Long) = TranscriptSegment(
        callId = 1,
        index = start.toInt(),
        startMs = start,
        endMs = end,
        text = "text",
    )
}
