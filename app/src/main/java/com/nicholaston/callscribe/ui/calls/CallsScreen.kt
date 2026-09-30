package com.nicholaston.callscribe.ui.calls

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ModelTraining
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.kitsumed.shizucallrecorder.system.openShizukuManager
import com.nicholaston.callscribe.data.CallRecord
import com.nicholaston.callscribe.data.CallStatus
import com.nicholaston.callscribe.data.CallWithSnippet
import com.nicholaston.callscribe.shizuku.ShizukuHealth
import com.nicholaston.callscribe.shizuku.ShizukuHealthMonitor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallsScreen(
    repository: com.nicholaston.callscribe.data.CallRepository,
    onOpenCall: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenModels: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    val viewModel: CallsViewModel = viewModel(factory = CallsViewModel.factory(repository))
    val query by viewModel.query.collectAsState()
    val calls by viewModel.calls.collectAsState()
    var menuExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val shizukuMonitor = remember { ShizukuHealthMonitor(context.applicationContext) }
    val shizukuHealth by shizukuMonitor.status.collectAsState()
    DisposableEffect(shizukuMonitor) {
        shizukuMonitor.start()
        onDispose { shizukuMonitor.stop() }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { shizukuMonitor.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Calls") },
                actions = {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Open navigation menu")
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Settings") },
                            leadingIcon = { Icon(Icons.Default.Settings, null) },
                            onClick = {
                                menuExpanded = false
                                onOpenSettings()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Speech models") },
                            leadingIcon = { Icon(Icons.Default.ModelTraining, null) },
                            onClick = {
                                menuExpanded = false
                                onOpenModels()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("About") },
                            leadingIcon = { Icon(Icons.Default.Info, null) },
                            onClick = {
                                menuExpanded = false
                                onOpenAbout()
                            },
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            ShizukuStatusCard(
                health = shizukuHealth,
                onAction = context::openShizukuManager,
            )
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::setQuery,
                placeholder = { Text("Search calls and transcripts") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (calls.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        if (query.isBlank()) "No calls yet" else "No calls match “$query”",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        if (query.isBlank()) {
                            "Recorded and imported calls will appear here."
                        } else {
                            "Try a contact, phone number, or words from a transcript."
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item { Spacer(Modifier.width(1.dp)) }
                    items(calls, key = { it.call.id }) { result ->
                        CallCard(result = result, onClick = { onOpenCall(result.call.id) })
                    }
                    item { Spacer(Modifier.width(1.dp)) }
                }
            }
        }
    }
}

@Composable
private fun ShizukuStatusCard(health: ShizukuHealth, onAction: () -> Unit) {
    val running = health == ShizukuHealth.RUNNING
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).clickable(onClick = onAction),
    ) {
        ListItem(
            headlineContent = {
                Text(if (running) "Shizuku ready" else "Shizuku action required")
            },
            supportingContent = {
                Text(
                    when (health) {
                        ShizukuHealth.RUNNING -> "Calls can be recorded."
                        ShizukuHealth.PERMISSION_REQUIRED -> "Grant CallScribe access in Shizuku."
                        ShizukuHealth.NOT_RUNNING ->
                            "Start Shizuku. The thedjchi fork can restart at boot; reboot still requires verification."
                    },
                )
            },
            leadingContent = {
                Icon(
                    if (running) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
            },
            trailingContent = { Text(if (running) "Open" else "Fix") },
        )
    }
}

@Composable
private fun CallCard(result: CallWithSnippet, onClick: () -> Unit) {
    val call = result.call
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    call.displayName(),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    call.status.displayLabel(),
                    style = MaterialTheme.typography.labelMedium,
                    color = statusColor(call),
                )
            }
            call.phoneNumber
                ?.takeIf { it.isNotBlank() && it != call.displayName() }
                ?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            Text(
                "${formatCallDate(call.startedAt)} • ${formatDuration(call.durationMs)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            result.snippet?.takeIf(String::isNotBlank)?.let {
                Text(
                    it.replace("[", "").replace("]", ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
            if (call.status == CallStatus.TRANSCRIBING || call.status == CallStatus.QUEUED) {
                LinearProgressIndicator(
                    progress = { call.progress.coerceIn(0, 100) / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("${call.progress.coerceIn(0, 100)}%", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun statusColor(call: CallRecord) = when (call.status) {
    CallStatus.FAILED -> MaterialTheme.colorScheme.error
    CallStatus.DONE -> MaterialTheme.colorScheme.primary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
