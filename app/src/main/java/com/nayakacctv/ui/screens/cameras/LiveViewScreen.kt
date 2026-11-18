package com.nayakacctv.ui.screens.cameras

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
import com.nayakacctv.data.model.Camera
import com.nayakacctv.data.repository.NayakaRepository
import com.nayakacctv.ui.components.MjpegStreamView
import com.nayakacctv.ui.components.NayakaTopAppBar
import com.nayakacctv.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun LiveViewScreen(
    cameraId: String,
    repository: NayakaRepository,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var camera by remember { mutableStateOf<Camera?>(null) }
    var isMuted by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }
    var recordElapsedSeconds by remember { mutableStateOf(0L) }
    var streamStatus by remember { mutableStateOf("Menghubungkan...") }

    LaunchedEffect(cameraId) {
        coroutineScope.launch {
            val res = repository.getCameraDetail(cameraId)
            camera = res.getOrNull()
        }
    }

    // Timer for recording simulation
    LaunchedEffect(isRecording) {
        if (isRecording) {
            while (true) {
                delay(1000)
                recordElapsedSeconds++
            }
        } else {
            recordElapsedSeconds = 0L
        }
    }

    Scaffold(
        topBar = {
            NayakaTopAppBar(
                title = camera?.name ?: "Siaran Langsung",
                canNavigateBack = true,
                onNavigateBack = onNavigateBack,
                actions = {
                    IconButton(onClick = { isMuted = !isMuted }) {
                        Icon(
                            imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                            contentDescription = "Mute/Unmute",
                            tint = TextPrimary
                        )
                    }
                }
            )
        },
        containerColor = BackgroundSlate
    ) { padding ->
        val cam = camera
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Live View Container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .background(Color.Black)
            ) {
                if (cam != null) {
                    val streamEndpoint = if (cam.streamUrl.isNotBlank()) cam.streamUrl else "/mobile/cameras/${cam.id}/stream"
                    MjpegStreamView(
                        streamUrl = streamEndpoint,
                        modifier = Modifier.fillMaxSize(),
                        onStateChange = { state ->
                            streamStatus = when (state) {
                                "playing" -> "LIVE HD"
                                "connecting" -> "Menghubungkan..."
                                else -> "Koneksi Terputus"
                            }
                        }
                    )
                }

                // REC indicator overlay
                if (isRecording) {
                    Box(
                        modifier = Modifier
                            .padding(12.dp)
                            .align(Alignment.TopStart)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Red.copy(alpha = 0.85f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        val m = recordElapsedSeconds / 60
                        val s = recordElapsedSeconds % 60
                        Text(
                            text = String.format("REC %02d:%02d", m, s),
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Live status tag
                Box(
                    modifier = Modifier
                        .padding(12.dp)
                        .align(Alignment.TopEnd)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = streamStatus,
                        color = if (streamStatus == "LIVE HD") Color(0xFF22C55E) else Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Controls Panel
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Action Buttons Row (Snapshot, Record, Siren)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ActionButton(
                        icon = Icons.Default.CameraAlt,
                        label = "Foto (Snapshot)",
                        onClick = {
                            Toast.makeText(context, "Tangkapan layar frame berhasil disimpan", Toast.LENGTH_SHORT).show()
                        }
                    )
                    ActionButton(
                        icon = if (isRecording) Icons.Default.Stop else Icons.Default.FiberManualRecord,
                        label = if (isRecording) "Berhenti" else "Rekam",
                        iconColor = if (isRecording) StatusDanger else PrimaryBlue,
                        onClick = {
                            isRecording = !isRecording
                            Toast.makeText(
                                context,
                                if (isRecording) "Merekam siaran langsung..." else "Rekaman disimpan",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    )
                    ActionButton(
                        icon = Icons.Default.NotificationsActive,
                        label = "Bunyikan Sirine",
                        iconColor = StatusDanger,
                        onClick = {
                            Toast.makeText(context, "Sirine kamera diaktifkan!", Toast.LENGTH_SHORT).show()
                        }
                    )
                }

                // PTZ Pan-Tilt-Zoom Controller
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Kontrol Arah Kamera (PTZ)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        // Up
                        IconButton(
                            onClick = { Toast.makeText(context, "Arahkan Atas", Toast.LENGTH_SHORT).show() },
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFF1F5F9))
                        ) {
                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Atas", tint = TextPrimary)
                        }

                        // Left - Center - Right
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(28.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { Toast.makeText(context, "Arahkan Kiri", Toast.LENGTH_SHORT).show() },
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFF1F5F9))
                            ) {
                                Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Kiri", tint = TextPrimary)
                            }

                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(PrimaryBlue.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.ControlCamera, contentDescription = null, tint = PrimaryBlue)
                            }

                            IconButton(
                                onClick = { Toast.makeText(context, "Arahkan Kanan", Toast.LENGTH_SHORT).show() },
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFF1F5F9))
                            ) {
                                Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Kanan", tint = TextPrimary)
                            }
                        }

                        // Down
                        IconButton(
                            onClick = { Toast.makeText(context, "Arahkan Bawah", Toast.LENGTH_SHORT).show() },
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFF1F5F9))
                        ) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Bawah", tint = TextPrimary)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    iconColor: Color = PrimaryBlue,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(100.dp)
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(SurfaceWhite)
        ) {
            Icon(imageVector = icon, contentDescription = label, tint = iconColor, modifier = Modifier.size(26.dp))
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            color = TextSecondary,
            fontWeight = FontWeight.Medium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}
