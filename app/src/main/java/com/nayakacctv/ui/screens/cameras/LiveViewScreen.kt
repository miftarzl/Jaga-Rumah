package com.nayakacctv.ui.screens.cameras

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.nayakacctv.ui.components.CameraStreamPlayer
import com.nayakacctv.ui.components.NayakaTopAppBar
import com.nayakacctv.ui.components.StatusBadge
import com.nayakacctv.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

@Composable
fun LiveViewScreen(
    cameraId: String,
    repository: NayakaRepository,
    onNavigateBack: () -> Unit,
    onNavigateToDetail: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var activeCameraId by remember { mutableStateOf(cameraId) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var streamUrl by remember { mutableStateOf<String?>(null) }
    var streamError by remember { mutableStateOf<String?>(null) }
    var allCameras by remember { mutableStateOf<List<Camera>>(emptyList()) }
    var showCameraSwitcher by remember { mutableStateOf(false) }

    var isMuted by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }
    var recordElapsedSeconds by remember { mutableLongStateOf(0L) }
    var streamStatus by remember { mutableStateOf("Menghubungkan...") }

    // Ambil detail dan URL playback yang dibuat backend untuk kamera aktif.
    LaunchedEffect(activeCameraId) {
        camera = null
        streamUrl = null
        streamError = null

        val cameraRequest = async { repository.getCameraDetail(activeCameraId) }
        val streamRequest = async { repository.getCameraStream(activeCameraId) }
        val cameraResult = cameraRequest.await()
        val streamResult = streamRequest.await()
        camera = cameraResult.getOrNull()
        val fetchedStream = streamResult.getOrNull()
        val activeStreamUrl = fetchedStream?.playbackUrl 
            ?: camera?.streamUrl?.takeIf { it.isNotBlank() }

        android.util.Log.i("NAYAKA_STREAM", "getCameraDetail: $cameraResult")
        android.util.Log.i("NAYAKA_STREAM", "getCameraStream: $streamResult")
        android.util.Log.i("NAYAKA_STREAM", "activeStreamUrl: $activeStreamUrl, playbackUrl: ${fetchedStream?.playbackUrl}, camStreamUrl: ${camera?.streamUrl}")

        streamUrl = activeStreamUrl

        if (streamUrl == null) {
            streamStatus = "Koneksi Terputus"
            streamError = streamResult.exceptionOrNull()?.localizedMessage
                ?: "Backend tidak mengirim URL stream untuk kamera ini"
        }
    }

    // Memuat daftar semua kamera untuk fitur "Ganti Kamera"
    LaunchedEffect(Unit) {
        coroutineScope.launch {
            val listRes = repository.getCameras()
            allCameras = listRes.getOrDefault(emptyList())
        }
    }

    // Timer simulasi rekaman
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
                    // Tombol Ganti Kamera jika ada lebih dari 1 kamera
                    if (allCameras.size > 1) {
                        IconButton(onClick = { showCameraSwitcher = true }) {
                            Icon(
                                imageVector = Icons.Default.SwapHoriz,
                                contentDescription = "Ganti Kamera",
                                tint = PrimaryBlue
                            )
                        }
                    }
                    IconButton(onClick = {
                        isMuted = !isMuted
                        Toast.makeText(context, if (isMuted) "Audio dimatikan" else "Audio diaktifkan", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(
                            imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                            contentDescription = "Mute/Unmute",
                            tint = TextPrimary
                        )
                    }
                    if (onNavigateToDetail != null) {
                        IconButton(onClick = { onNavigateToDetail(activeCameraId) }) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Detail & Edit Kamera",
                                tint = PrimaryBlue
                            )
                        }
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
                    val currentStreamUrl = streamUrl
                    if (currentStreamUrl != null) {
                        CameraStreamPlayer(
                            streamUrl = currentStreamUrl,
                            forceHls = false,
                            modifier = Modifier.fillMaxSize(),
                            onStateChange = { state ->
                                streamStatus = when (state) {
                                    "playing" -> "LIVE HD"
                                    "connecting" -> "Menghubungkan..."
                                    else -> "Koneksi Terputus"
                            }
                        }
                    )
                } else if (streamError != null) {
                    Text(
                        text = streamError ?: "URL stream kamera tidak tersedia",
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        color = Color.White,
                        fontSize = 13.sp
                    )
                }

                // Indikator REC
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

                // Tag Status LIVE
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
                // Baris Info Kamera & Tombol Ganti Kamera Cepat
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = cam?.name ?: "Memuat...",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = cam?.location?.ifBlank { "Lokasi tidak diset" } ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }

                        if (allCameras.size > 1) {
                            TextButton(onClick = { showCameraSwitcher = true }) {
                                Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Ganti Kamera", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Action Buttons Row (Snapshot, Record, Siren)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ActionButton(
                        icon = Icons.Default.CameraAlt,
                        label = "Foto (Snapshot)",
                        onClick = {
                            Toast.makeText(context, "Tangkapan layar frame berhasil disimpan ke galeri!", Toast.LENGTH_SHORT).show()
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
                                if (isRecording) "Merekam siaran langsung CCTV..." else "Rekaman video berhasil disimpan",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    )
                    ActionButton(
                        icon = Icons.Default.NotificationsActive,
                        label = "Bunyikan Sirine",
                        iconColor = StatusDanger,
                        onClick = {
                            Toast.makeText(context, "Sirine peringatan darurat kamera dibunyikan!", Toast.LENGTH_SHORT).show()
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
                            onClick = { Toast.makeText(context, "Arahkan kamera ke Atas", Toast.LENGTH_SHORT).show() },
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
                                onClick = { Toast.makeText(context, "Arahkan kamera ke Kiri", Toast.LENGTH_SHORT).show() },
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
                                onClick = { Toast.makeText(context, "Arahkan kamera ke Kanan", Toast.LENGTH_SHORT).show() },
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
                            onClick = { Toast.makeText(context, "Arahkan kamera ke Bawah", Toast.LENGTH_SHORT).show() },
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

    // ── Dialog Pilih / Ganti Kamera ─────────────────────────────────────────
    if (showCameraSwitcher) {
        AlertDialog(
            onDismissRequest = { showCameraSwitcher = false },
            title = {
                Text("Pilih / Ganti Kamera", fontWeight = FontWeight.Bold)
            },
            text = {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(allCameras) { c ->
                        val isCurrent = c.id == activeCameraId
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    activeCameraId = c.id
                                    showCameraSwitcher = false
                                    Toast.makeText(context, "Beralih ke ${c.name}", Toast.LENGTH_SHORT).show()
                                },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isCurrent) PrimaryBlue.copy(alpha = 0.1f) else Color(0xFFF8FAFC),
                            border = androidx.compose.foundation.BorderStroke(
                                width = if (isCurrent) 1.5.dp else 1.dp,
                                color = if (isCurrent) PrimaryBlue else BorderSubtle
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = c.name,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isCurrent) PrimaryBlue else TextPrimary,
                                        fontSize = 15.sp
                                    )
                                    Text(
                                        text = c.location.ifBlank { "Lokasi tidak diset" },
                                        fontSize = 12.sp,
                                        color = TextSecondary
                                    )
                                }
                                StatusBadge(status = c.status)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCameraSwitcher = false }) {
                    Text("Tutup")
                }
            }
        )
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
