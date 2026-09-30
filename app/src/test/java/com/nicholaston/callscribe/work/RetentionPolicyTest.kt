package com.nicholaston.callscribe.work

import com.nicholaston.callscribe.data.CallDirection
import com.nicholaston.callscribe.data.CallRecord
import com.nicholaston.callscribe.data.CallSource
import org.junit.Assert.assertEquals
import org.junit.Test

class RetentionPolicyTest {
    private val day = 24L * 60 * 60 * 1_000

    @Test
    fun `call deletion takes precedence over audio deletion`() {
        val call = call(startedAt = 0, audioUri = "file:///recording.ogg")

        assertEquals(
            RetentionDecision(deleteCall = true, deleteAudio = false),
            RetentionPolicy.decide(call, now = 40 * day, audioDays = 7, callDays = 30),
        )
    }

    @Test
    fun `pinned calls are never deleted`() {
        val call = call(startedAt = 0, audioUri = "file:///recording.ogg", keepForever = true)

        assertEquals(
            RetentionDecision(deleteCall = false, deleteAudio = false),
            RetentionPolicy.decide(call, now = 400 * day, audioDays = 7, callDays = 30),
        )
    }

    private fun call(
        startedAt: Long,
        audioUri: String?,
        keepForever: Boolean = false,
    ) = CallRecord(
        id = 1,
        source = CallSource.SHIZUKU,
        startedAt = startedAt,
        direction = CallDirection.INCOMING,
        audioUri = audioUri,
        keepForever = keepForever,
    )
}
