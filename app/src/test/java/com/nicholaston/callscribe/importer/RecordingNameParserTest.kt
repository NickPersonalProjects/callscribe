package com.nicholaston.callscribe.importer

import com.nicholaston.callscribe.data.CallDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.OffsetDateTime

class RecordingNameParserTest {
    @Test
    fun `parses BCR compatible file name`() {
        val result = BcrRecordingParser.parse(
            "20230414_215701.088-0400_out_sim2_+12065550100_[Ada_Lovelace].ogg",
        )

        assertNotNull(result)
        assertEquals(CallDirection.OUTGOING, result?.direction)
        assertEquals(2, result?.simSlot)
        assertEquals("+12065550100", result?.phoneNumber)
        assertEquals("Ada Lovelace", result?.contactName)
        assertEquals(
            OffsetDateTime.parse("2023-04-14T21:57:01.088-04:00").toInstant().toEpochMilli(),
            result?.startedAt,
        )
    }

    @Test
    fun `sidecar timestamp overrides filename`() {
        val result = BcrRecordingParser.mergeSidecar(
            "20230414_215701.088-0400_in_12065550100.opus",
            """{"timestamp_unix_ms":"1700000000000"}""",
        )

        assertEquals(1_700_000_000_000, result?.startedAt)
        assertEquals(CallDirection.INCOMING, result?.direction)
    }

    @Test
    fun `sidecar fills optional BCR metadata`() {
        val result = BcrRecordingParser.mergeSidecar(
            "unknown.opus",
            """
                {
                  "timestamp_unix_ms": 1700000000000,
                  "direction": "outgoing",
                  "phone_number": "+12065550100",
                  "contact_name": "Ada Lovelace",
                  "sim_slot": 2
                }
            """.trimIndent(),
        )

        assertEquals(1_700_000_000_000, result?.startedAt)
        assertEquals(CallDirection.OUTGOING, result?.direction)
        assertEquals("+12065550100", result?.phoneNumber)
        assertEquals("Ada Lovelace", result?.contactName)
        assertEquals(2, result?.simSlot)
    }

    @Test
    fun `parses Samsung style name`() {
        val result = SamsungRecordingParser.parse("Call recording Ada Lovelace_20260930_114500.m4a")

        assertEquals("Ada Lovelace", result?.contactName)
        assertNotNull(result?.startedAt)
    }
}
