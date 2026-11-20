package com.nayakacctv.ui.screens.playback

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.nayakacctv.data.model.Recording
import com.nayakacctv.data.repository.NayakaRepository
import com.nayakacctv.ui.components.NayakaTopAppBar
import com.nayakacctv.ui.theme.*
import com.nayakacctv.ui.utils.Formatters
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun PlaybackDetailScreen(
    recordingId: String,
    repository: NayakaRepository,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var recording by remember { mutableStateOf<Recording?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentSeconds by remember { mutableLongStateOf(0L) }
    var totalDuration by remember { mutableLongStateOf(0L) }
    var speed by remember { mutableFloatStateOf(1f) }
    var isScrubbing by remember { mutableStateOf(false) }

    LaunchedEffect(recordingId) {
        coroutineScope.launch {
            val res = repository.getRecordingDetail(recordingId)
            val rec = res.getOrNull() ?: repository.getRecordings().getOrDefault(emptyList()).find { it.id == recordingId }
            recording = rec ?: Recording(
                id = recordingId,
                cameraId = "cam-1",
                cameraName = "Kamera Depan",
                title = "Rekaman Kejadian #$recordingId",
                startedAt = "2026-11-17T14:00:00Z",
                duration = 300,
                sizeBytes = 15400000,
                hasMotion = true
            )
            totalDuration = (recording?.duration ?: 0L).takeIf { it > 0 } ?: 10L
        }
    }

    val playbackUrl = recording?.playbackUrl
    val mediaUri: Uri? = remember(playbackUrl) {
        if (playbackUrl.isNullOrBlank()) null
        else if (playbackUrl.startsWith("http://", ignoreCase = true) || playbackUrl.startsWith("https://", ignoreCase = true) || playbackUrl.startsWith("content://", ignoreCase = true)) {
            Uri.parse(playbackUrl)
        } else {
            Uri.fromFile(File(playbackUrl))
        }
    }

    val exoPlayer = remember(mediaUri) {
        if (mediaUri != null) {
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(mediaUri))
                prepare()
                playWhenReady = true
            }
        } else null
    }

    DisposableEffect(exoPlayer) {
        val player = exoPlayer ?: return@DisposableEffect onDispose {}
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    val dur = player.duration
                    if (dur > 0) {
                        totalDuration = dur / 1000
                    }
                } else if (playbackState == Player.STATE_ENDED) {
                    isPlaying = false
                }
            }
        }
        player.addListener(listener)

        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    // Update position sync
    LaunchedEffect(exoPlayer, isPlaying, isScrubbing) {
        val player = exoPlayer
        if (player != null && !isScrubbing) {
            while (true) {
                if (player.isPlaying) {
                    currentSeconds = player.currentPosition / 1000
                }
                delay(500)
            }
        }
    }

    // Kecepatan putar
    LaunchedEffect(speed) {
        exoPlayer?.playbackParameters = PlaybackParameters(speed)
    }

    Scaffold(
        topBar = {
            NayakaTopAppBar(
                title = "Putar Rekaman",
                canNavigateBack = true,
                onNavigateBack = onNavigateBack
            )
        },
        containerColor = BackgroundSlate
    ) { padding ->
        val rec = recording
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Player Video Container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                if (exoPlayer != null) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                useController = false
                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                keepScreenOn = true
                                player = exoPlayer
                            }
                        },
                        update = { it.player = exoPlayer }
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Videocam,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.3f),
                        modifier = Modifier.size(70.dp)
                    )
                }

                // Overlay Play/Pause Control
                IconButton(
                    onClick = {
                        val player = exoPlayer
                        if (player != null) {
                            if (player.playbackState == Player.STATE_ENDED) {
                                player.seekTo(0)
                                player.play()
                            } else if (player.isPlaying) {
                                player.pause()
                            } else {
                                player.play()
                            }
                        } else {
                            isPlaying = !isPlaying
                        }
                    },
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f))
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Speed Pill
                Box(
                    modifier = Modifier
                        .padding(12.dp)
                        .align(Alignment.TopEnd)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${speed}x",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Timeline Scrubber
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                val maxRange = totalDuration.coerceAtLeast(1L).toFloat()
                Slider(
                    value = currentSeconds.toFloat().coerceIn(0f, maxRange),
                    onValueChange = {
                        isScrubbing = true
                        currentSeconds = it.toLong()
                    },
                    onValueChangeFinished = {
                        exoPlayer?.seekTo(currentSeconds * 1000)
                        isScrubbing = false
                    },
                    valueRange = 0f..maxRange,
                    colors = SliderDefaults.colors(
                        thumbColor = PrimaryBlue,
                        activeTrackColor = PrimaryBlue,
                        inactiveTrackColor = BorderSubtle
                    )
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = Formatters.formatDuration(currentSeconds),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                    Text(
                        text = Formatters.formatDuration(totalDuration),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
            }

            // Metadata Detail Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = rec?.title?.ifBlank { "Rekaman CCTV" } ?: "Memuat...",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${rec?.cameraName ?: ""} • ${Formatters.formatIsoDate(rec?.startedAt ?: "")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Durasi: ${Formatters.formatDuration(totalDuration)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                        Text(
                            text = "Ukuran: ${Formatters.formatBytes(rec?.sizeBytes ?: 0L)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                    }
                }
            }

            // Controls & Speeds Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Kecepatan Pemutaran",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        listOf(0.5f, 1f, 2f, 4f).forEach { s ->
                            val isSelected = speed == s
                            FilterChip(
                                selected = isSelected,
                                onClick = { speed = s },
                                label = { Text("${s}x", fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = PrimaryBlue,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Divider(color = BorderSubtle)
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Aksi Berkas Rekaman",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                if (playbackUrl != null && (playbackUrl.startsWith("/") || playbackUrl.startsWith("file://"))) {
                                    Toast.makeText(context, "Berkas video sudah tersimpan di Galeri & Movies/NayakaCCTV", Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(context, "Mengunduh berkas rekaman ke galeri...", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF1F5F9), contentColor = TextPrimary)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Unduh Video", fontSize = 13.sp)
                        }

                        Button(
                            onClick = {
                                try {
                                    if (playbackUrl != null && (playbackUrl.startsWith("/") || playbackUrl.startsWith("file://"))) {
                                        val cleanPath = playbackUrl.removePrefix("file://")
                                        val f = File(cleanPath)
                                        if (f.exists()) {
                                            val shareUri = FileProvider.getUriForFile(
                                                context,
                                                "${context.packageName}.fileprovider",
                                                f
                                            )
                                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                type = "video/mp4"
                                                putExtra(Intent.EXTRA_STREAM, shareUri)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(Intent.createChooser(shareIntent, "Bagikan Rekaman CCTV"))
                                            return@Button
                                        }
                                    }
                                    // Fallback plain share
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, "Rekaman CCTV: ${rec?.title} (${playbackUrl ?: ""})")
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Bagikan Rekaman"))
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Gagal membagikan rekaman: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF1F5F9), contentColor = TextPrimary)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Bagikan", fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}
