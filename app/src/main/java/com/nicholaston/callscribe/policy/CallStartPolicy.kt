package com.nicholaston.callscribe.policy

data class CallStartEffects(
    val startRecording: Boolean,
    val showConsentReminder: Boolean,
)

object CallStartPolicy {
    fun effects(shouldRecord: Boolean, consentReminderEnabled: Boolean) = CallStartEffects(
        startRecording = shouldRecord,
        showConsentReminder = shouldRecord && consentReminderEnabled,
    )
}
