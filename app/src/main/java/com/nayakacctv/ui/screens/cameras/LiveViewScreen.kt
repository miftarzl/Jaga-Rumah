package com.nayakacctv.ui.screens.cameras

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.nayakacctv.data.model.Camera
import com.nayakacctv.data.model.PtzDirection
import com.nayakacctv.data.repository.NayakaRepository
import com.nayakacctv.ui.components.CameraStreamPlayer
import com.nayakacctv.ui.components.rememberCameraStreamController
import com.nayakacctv.ui.components.NayakaTopAppBar
import com.nayakacctv.ui.components.StatusBadge
import com.nayakacctv.ui.theme.*
import com.nayakacctv.ui.utils.MicrophoneCapture
import com.nayakacctv.ui.utils.StreamRecorder
import kotlinx.coroutines.delay
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.util.Locale

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

    val streamController = rememberCameraStreamController()
    var isMuted by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }
    var isSavingRecording by remember { mutableStateOf(false) }
    var recordElapsedSeconds by remember { mutableLongStateOf(0L) }
    var streamStatus by remember { mutableStateOf("Menghubungkan...") }
    var hasMicrophonePermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var isMicrophoneCapturing by remember { mutableStateOf(false) }
    var microphoneLevel by remember { mutableFloatStateOf(0f) }
    var microphoneError by remember { mutableStateOf<String?>(null) }
    var isPtzLoading by remember { mutableStateOf(false) }
    var ptzError by remember { mutableStateOf<String?>(null) }
    var ptzStatus by remember { mutableStateOf<String?>(null) }
    val microphoneCapture = remember { MicrophoneCapture() }
    val microphonePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicrophonePermission = granted
        microphoneError = if (granted) null else "Izin mikrofon ditolak."
    }

    fun toggleMicrophoneCapture() {
        if (isMicrophoneCapturing) {
            microphoneCapture.stop()
            isMicrophoneCapturing = false
            microphoneLevel = 0f
        } else if (!hasMicrophonePermission) {
            microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            microphoneError = null
            isMicrophoneCapturing = microphoneCapture.start(
                scope = coroutineScope,
                onLevelChanged = { microphoneLevel = it },
                onError = {
                    microphoneError = it
                    isMicrophoneCapturing = false
                    microphoneLevel = 0f
                    microphoneCapture.stop()
                }
            )
        }
    }

    fun sendPtzCommand(direction: PtzDirection) {
        if (isPtzLoading) return
        isPtzLoading = true
        ptzError = null
        ptzStatus = null
        val targetCameraId = activeCameraId
        coroutineScope.launch {
            repository.controlCameraPtz(targetCameraId, direction)
                .onSuccess { ptzStatus = it.take(300) }
                .onFailure { ptzError = it.localizedMessage ?: "Gagal mengontrol kamera." }
            isPtzLoading = false
        }
    }

    DisposableEffect(microphoneCapture) {
        onDispose { microphoneCapture.stop() }
    }

    LaunchedEffect(activeCameraId) {
        microphoneCapture.stop()
        isMicrophoneCapturing = false
        microphoneLevel = 0f
    }

    DisposableEffect(Unit) {
        onDispose {
            if (StreamRecorder.isRecording) {
                coroutineScope.launch {
                    val res = StreamRecorder.stopRecording(context)
                    res.getOrNull()?.let { repository.saveLocalRecording(it) }
                }
            }
        }
    }

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
                            controller = streamController,
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
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
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
                            streamController.captureFrame { bitmap ->
                                if (bitmap != null) {
                                    val res = StreamRecorder.saveSnapshotToGallery(context, bitmap)
                                    if (res.isSuccess) {
                                        Toast.makeText(context, "Foto snapshot berhasil disimpan ke Galeri (Pictures/NayakaCCTV)!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Gagal menyimpan foto: ${res.exceptionOrNull()?.localizedMessage}", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    Toast.makeText(context, "Menunggu siaran video tampil untuk mengambil snapshot...", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )
                    ActionButton(
                        icon = if (isRecording) Icons.Default.Stop else Icons.Default.FiberManualRecord,
                        label = if (isSavingRecording) "Menyimpan..." else if (isRecording) "Berhenti" else "Rekam",
                        iconColor = if (isRecording) StatusDanger else PrimaryBlue,
                        onClick = {
                            val activeUrl = streamUrl
                            if (!isRecording) {
                                if (activeUrl.isNullOrBlank()) {
                                    Toast.makeText(context, "URL live stream belum siap untuk direkam", Toast.LENGTH_SHORT).show()
                                    return@ActionButton
                                }
                                isRecording = true
                                recordElapsedSeconds = 0L
                                StreamRecorder.startRecording(
                                    context = context,
                                    streamUrl = activeUrl,
                                    cameraId = activeCameraId,
                                    cameraName = cam?.name ?: "Kamera CCTV",
                                    scope = coroutineScope
                                )
                                Toast.makeText(context, "Mulai merekam siaran langsung CCTV...", Toast.LENGTH_SHORT).show()
                            } else {
                                if (isSavingRecording) return@ActionButton
                                isSavingRecording = true
                                isRecording = false

                                streamController.captureFrame { lastFrame ->
                                    coroutineScope.launch {
                                        val result = StreamRecorder.stopRecording(context, lastFrame)
                                        isSavingRecording = false
                                        result.onSuccess { rec ->
                                            repository.saveLocalRecording(rec)
                                            Toast.makeText(
                                                context,
                                                "Rekaman video berhasil disimpan di Galeri (Movies/NayakaCCTV) & arsip Playback!",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }.onFailure { err ->
                                            Toast.makeText(
                                                context,
                                                "Gagal menyimpan rekaman: ${err.localizedMessage ?: "Terjadi kesalahan"}",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    }
                                }
                            }
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

                MicrophoneTalkPanel(
                    hasPermission = hasMicrophonePermission,
                    isCapturing = isMicrophoneCapturing,
                    audioLevel = microphoneLevel,
                    errorMessage = microphoneError,
                    onToggleCapture = ::toggleMicrophoneCapture
                )

                PtzControlPanel(
                    isLoading = isPtzLoading,
                    errorMessage = ptzError,
                    statusMessage = ptzStatus,
                    onDirection = ::sendPtzCommand
                )

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
private fun PtzControlPanel(
    isLoading: Boolean,
    errorMessage: String?,
    statusMessage: String?,
    onDirection: (PtzDirection) -> Unit
) {
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
            Text(
                text = "Setiap tombol menggerakkan kamera selama 400 ms.",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
            Spacer(modifier = Modifier.height(12.dp))

            PtzControlButton(
                icon = Icons.Default.KeyboardArrowUp,
                description = "Atas",
                enabled = !isLoading,
                onClick = { onDirection(PtzDirection.UP) }
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PtzControlButton(
                    icon = Icons.Default.KeyboardArrowLeft,
                    description = "Kiri",
                    enabled = !isLoading,
                    onClick = { onDirection(PtzDirection.LEFT) }
                )
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(PrimaryBlue.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = PrimaryBlue,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.ControlCamera, contentDescription = null, tint = PrimaryBlue)
                    }
                }
                PtzControlButton(
                    icon = Icons.Default.KeyboardArrowRight,
                    description = "Kanan",
                    enabled = !isLoading,
                    onClick = { onDirection(PtzDirection.RIGHT) }
                )
            }
            PtzControlButton(
                icon = Icons.Default.KeyboardArrowDown,
                description = "Bawah",
                enabled = !isLoading,
                onClick = { onDirection(PtzDirection.DOWN) }
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { onDirection(PtzDirection.ZOOM_OUT) },
                    enabled = !isLoading
                ) {
                    Icon(Icons.Default.ZoomOut, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Zoom −")
                }
                OutlinedButton(
                    onClick = { onDirection(PtzDirection.ZOOM_IN) },
                    enabled = !isLoading
                ) {
                    Icon(Icons.Default.ZoomIn, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Zoom +")
                }
            }
            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = errorMessage,
                    color = StatusDanger,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (statusMessage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Respons API (belum memastikan kamera bergerak): $statusMessage",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun PtzControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color(0xFFF1F5F9))
    ) {
        Icon(icon, contentDescription = description, tint = TextPrimary)
    }
}

@Composable
private fun MicrophoneTalkPanel(
    hasPermission: Boolean,
    isCapturing: Boolean,
    audioLevel: Float,
    errorMessage: String?,
    onToggleCapture: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Bicara ke Kamera",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                text = "Tes mikrofon lokal. Suara belum dikirim ke kamera karena backend talk belum tersedia.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
            Button(
                onClick = onToggleCapture,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isCapturing) StatusDanger else PrimaryBlue
                )
            ) {
                Icon(
                    imageVector = if (isCapturing) Icons.Default.MicOff else Icons.Default.Mic,
                    contentDescription = null
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    when {
                        isCapturing -> "Hentikan tes mikrofon"
                        hasPermission -> "Mulai tes mikrofon"
                        else -> "Izinkan mikrofon"
                    }
                )
            }
            if (isCapturing) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(BorderSubtle)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(audioLevel)
                            .fillMaxHeight()
                            .background(PrimaryBlue)
                    )
                }
            }
            if (errorMessage != null) {
                Text(errorMessage, color = StatusDanger, style = MaterialTheme.typography.bodySmall)
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
