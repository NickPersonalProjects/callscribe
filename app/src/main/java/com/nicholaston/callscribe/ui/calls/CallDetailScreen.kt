package com.nicholaston.callscribe.ui.calls

import android.net.Uri
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.nicholaston.callscribe.data.CallRecord
import com.nicholaston.callscribe.data.CallRepository
import com.nicholaston.callscribe.data.CallStatus
import com.nicholaston.callscribe.data.TranscriptSegment
import com.nicholaston.callscribe.export.CallShareManager
import com.nicholaston.callscribe.export.TranscriptExportFormat
import java.io.File
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallDetailScreen(
    callId: Long,
    repository: CallRepository,
    onBack: () -> Unit,
) {
    val viewModel: CallDetailViewModel =
        viewModel(key = "call-$callId", factory = CallDetailViewModel.factory(callId, repository))
    val state by viewModel.state.collectAsState()
    val call = state.call

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(call?.displayName() ?: "Call") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (call == null) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text("This call is no longer available.", style = MaterialTheme.typography.titleMedium)
            }
        } else {
            CallDetailContent(
                call = call,
                transcript = state.transcript,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
        }
    }
}

@Composable
private fun CallDetailContent(
    call: CallRecord,
    transcript: List<TranscriptSegment>,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val player = remember { ExoPlayer.Builder(context).build() }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(call.durationMs ?: 0L) }
    var isPlaying by remember { mutableStateOf(false) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var shareError by remember { mutableStateOf<String?>(null) }
    var speed by remember { mutableStateOf(1f) }
    val audioUnavailable = call.audioUri.isNullOrBlank() ||
        call.audioDeletedAt != null ||
        isMissingFileUri(call.audioUri)

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(value: Boolean) {
                isPlaying = value
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY && player.duration != C.TIME_UNSET) {
                    durationMs = player.duration.coerceAtLeast(0L)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                playbackError = "Audio cannot be played: ${error.errorCodeName}"
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(call.audioUri, audioUnavailable) {
        player.clearMediaItems()
        playbackError = null
        if (!audioUnavailable) {
            runCatching {
                player.setMediaItem(MediaItem.fromUri(Uri.parse(call.audioUri)))
                player.prepare()
            }.onFailure {
                playbackError = "Audio cannot be opened."
            }
        }
    }

    LaunchedEffect(player) {
        while (true) {
            positionMs = player.currentPosition.coerceAtLeast(0L)
            delay(250)
        }
    }

    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(call.displayName(), style = MaterialTheme.typography.headlineSmall)
                call.phoneNumber?.let { Text(it) }
                Text(
                    "${formatCallDate(call.startedAt)} • ${formatDuration(call.durationMs)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text("Status: ${call.status.displayLabel()}")
                if (call.status == CallStatus.TRANSCRIBING || call.status == CallStatus.QUEUED) {
                    LinearProgressIndicator(
                        progress = { call.progress.coerceIn(0, 100) / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                call.error?.takeIf(String::isNotBlank)?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }
        }
        item {
            AudioControls(
                unavailable = audioUnavailable,
                error = playbackError,
                isPlaying = isPlaying,
                positionMs = positionMs,
                durationMs = durationMs,
                speed = speed,
                onPlayPause = {
                    if (isPlaying) player.pause() else player.play()
                },
                onSeek = {
                    positionMs = it
                    player.seekTo(it)
                },
                onSpeedChange = {
                    speed = if (speed >= 2f) 0.75f else speed + 0.25f
                    player.setPlaybackSpeed(speed)
                },
            )
        }
        item {
            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Export", style = MaterialTheme.typography.titleMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        TranscriptExportFormat.entries.forEach { format ->
                            TextButton(
                                enabled = transcript.isNotEmpty(),
                                onClick = {
                                    shareError = runCatching {
                                        CallShareManager.shareTranscript(context, call, transcript, format)
                                    }.exceptionOrNull()?.message
                                },
                            ) {
                                Text(format.extension.uppercase())
                            }
                        }
                        TextButton(
                            enabled = !audioUnavailable,
                            onClick = {
                                shareError = runCatching {
                                    CallShareManager.shareAudio(context, call)
                                }.exceptionOrNull()?.message
                            },
                        ) {
                            Text("Audio")
                        }
                    }
                    shareError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
        if (transcript.isEmpty()) {
            item {
                Text(
                    text = when (call.status) {
                        CallStatus.FAILED -> "Transcription failed."
                        CallStatus.TRANSCRIBING, CallStatus.QUEUED -> "Transcript is being prepared."
                        else -> "No transcript is available."
                    },
                    modifier = Modifier.padding(24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            item {
                Text(
                    "Transcript",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            items(transcript, key = { it.id }) { segment ->
                TranscriptCard(
                    segment = segment,
                    highlighted = !audioUnavailable &&
                        playbackError == null &&
                        positionMs in segment.startMs until segment.endMs,
                    onClick = {
                        positionMs = segment.startMs
                        player.seekTo(segment.startMs)
                    },
                )
            }
        }
    }
}

@Composable
private fun AudioControls(
    unavailable: Boolean,
    error: String?,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    speed: Float,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onSpeedChange: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (unavailable) {
                Text(
                    "Audio is missing or has been deleted. The transcript remains available.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
                return@Column
            }
            Slider(
                value = positionMs.coerceIn(0L, durationMs.coerceAtLeast(1L)).toFloat(),
                onValueChange = { onSeek(it.toLong()) },
                valueRange = 0f..durationMs.coerceAtLeast(1L).toFloat(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("${formatPosition(positionMs)} / ${formatPosition(durationMs)}")
                Row {
                    IconButton(onClick = onPlayPause) {
                        Icon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                        )
                    }
                    Button(onClick = onSpeedChange) { Text("${speed}×") }
                }
            }
        }
    }
}

@Composable
private fun TranscriptCard(
    segment: TranscriptSegment,
    highlighted: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).clickable(onClick = onClick),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = if (highlighted) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    segment.speaker?.takeIf(String::isNotBlank) ?: "Speaker",
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.SemiBold,
                    color = if (highlighted) MaterialTheme.colorScheme.primary else Color.Unspecified,
                )
                Text(
                    formatPosition(segment.startMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                segment.text,
                color = if (highlighted) MaterialTheme.colorScheme.primary else Color.Unspecified,
            )
        }
    }
}

private fun isMissingFileUri(uriString: String?): Boolean {
    if (uriString.isNullOrBlank()) return true
    val uri = Uri.parse(uriString)
    return uri.scheme == "file" && uri.path?.let { !File(it).isFile } != false
}
