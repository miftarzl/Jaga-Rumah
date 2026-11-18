package com.nayakacctv.ui.screens.playback

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
import com.nayakacctv.data.model.Recording
import com.nayakacctv.data.repository.NayakaRepository
import com.nayakacctv.ui.components.NayakaTopAppBar
import com.nayakacctv.ui.theme.*
import com.nayakacctv.ui.utils.Formatters
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun PlaybackDetailScreen(
    recordingId: String,
    repository: NayakaRepository,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var recording by remember { mutableStateOf<Recording?>(null) }
    var isPlaying by remember { mutableStateOf(true) }
    var currentSeconds by remember { mutableStateOf(0L) }
    var speed by remember { mutableStateOf(1f) }

    LaunchedEffect(recordingId) {
        coroutineScope.launch {
            val list = repository.getRecordings().getOrDefault(emptyList())
            recording = list.find { it.id == recordingId } ?: Recording(
                id = recordingId,
                cameraId = "cam-1",
                cameraName = "Kamera Depan",
                title = "Rekaman Kejadian #$recordingId",
                startedAt = "2026-11-17T14:00:00Z",
                duration = 300,
                sizeBytes = 15400000,
                hasMotion = true
            )
        }
    }

    val totalDuration = recording?.duration ?: 300L

    // Playback scrubber ticker
    LaunchedEffect(isPlaying, speed) {
        if (isPlaying) {
            while (currentSeconds < totalDuration) {
                delay((1000 / speed).toLong())
                currentSeconds++
            }
            isPlaying = false
        }
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
                Icon(
                    imageVector = Icons.Default.Videocam,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.3f),
                    modifier = Modifier.size(70.dp)
                )

                // Overlay Controls
                IconButton(
                    onClick = {
                        if (currentSeconds >= totalDuration) currentSeconds = 0L
                        isPlaying = !isPlaying
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
                Slider(
                    value = currentSeconds.toFloat(),
                    onValueChange = { currentSeconds = it.toLong() },
                    valueRange = 0f..totalDuration.toFloat(),
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

            // Controls & Speeds Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
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
                            onClick = { Toast.makeText(context, "Mulai mengunduh rekaman...", Toast.LENGTH_SHORT).show() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF1F5F9), contentColor = TextPrimary)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Unduh Video", fontSize = 13.sp)
                        }

                        Button(
                            onClick = { Toast.makeText(context, "Membuka dialog bagikan...", Toast.LENGTH_SHORT).show() },
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
