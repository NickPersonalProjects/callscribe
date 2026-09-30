package com.nicholaston.callscribe.export

import com.nicholaston.callscribe.data.CallDirection
import com.nicholaston.callscribe.data.CallRecord
import com.nicholaston.callscribe.data.CallSource
import com.nicholaston.callscribe.data.TranscriptSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TranscriptExporterTest {
    private val call = CallRecord(
        id = 7,
        source = CallSource.SHIZUKU,
        startedAt = 0,
        durationMs = 65_432,
        direction = CallDirection.OUTGOING,
        contactName = "Ada *Lovelace*",
        modelId = "parakeet",
    )
    private val segments = listOf(
        TranscriptSegment(
            id = 1,
            callId = 7,
            index = 0,
            startMs = 1_234,
            endMs = 4_567,
            speaker = "Me",
            text = "Project update",
        ),
    )

    @Test
    fun `SRT uses comma millisecond timecodes`() {
        assertEquals(
            "1\n00:00:01,234 --> 00:00:04,567\n[Me] Project update",
            TranscriptExporter.srt(segments),
        )
    }

    @Test
    fun `Markdown escapes formatting characters`() {
        val output = TranscriptExporter.markdown(call, segments)

        assertTrue(output.startsWith("# Ada \\*Lovelace\\*"))
        assertTrue(output.contains("**Me:** [00:00:01] Project update"))
    }
}
