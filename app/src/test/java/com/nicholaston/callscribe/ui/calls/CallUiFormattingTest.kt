package com.nicholaston.callscribe.ui.calls

import com.nicholaston.callscribe.data.CallDirection
import com.nicholaston.callscribe.data.CallRecord
import com.nicholaston.callscribe.data.CallSource
import com.nicholaston.callscribe.data.CallStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class CallUiFormattingTest {
    @Test
    fun `duration uses compact clock format`() {
        assertEquals("0:00", formatDuration(0))
        assertEquals("1:05", formatDuration(65_000))
        assertEquals("1:01:01", formatDuration(3_661_000))
        assertEquals("Unknown duration", formatDuration(null))
    }

    @Test
    fun `display name prefers contact then number`() {
        val call = CallRecord(
            source = CallSource.IMPORT,
            startedAt = 0,
            direction = CallDirection.INCOMING,
            phoneNumber = "+15551234567",
            contactName = "Alex",
        )

        assertEquals("Alex", call.displayName())
        assertEquals("+15551234567", call.copy(contactName = null).displayName())
        assertEquals(
            "Unknown incoming call",
            call.copy(contactName = null, phoneNumber = null).displayName(),
        )
    }

    @Test
    fun `status labels are user facing`() {
        assertEquals("Transcribing", CallStatus.TRANSCRIBING.displayLabel())
        assertEquals("Transcribed", CallStatus.DONE.displayLabel())
        assertEquals("Failed", CallStatus.FAILED.displayLabel())
    }
}
