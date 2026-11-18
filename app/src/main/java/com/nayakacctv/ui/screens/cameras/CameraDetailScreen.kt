package com.nayakacctv.ui.screens.cameras

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nayakacctv.data.model.Camera
import com.nayakacctv.data.repository.NayakaRepository
import com.nayakacctv.ui.components.NayakaButton
import com.nayakacctv.ui.components.NayakaTopAppBar
import com.nayakacctv.ui.components.StatusBadge
import com.nayakacctv.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun CameraDetailScreen(
    cameraId: String,
    repository: NayakaRepository,
    onNavigateBack: () -> Unit,
    onNavigateToLiveView: (String) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var camera by remember { mutableStateOf<Camera?>(null) }
    var motionDetection by remember { mutableStateOf(true) }
    var notificationEnabled by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }

    LaunchedEffect(cameraId) {
        coroutineScope.launch {
            val res = repository.getCameraDetail(cameraId)
            res.getOrNull()?.let {
                camera = it
            }
        }
    }

    Scaffold(
        topBar = {
            NayakaTopAppBar(
                title = camera?.name ?: "Detail Kamera",
                canNavigateBack = true,
                onNavigateBack = onNavigateBack
            )
        },
        containerColor = BackgroundSlate
    ) { padding ->
        val cam = camera
        if (cam == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PrimaryBlue)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Status Header Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = cam.name,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = cam.location.ifBlank { "Lokasi tidak diset" },
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                        StatusBadge(cam.status)
                    }
                }

                // Quick Action Button
                NayakaButton(
                    text = "Buka Siaran Langsung (Live View)",
                    onClick = { onNavigateToLiveView(cam.id) }
                )

                // Technical Specs Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Spesifikasi Perangkat",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        SpecRow("Model", cam.model.ifBlank { "Nayaka SmartCam Pro" })
                        SpecRow("Nomor Seri", cam.serialNumber.ifBlank { "SN-98234-NK" })
                        SpecRow("Alamat IP", cam.ip.ifBlank { "192.168.1.120" })
                        SpecRow("Resolusi", cam.resolution)
                        SpecRow("Sudut Pandang (FOV)", "${cam.fov}°")
                        SpecRow("Baterai", if (cam.battery != null) "${cam.battery}%" else "Daya Langsung (AC)")
                    }
                }

                // Settings Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Pengaturan Pemantauan",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Deteksi Gerakan Cerdas", fontWeight = FontWeight.SemiBold, color = TextPrimary)
                                Text("Kamera akan mencatat rekaman saat ada pergerakan", fontSize = 12.sp, color = TextSecondary)
                            }
                            Switch(
                                checked = motionDetection,
                                onCheckedChange = {
                                    motionDetection = it
                                    coroutineScope.launch {
                                        repository.updateCameraSettings(cam.id, motionDetection, notificationEnabled)
                                    }
                                },
                                colors = SwitchDefaults.colors(checkedThumbColor = PrimaryBlue)
                            )
                        }

                        Divider(modifier = Modifier.padding(vertical = 12.dp), color = BorderSubtle)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Notifikasi Instan", fontWeight = FontWeight.SemiBold, color = TextPrimary)
                                Text("Kirim push alert ke HP saat ancaman terdeteksi", fontSize = 12.sp, color = TextSecondary)
                            }
                            Switch(
                                checked = notificationEnabled,
                                onCheckedChange = {
                                    notificationEnabled = it
                                    coroutineScope.launch {
                                        repository.updateCameraSettings(cam.id, motionDetection, notificationEnabled)
                                    }
                                },
                                colors = SwitchDefaults.colors(checkedThumbColor = PrimaryBlue)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpecRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = TextSecondary, fontSize = 14.sp)
        Text(text = value, color = TextPrimary, fontWeight = FontWeight.Medium, fontSize = 14.sp)
    }
}
