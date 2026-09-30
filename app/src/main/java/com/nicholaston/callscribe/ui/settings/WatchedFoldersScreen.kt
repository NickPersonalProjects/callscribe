package com.nicholaston.callscribe.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kitsumed.shizucallrecorder.ShizuApplication
import com.nicholaston.callscribe.importer.WatchedFolderManager
import com.nicholaston.callscribe.work.ImportScanScheduler
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchedFoldersScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val application = context.applicationContext as ShizuApplication
    val folders by application.callScribeContainer.database.importDao()
        .observeFolders()
        .collectAsState(initial = emptyList())
    val manager = remember { WatchedFolderManager(context.applicationContext) }
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            scope.launch {
                if (manager.add(uri)) ImportScanScheduler.scanNow(context)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Watched folders") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { ImportScanScheduler.scanNow(context) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Scan now")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("CallScribe recursively scans these folders every 6 hours and after calls.")
            Button(onClick = { picker.launch(null) }) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text("Add folder", modifier = Modifier.padding(start = 8.dp))
            }
            if (folders.isEmpty()) {
                Text("No watched folders configured.")
            } else {
                LazyColumn {
                    items(folders, key = { it.id }) { folder ->
                        ListItem(
                            headlineContent = { Text(folder.label) },
                            supportingContent = {
                                Text(
                                    folder.lastScanAt?.let {
                                        "Last scanned ${DateFormat.getDateTimeInstance().format(Date(it))}"
                                    } ?: "Not scanned yet",
                                )
                            },
                            trailingContent = {
                                IconButton(onClick = { scope.launch { manager.remove(folder) } }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Stop watching")
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}
