package com.nayakacctv.ui.screens.playback

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import com.nayakacctv.data.model.Camera
import com.nayakacctv.data.model.Recording
import com.nayakacctv.data.repository.NayakaRepository
import com.nayakacctv.ui.components.NayakaTopAppBar
import com.nayakacctv.ui.theme.*
import com.nayakacctv.ui.utils.Formatters
import com.nayakacctv.ui.utils.StreamRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

@Composable
fun PlaybackScreen(
    repository: NayakaRepository,
    onNavigateToDetail: (String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var recordings by remember { mutableStateOf<List<Recording>>(emptyList()) }
    var cameras by remember { mutableStateOf<List<Camera>>(emptyList()) }
    var selectedCameraId by remember { mutableStateOf<String?>(null) }
    var fromMillis by remember { mutableLongStateOf(System.currentTimeMillis() - 24 * 60 * 60 * 1000L + 60_000L) }
    var toMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var isLoading by remember { mutableStateOf(true) }
    var isDownloadingClip by remember { mutableStateOf(false) }
    var showClipConfirmation by remember { mutableStateOf(false) }
    var clipError by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun loadRecordings(forceRefresh: Boolean) {
        coroutineScope.launch {
            isLoading = true
            errorMessage = null
            val result = repository.getRecordings(
                cameraId = selectedCameraId,
                from = formatUtcIso(fromMillis),
                to = formatUtcIso(toMillis),
                forceRefresh = forceRefresh
            )
            result.onSuccess { recordings = it }
                .onFailure { errorMessage = it.localizedMessage ?: "Gagal memuat rekaman." }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        repository.getCameras().onSuccess { cameras = it }
        loadRecordings(forceRefresh = true)
    }

    Scaffold(
        topBar = {
            NayakaTopAppBar(
                title = "Rekaman Histori",
                actions = {
                    IconButton(onClick = { loadRecordings(forceRefresh = true) }, enabled = !isLoading) {
                        Icon(Icons.Default.Refresh, contentDescription = "Muat ulang", tint = PrimaryBlue)
                    }
                }
            )
        },
        containerColor = BackgroundSlate
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                PlaybackFilterCard(
                    cameras = cameras,
                    selectedCameraId = selectedCameraId,
                    fromMillis = fromMillis,
                    toMillis = toMillis,
                    onCameraSelected = { selectedCameraId = it },
                    onFromSelected = { fromMillis = it },
                    onToSelected = { toMillis = it },
                    onSearch = {
                        val now = System.currentTimeMillis()
                        if (fromMillis < now - 24 * 60 * 60 * 1000L || toMillis > now ||
                            fromMillis >= toMillis
                        ) {
                            errorMessage = "Pilih rentang waktu yang valid dalam 24 jam terakhir."
                        } else {
                            loadRecordings(forceRefresh = true)
                        }
                    },
                    onRequestClip = {
                        clipError = null
                        showClipConfirmation = true
                    }
                )
            }
            if (errorMessage != null) {
                item {
                    Text(
                        text = errorMessage.orEmpty(),
                        color = StatusDanger,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            if (isLoading) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = PrimaryBlue)
                    }
                }
            } else if (recordings.isEmpty()) {
                item {
                    Text(
                        text = "Belum ada rekaman pada rentang waktu ini.",
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        color = TextMuted,
                        fontSize = 14.sp
                    )
                }
            } else {
                items(recordings) { rec ->
                    RecordingCard(recording = rec, onClick = { onNavigateToDetail(rec.id) })
                }
            }
        }
    }

    if (showClipConfirmation) {
        AlertDialog(
            onDismissRequest = { showClipConfirmation = false },
            title = { Text("Tarik Klip Kejadian", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Kamera: ${cameras.firstOrNull { it.id == selectedCameraId }?.name ?: "belum dipilih"}\n" +
                            "Periode: ${formatLocalDateTime(fromMillis)} – ${formatLocalDateTime(toMillis)}\n" +
                            "Durasi maksimal 30 menit."
                    )
                    clipError?.let {
                        Text(it, color = StatusDanger, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !isDownloadingClip,
                    onClick = {
                        val durationMs = toMillis - fromMillis
                        val now = System.currentTimeMillis()
                        if (selectedCameraId.isNullOrBlank()) {
                            clipError = "Pilih kamera terlebih dahulu."
                        } else if (durationMs <= 0 || durationMs > 30 * 60 * 1000L ||
                            fromMillis < now - 24 * 60 * 60 * 1000L || toMillis > now
                        ) {
                            clipError = "Rentang klip harus valid dan maksimal 30 menit dalam 24 jam terakhir."
                        } else {
                            val cameraId = selectedCameraId!!
                            val cameraName = cameras.firstOrNull { it.id == cameraId }?.name ?: "Kamera CCTV"
                            val clipStart = formatUtcIso(fromMillis)
                            val clipEnd = formatUtcIso(toMillis)
                            isDownloadingClip = true
                            clipError = null
                            coroutineScope.launch {
                                try {
                                    val result = repository.downloadRecordingClip(cameraId, clipStart, clipEnd)
                                    result.onSuccess { body ->
                                        val byteCount = body.contentLength().coerceAtLeast(0L)
                                        val id = "local-clip-${UUID.randomUUID()}"
                                        val saved = withContext(Dispatchers.IO) {
                                            body.byteStream().use { input ->
                                                StreamRecorder.saveVideoStreamToGallery(
                                                    context,
                                                    input,
                                                    "NAYAKA_CLIP_${System.currentTimeMillis()}.mp4"
                                                )
                                            }
                                        }
                                        saved.onSuccess { uri ->
                                            repository.saveLocalRecording(
                                                Recording(
                                                    id = id,
                                                    cameraId = cameraId,
                                                    cameraName = cameraName,
                                                    title = "Klip Kejadian - $cameraName",
                                                    startedAt = clipStart,
                                                    endedAt = clipEnd,
                                                    duration = durationMs / 1000L,
                                                    durationSeconds = durationMs / 1000L,
                                                    sizeBytes = byteCount,
                                                    status = "available",
                                                    playbackUrl = uri.toString()
                                                )
                                            )
                                            showClipConfirmation = false
                                            loadRecordings(forceRefresh = true)
                                            onNavigateToDetail(id)
                                        }.onFailure {
                                            clipError = it.localizedMessage ?: "Gagal menyimpan klip."
                                        }
                                    }.onFailure {
                                        clipError = it.localizedMessage ?: "Gagal mengambil klip."
                                    }
                                } catch (e: Exception) {
                                    clipError = e.localizedMessage ?: "Gagal mengambil atau menyimpan klip."
                                } finally {
                                    isDownloadingClip = false
                                }
                            }
                        }
                    }
                ) {
                    Text(if (isDownloadingClip) "Mengunduh..." else "Unduh")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClipConfirmation = false }) { Text("Batal") }
            }
        )
    }
}

@Composable
private fun PlaybackFilterCard(
    cameras: List<Camera>,
    selectedCameraId: String?,
    fromMillis: Long,
    toMillis: Long,
    onCameraSelected: (String?) -> Unit,
    onFromSelected: (Long) -> Unit,
    onToSelected: (Long) -> Unit,
    onSearch: () -> Unit,
    onRequestClip: () -> Unit
) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Filter rekaman (maks. 24 jam)", fontWeight = FontWeight.Bold, color = TextPrimary)
            Box {
                OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        cameras.firstOrNull { it.id == selectedCameraId }?.name ?: "Semua kamera",
                        modifier = Modifier.weight(1f)
                    )
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Semua kamera") },
                        onClick = { onCameraSelected(null); expanded = false }
                    )
                    cameras.forEach { camera ->
                        DropdownMenuItem(
                            text = { Text(camera.name) },
                            onClick = { onCameraSelected(camera.id); expanded = false }
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { showDateTimePicker(context, fromMillis, onFromSelected) },
                    modifier = Modifier.weight(1f)
                ) { Text("Dari\n${formatLocalDateTime(fromMillis)}") }
                OutlinedButton(
                    onClick = { showDateTimePicker(context, toMillis, onToSelected) },
                    modifier = Modifier.weight(1f)
                ) { Text("Sampai\n${formatLocalDateTime(toMillis)}") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onSearch, modifier = Modifier.weight(1f)) {
                    Text("Tampilkan")
                }
                OutlinedButton(onClick = onRequestClip, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Tarik Klip")
                }
            }
            Text(
                "Rekaman otomatis tersimpan maksimal 24 jam.",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        }
    }
}

private fun showDateTimePicker(
    context: android.content.Context,
    initialMillis: Long,
    onSelected: (Long) -> Unit
) {
    val initial = Calendar.getInstance().apply { timeInMillis = initialMillis }
    DatePickerDialog(
        context,
        { _, year, month, day ->
            val selectedDate = Calendar.getInstance().apply {
                timeInMillis = initialMillis
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month)
                set(Calendar.DAY_OF_MONTH, day)
            }
            TimePickerDialog(
                context,
                { _, hour, minute ->
                    selectedDate.set(Calendar.HOUR_OF_DAY, hour)
                    selectedDate.set(Calendar.MINUTE, minute)
                    selectedDate.set(Calendar.SECOND, 0)
                    selectedDate.set(Calendar.MILLISECOND, 0)
                    onSelected(selectedDate.timeInMillis)
                },
                initial.get(Calendar.HOUR_OF_DAY),
                initial.get(Calendar.MINUTE),
                true
            ).show()
        },
        initial.get(Calendar.YEAR),
        initial.get(Calendar.MONTH),
        initial.get(Calendar.DAY_OF_MONTH)
    ).show()
}

private fun formatLocalDateTime(millis: Long): String =
    SimpleDateFormat("dd MMM HH:mm", Locale("in", "ID")).format(Date(millis))

private fun formatUtcIso(millis: Long): String =
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.format(Date(millis))

@Composable
fun RecordingCard(
    recording: Recording,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp, 48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(PrimaryBlue.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                if (!recording.thumbnailUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = recording.thumbnailUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.25f))
                    )
                }
                Icon(
                    imageVector = Icons.Default.PlayCircle,
                    contentDescription = null,
                    tint = if (!recording.thumbnailUrl.isNullOrBlank()) Color.White else PrimaryBlue,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = recording.title.ifBlank { "Rekaman CCTV" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${recording.cameraName} • ${Formatters.formatIsoDate(recording.startedAt)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Durasi: ${Formatters.formatDuration(recording.duration)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                    Text(
                        text = "Ukuran: ${Formatters.formatBytes(recording.sizeBytes)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                    if (recording.hasMotion) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFFFEF3C7))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.DirectionsRun,
                                    contentDescription = null,
                                    tint = StatusWarning,
                                    modifier = Modifier.size(10.dp)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text("Gerakan", color = StatusWarning, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
