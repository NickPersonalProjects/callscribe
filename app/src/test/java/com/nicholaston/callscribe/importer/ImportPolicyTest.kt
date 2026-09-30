package com.nicholaston.callscribe.importer

import com.nicholaston.callscribe.data.CallDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportPolicyTest {
    @Test
    fun `audio policy accepts mime type and known extensions`() {
        assertTrue(ImportPolicy.isAudio("recording.bin", "audio/ogg"))
        assertTrue(ImportPolicy.isAudio("recording.M4A", null))
        assertFalse(ImportPolicy.isAudio("recording.json", "application/json"))
    }

    @Test
    fun `call log matcher picks nearest entry within window`() {
        val entries = listOf(
            CallLogEntry(900_000, null, CallDirection.INCOMING, "old", null),
            CallLogEntry(1_005_000, 42_000, CallDirection.OUTGOING, "nearest", "Ada"),
            CallLogEntry(1_010_000, null, CallDirection.INCOMING, "later", null),
        )

        val match = ImportPolicy.matchCallLog(1_000_000, entries)

        assertEquals("nearest", match?.phoneNumber)
        assertEquals(42_000L, match?.durationMs)
    }

    @Test
    fun `call log matcher rejects entries outside window`() {
        assertNull(
            ImportPolicy.matchCallLog(
                1_000_000,
                listOf(CallLogEntry(1_121_000, null, CallDirection.UNKNOWN, null, null)),
            ),
        )
    }

    @Test
    fun `merge preserves parsed metadata and fills missing values`() {
        val merged = ImportPolicy.merge(
            ParsedRecording(
                startedAt = 1_000,
                direction = CallDirection.UNKNOWN,
                contactName = "Filename name",
            ),
            CallLogEntry(
                startedAt = 1_010,
                durationMs = null,
                direction = CallDirection.INCOMING,
                phoneNumber = "+12065550100",
                contactName = "Call log name",
            ),
        )

        assertEquals(1_000L, merged.startedAt)
        assertEquals(CallDirection.INCOMING, merged.direction)
        assertEquals("+12065550100", merged.phoneNumber)
        assertEquals("Filename name", merged.contactName)
    }
}
