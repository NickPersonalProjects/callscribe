package com.nicholaston.callscribe.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ModelTraining
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nicholaston.callscribe.data.CallScribeSettings
import com.nicholaston.callscribe.data.SettingsSnapshot
import com.nicholaston.callscribe.data.SpeakerMode
import com.nicholaston.callscribe.data.StorageMode
import com.nicholaston.callscribe.data.TranscriptionTiming
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CallScribeSettingsScreen(
    settings: CallScribeSettings,
    onBack: () -> Unit,
    onOpenModels: () -> Unit,
    onOpenUpstreamSettings: () -> Unit,
) {
    val values by settings.values.collectAsState(initial = SettingsSnapshot())
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("CallScribe settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SettingsCard("Transcription") {
                    EnumButtons(
                        label = "Timing",
                        selected = values.transcriptionTiming,
                        values = TranscriptionTiming.entries,
                        title = { it.displayName() },
                        onSelect = { scope.launch { settings.setTranscriptionTiming(it) } },
                    )
                    Text("Worker threads: ${values.threadCount}")
                    Slider(
                        value = values.threadCount.toFloat(),
                        onValueChange = {
                            scope.launch { settings.setThreadCount(it.toInt().coerceIn(1, 8)) }
                        },
                        valueRange = 1f..8f,
                        steps = 6,
                    )
                    EnumButtons(
                        label = "Provider",
                        selected = values.provider,
                        values = listOf("cpu", "nnapi"),
                        title = { it.uppercase() },
                        onSelect = { scope.launch { settings.setProvider(it) } },
                    )
                    EnumButtons(
                        label = "Speaker mode",
                        selected = values.speakerMode,
                        values = SpeakerMode.entries,
                        title = { it.displayName() },
                        onSelect = { scope.launch { settings.setSpeakerMode(it) } },
                    )
                    ListItem(
                        headlineContent = { Text("Speech models") },
                        supportingContent = { Text(values.defaultModelId) },
                        leadingContent = { Icon(Icons.Default.ModelTraining, null) },
                        trailingContent = { Icon(Icons.Default.ChevronRight, null) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(onClick = onOpenModels, modifier = Modifier.fillMaxWidth()) {
                        Text("Manage models")
                    }
                }
            }
            item {
                SettingsCard("Privacy and storage") {
                    RetentionField(
                        label = "Delete audio after days",
                        value = values.retentionAudioDays,
                        onValue = { scope.launch { settings.setRetentionAudioDays(it) } },
                    )
                    RetentionField(
                        label = "Delete call history after days",
                        value = values.retentionCallDays,
                        onValue = { scope.launch { settings.setRetentionCallDays(it) } },
                    )
                    ToggleRow(
                        "Consent reminder",
                        "Show a heads-up reminder when a call will be recorded.",
                        values.consentReminder,
                    ) { scope.launch { settings.setConsentReminder(it) } }
                    EnumButtons(
                        label = "Recording storage",
                        selected = values.storageMode,
                        values = StorageMode.entries,
                        title = { it.displayName() },
                        onSelect = { scope.launch { settings.setStorageMode(it) } },
                    )
                    ToggleRow(
                        "Wi-Fi-only model downloads",
                        "Avoid downloading speech models on metered mobile data.",
                        values.wifiOnlyDownloads,
                    ) { scope.launch { settings.setWifiOnlyDownloads(it) } }
                }
            }
            item {
                SettingsCard("Recording and app") {
                    Text(
                        "Upstream recording rules, audio, Shizuku auto-management, appearance, " +
                            "security, and debug settings remain available.",
                    )
                    OutlinedButton(
                        onClick = onOpenUpstreamSettings,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Open recording & app settings")
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun <T> EnumButtons(
    label: String,
    selected: T,
    values: List<T>,
    title: (T) -> String,
    onSelect: (T) -> Unit,
) {
    Text(label, style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        values.forEach { value ->
            if (value == selected) {
                Button(onClick = { onSelect(value) }) { Text(title(value)) }
            } else {
                OutlinedButton(onClick = { onSelect(value) }) { Text(title(value)) }
            }
        }
    }
}

@Composable
private fun RetentionField(label: String, value: Long?, onValue: (Long?) -> Unit) {
    OutlinedTextField(
        value = value?.toString().orEmpty(),
        onValueChange = { text ->
            if (text.isBlank()) onValue(null)
            else text.toLongOrNull()?.takeIf { it > 0 }?.let(onValue)
        },
        label = { Text(label) },
        supportingText = { Text("Leave blank to keep indefinitely") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ToggleRow(label: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = { Text(description) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
    )
}

private fun TranscriptionTiming.displayName() = when (this) {
    TranscriptionTiming.AFTER_CALL -> "After call"
    TranscriptionTiming.CHARGING_ONLY -> "Charging"
    TranscriptionTiming.MANUAL -> "Manual"
}

private fun SpeakerMode.displayName() = name.lowercase().replaceFirstChar(Char::uppercase)

private fun StorageMode.displayName() = when (this) {
    StorageMode.APP_PRIVATE -> "App private"
    StorageMode.SAF_FOLDER -> "Selected folder"
}
