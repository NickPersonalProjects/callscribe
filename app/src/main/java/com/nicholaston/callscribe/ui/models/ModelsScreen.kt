package com.nicholaston.callscribe.ui.models

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelsScreen(
    onBack: () -> Unit,
    viewModel: ModelsViewModel = viewModel(),
) {
    val models by viewModel.models.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("On-device models") },
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
                Text(
                    text = "Models are downloaded directly to this phone. Recordings are never uploaded.",
                    modifier = Modifier.padding(vertical = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            items(models, key = { it.model.id }) { state ->
                ModelCard(
                    state = state,
                    onDownload = { viewModel.download(state.model.id) },
                    onDelete = { viewModel.delete(state.model.id) },
                )
            }
        }
    }
}

@Composable
private fun ModelCard(
    state: ModelUiState,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(state.model.displayName, style = MaterialTheme.typography.titleMedium)
            Text("${state.model.details} • ${formatBytes(state.model.downloadBytes)}")
            Text("License: ${state.model.license}", style = MaterialTheme.typography.bodySmall)
            if (state.downloading) {
                LinearProgressIndicator(
                    progress = { state.progress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("${state.progress}% downloaded", style = MaterialTheme.typography.bodySmall)
            }
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    state.installed -> {
                        Text("Installed", color = MaterialTheme.colorScheme.primary)
                        TextButton(onClick = onDelete) { Text("Delete") }
                    }
                    !state.downloading -> Button(onClick = onDownload) { Text("Download") }
                }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String =
    String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
