package com.nicholaston.callscribe.policy

import org.junit.Assert.assertEquals
import org.junit.Test

class CallStartPolicyTest {
    @Test
    fun `consent reminder is only shown for calls that will be recorded`() {
        assertEquals(
            CallStartEffects(startRecording = true, showConsentReminder = true),
            CallStartPolicy.effects(shouldRecord = true, consentReminderEnabled = true),
        )
        assertEquals(
            CallStartEffects(startRecording = false, showConsentReminder = false),
            CallStartPolicy.effects(shouldRecord = false, consentReminderEnabled = true),
        )
        assertEquals(
            CallStartEffects(startRecording = true, showConsentReminder = false),
            CallStartPolicy.effects(shouldRecord = true, consentReminderEnabled = false),
        )
    }
}
