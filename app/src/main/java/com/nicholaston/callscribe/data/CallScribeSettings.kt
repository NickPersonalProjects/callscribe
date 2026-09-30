package com.nicholaston.callscribe.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.callScribeDataStore by preferencesDataStore("callscribe_settings")

data class SettingsSnapshot(
    val transcriptionTiming: TranscriptionTiming = TranscriptionTiming.AFTER_CALL,
    val defaultModelId: String = "parakeet-tdt-0.6b-v3-int8",
    val threadCount: Int = 4,
    val provider: String = "cpu",
    val speakerMode: SpeakerMode = SpeakerMode.AUTO,
    val retentionAudioDays: Long? = null,
    val retentionCallDays: Long? = null,
    val consentReminder: Boolean = true,
    val storageMode: StorageMode = StorageMode.APP_PRIVATE,
    val wifiOnlyDownloads: Boolean = true,
)

enum class TranscriptionTiming { AFTER_CALL, CHARGING_ONLY, MANUAL }
enum class SpeakerMode { AUTO, CHANNELS, DIARIZATION, OFF }
enum class StorageMode { APP_PRIVATE, SAF_FOLDER }

class CallScribeSettings(private val context: Context) {
    private object Keys {
        val timing = stringPreferencesKey("transcription_timing")
        val model = stringPreferencesKey("default_model_id")
        val threads = intPreferencesKey("thread_count")
        val provider = stringPreferencesKey("execution_provider")
        val speakerMode = stringPreferencesKey("speaker_mode")
        val retentionAudioDays = longPreferencesKey("retention_audio_days")
        val retentionCallDays = longPreferencesKey("retention_call_days")
        val consentReminder = booleanPreferencesKey("consent_reminder")
        val storageMode = stringPreferencesKey("storage_mode")
        val wifiOnly = booleanPreferencesKey("wifi_only_downloads")
    }

    val values: Flow<SettingsSnapshot> = context.callScribeDataStore.data.map { preferences ->
        SettingsSnapshot(
            transcriptionTiming = enumOrDefault(
                preferences[Keys.timing],
                TranscriptionTiming.AFTER_CALL,
            ),
            defaultModelId = preferences[Keys.model] ?: "parakeet-tdt-0.6b-v3-int8",
            threadCount = (preferences[Keys.threads] ?: 4).coerceIn(1, 8),
            provider = preferences[Keys.provider] ?: "cpu",
            speakerMode = enumOrDefault(preferences[Keys.speakerMode], SpeakerMode.AUTO),
            retentionAudioDays = preferences[Keys.retentionAudioDays],
            retentionCallDays = preferences[Keys.retentionCallDays],
            consentReminder = preferences[Keys.consentReminder] ?: true,
            storageMode = enumOrDefault(preferences[Keys.storageMode], StorageMode.APP_PRIVATE),
            wifiOnlyDownloads = preferences[Keys.wifiOnly] ?: true,
        )
    }

    suspend fun setTranscriptionTiming(value: TranscriptionTiming) = update(Keys.timing, value.name)
    suspend fun setDefaultModel(value: String) = update(Keys.model, value)
    suspend fun setThreadCount(value: Int) = update(Keys.threads, value.coerceIn(1, 8))
    suspend fun setProvider(value: String) = update(Keys.provider, value)
    suspend fun setSpeakerMode(value: SpeakerMode) = update(Keys.speakerMode, value.name)
    suspend fun setRetentionAudioDays(value: Long?) = updateOptional(Keys.retentionAudioDays, value)
    suspend fun setRetentionCallDays(value: Long?) = updateOptional(Keys.retentionCallDays, value)
    suspend fun setConsentReminder(value: Boolean) = update(Keys.consentReminder, value)
    suspend fun setStorageMode(value: StorageMode) = update(Keys.storageMode, value.name)
    suspend fun setWifiOnlyDownloads(value: Boolean) = update(Keys.wifiOnly, value)

    private suspend fun <T> update(key: androidx.datastore.preferences.core.Preferences.Key<T>, value: T) {
        context.callScribeDataStore.edit { it[key] = value }
    }

    private suspend fun <T> updateOptional(
        key: androidx.datastore.preferences.core.Preferences.Key<T>,
        value: T?,
    ) {
        context.callScribeDataStore.edit {
            if (value == null) it.remove(key) else it[key] = value
        }
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(value: String?, default: T): T =
        value?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default
}
