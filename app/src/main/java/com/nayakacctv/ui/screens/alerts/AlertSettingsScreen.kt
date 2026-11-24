package com.nayakacctv.ui.screens.alerts

import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nayakacctv.data.model.Camera
import com.nayakacctv.data.repository.NayakaRepository
import com.nayakacctv.ui.components.NayakaTopAppBar
import com.nayakacctv.ui.theme.BackgroundSlate
import com.nayakacctv.ui.theme.PrimaryBlue
import com.nayakacctv.ui.theme.StatusDanger
import com.nayakacctv.ui.theme.SurfaceWhite
import com.nayakacctv.ui.theme.TextMuted
import com.nayakacctv.ui.theme.TextPrimary
import com.nayakacctv.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import java.util.Locale

private val scheduleDays = listOf("Sen", "Sel", "Rab", "Kam", "Jum", "Sab", "Min")

@Composable
fun AlertSettingsScreen(
    repository: NayakaRepository,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val motionEnabled = remember { mutableStateMapOf<String, Boolean>() }
    val notificationsEnabled = remember { mutableStateMapOf<String, Boolean>() }
    val scheduledCameraIds = remember { mutableStateListOf<String>() }
    val selectedDays = remember { mutableStateListOf("Sen", "Sel", "Rab", "Kam", "Jum") }

    var cameras by remember { mutableStateOf<List<Camera>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var sleepModeEnabled by remember { mutableStateOf(false) }
    var startTime by remember { mutableStateOf("22:00") }
    var endTime by remember { mutableStateOf("06:00") }

    LaunchedEffect(repository) {
        repository.getCameras(forceRefresh = true)
            .onSuccess { loadedCameras ->
                cameras = loadedCameras
                loadedCameras.forEach { camera ->
                    motionEnabled[camera.id] = true
                    notificationsEnabled[camera.id] = true
                }
            }
            .onFailure { error ->
                loadError = error.localizedMessage ?: "Gagal memuat daftar kamera."
            }
        isLoading = false
    }

    fun showTimePicker(initialTime: String, onTimeSelected: (String) -> Unit) {
        val (hour, minute) = initialTime.split(":").mapNotNull(String::toIntOrNull)
            .let { values -> (values.getOrNull(0) ?: 22) to (values.getOrNull(1) ?: 0) }
        TimePickerDialog(
            context,
            { _, selectedHour, selectedMinute ->
                onTimeSelected(String.format(Locale.getDefault(), "%02d:%02d", selectedHour, selectedMinute))
            },
            hour,
            minute,
            true
        ).show()
    }

    Scaffold(
        topBar = {
            NayakaTopAppBar(
                title = "Pengaturan Alert",
                canNavigateBack = true,
                onNavigateBack = onNavigateBack
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = BackgroundSlate
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                PreviewAlertNotice()
            }

            item {
                SectionTitle(
                    icon = Icons.Default.Security,
                    title = "Pengaturan per kamera",
                    subtitle = "Pilih kamera yang mendeteksi gerakan dan mengirim notifikasi."
                )
            }

            when {
                isLoading -> item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = PrimaryBlue)
                    }
                }
                loadError != null -> item {
                    Text(
                        text = loadError.orEmpty(),
                        color = StatusDanger,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                cameras.isEmpty() -> item {
                    InfoCard("Belum ada kamera. Tambahkan kamera terlebih dahulu untuk mengatur alert.")
                }
                else -> items(cameras, key = { it.id }) { camera ->
                    CameraAlertCard(
                        camera = camera,
                        motionEnabled = motionEnabled[camera.id] ?: false,
                        notificationsEnabled = notificationsEnabled[camera.id] ?: false,
                        onMotionChanged = { motionEnabled[camera.id] = it },
                        onNotificationsChanged = { notificationsEnabled[camera.id] = it }
                    )
                }
            }

            item {
                SectionTitle(
                    icon = Icons.Default.Bedtime,
                    title = "Jadwal Sleep Mode",
                    subtitle = "Tentukan kapan alert aktif dan kamera yang mengikuti jadwal."
                )
            }

            item {
                SleepScheduleCard(
                    enabled = sleepModeEnabled,
                    startTime = startTime,
                    endTime = endTime,
                    days = selectedDays,
                    cameras = cameras,
                    selectedCameraIds = scheduledCameraIds,
                    onEnabledChanged = { sleepModeEnabled = it },
                    onStartTimeClick = { showTimePicker(startTime) { startTime = it } },
                    onEndTimeClick = { showTimePicker(endTime) { endTime = it } },
                    onDayClick = { day ->
                        if (day in selectedDays) selectedDays.remove(day) else selectedDays.add(day)
                    },
                    onCameraCheckedChange = { cameraId, checked ->
                        if (checked && cameraId !in scheduledCameraIds) {
                            scheduledCameraIds.add(cameraId)
                        } else if (!checked) {
                            scheduledCameraIds.remove(cameraId)
                        }
                    }
                )
            }

            item {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(
                                "Pratinjau UI saja. Pengaturan belum tersimpan atau diterapkan ke kamera."
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) {
                    Text("Simpan pengaturan")
                }
            }
        }
    }
}

@Composable
private fun PreviewAlertNotice() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Notifications, contentDescription = null, tint = PrimaryBlue)
            Column(
                modifier = Modifier.padding(start = 10.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text("Pratinjau UI", fontWeight = FontWeight.Bold, color = TextPrimary)
                Text(
                    "Pengaturan belum tersambung ke deteksi gerak, push notification, atau jadwal backend.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(20.dp))
            Text(
                text = title,
                modifier = Modifier.padding(start = 8.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        }
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
    }
}

@Composable
private fun CameraAlertCard(
    camera: Camera,
    motionEnabled: Boolean,
    notificationsEnabled: Boolean,
    onMotionChanged: (Boolean) -> Unit,
    onNotificationsChanged: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(camera.name, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text(
                camera.location.ifBlank { "Lokasi belum diatur" },
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
            Spacer(modifier = Modifier.height(8.dp))
            SettingSwitchRow(
                title = "Deteksi gerak",
                subtitle = "Mendeteksi aktivitas dari kamera ini.",
                checked = motionEnabled,
                onCheckedChange = onMotionChanged
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SettingSwitchRow(
                title = "Notifikasi alert",
                subtitle = if (motionEnabled) {
                    "Kirim alert saat gerakan terdeteksi."
                } else {
                    "Aktifkan deteksi gerak untuk menerima alert."
                },
                checked = notificationsEnabled,
                enabled = motionEnabled,
                onCheckedChange = onNotificationsChanged
            )
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}

@Composable
private fun SleepScheduleCard(
    enabled: Boolean,
    startTime: String,
    endTime: String,
    days: List<String>,
    cameras: List<Camera>,
    selectedCameraIds: List<String>,
    onEnabledChanged: (Boolean) -> Unit,
    onStartTimeClick: () -> Unit,
    onEndTimeClick: () -> Unit,
    onDayClick: (String) -> Unit,
    onCameraCheckedChange: (String, Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SettingSwitchRow(
                title = "Aktifkan Sleep Mode",
                subtitle = "Jadwalkan waktu alert untuk kamera pilihan.",
                checked = enabled,
                onCheckedChange = onEnabledChanged
            )

            if (enabled) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TimeButton(
                        label = "Mulai",
                        time = startTime,
                        modifier = Modifier.weight(1f),
                        onClick = onStartTimeClick
                    )
                    TimeButton(
                        label = "Selesai",
                        time = endTime,
                        modifier = Modifier.weight(1f),
                        onClick = onEndTimeClick
                    )
                }

                Text("Hari aktif", fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    scheduleDays.forEach { day ->
                        FilterChip(
                            selected = day in days,
                            onClick = { onDayClick(day) },
                            label = { Text(day) }
                        )
                    }
                }

                Text("Kamera yang mengikuti jadwal", fontWeight = FontWeight.SemiBold, color = TextPrimary)
                if (cameras.isEmpty()) {
                    Text("Daftar kamera belum tersedia.", color = TextSecondary)
                } else {
                    cameras.forEach { camera ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = camera.id in selectedCameraIds,
                                onCheckedChange = { onCameraCheckedChange(camera.id, it) }
                            )
                            Column {
                                Text(camera.name, color = TextPrimary)
                                Text(
                                    camera.location.ifBlank { "Lokasi belum diatur" },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextMuted
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimeButton(
    label: String,
    time: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    androidx.compose.material3.OutlinedButton(
        onClick = onClick,
        modifier = modifier
    ) {
        Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(18.dp))
        Column(
            modifier = Modifier.padding(start = 8.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text(time, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun InfoCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(16.dp),
            color = TextSecondary,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
