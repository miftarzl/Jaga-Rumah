package com.nayakacctv.ui.screens.cameras

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Videocam
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
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var camera by remember { mutableStateOf<Camera?>(null) }
    var motionDetection by remember { mutableStateOf(true) }
    var notificationEnabled by remember { mutableStateOf(true) }

    // Dialog Edit Kamera
    var showEditDialog by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf("") }
    var editLocation by remember { mutableStateOf("") }
    var editStreamUrl by remember { mutableStateOf("") }
    var isSavingEdit by remember { mutableStateOf(false) }

    // Dialog Hapus / Lepas Kamera
    var showDeleteDialog by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }

    val reloadCamera = {
        coroutineScope.launch {
            val res = repository.getCameraDetail(cameraId)
            res.getOrNull()?.let {
                camera = it
                editName = it.name
                editLocation = it.location
                editStreamUrl = it.streamUrl
            }
        }
    }

    LaunchedEffect(cameraId) {
        reloadCamera()
    }

    Scaffold(
        topBar = {
            NayakaTopAppBar(
                title = camera?.name ?: "Detail Kamera",
                canNavigateBack = true,
                onNavigateBack = onNavigateBack,
                actions = {
                    if (camera != null) {
                        IconButton(onClick = {
                            editName = camera?.name ?: ""
                            editLocation = camera?.location ?: ""
                            editStreamUrl = camera?.streamUrl ?: ""
                            showEditDialog = true
                        }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit Kamera", tint = PrimaryBlue)
                        }
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Lepas Kamera", tint = StatusDanger)
                        }
                    }
                }
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
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = cam.name,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = TextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = cam.location.ifBlank { "Lokasi belum diset" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )
                            }
                        }
                        StatusBadge(cam.status)
                    }
                }

                // Quick Action Button
                NayakaButton(
                    text = "Buka Siaran Langsung (Live View)",
                    onClick = { onNavigateToLiveView(cam.id) }
                )

                // Tombol Aksi CRUD Kamera
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            editName = cam.name
                            editLocation = cam.location
                            editStreamUrl = cam.streamUrl
                            showEditDialog = true
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp), tint = PrimaryBlue)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Edit Kamera", color = PrimaryBlue, fontWeight = FontWeight.SemiBold)
                    }

                    OutlinedButton(
                        onClick = { showDeleteDialog = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusDanger)
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp), tint = StatusDanger)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Lepas Kamera", color = StatusDanger, fontWeight = FontWeight.SemiBold)
                    }
                }

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
                        SpecRow("Alamat Stream", if (cam.streamUrl.isNotBlank()) cam.streamUrl else "Otomatis diatur backend")
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

                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = BorderSubtle)

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

    // ── Dialog Edit Kamera ──────────────────────────────────────────────────
    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { if (!isSavingEdit) showEditDialog = false },
            title = {
                Text("Edit Informasi Kamera", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Nama Kamera") },
                        placeholder = { Text("Contoh: Kamera Depan Rumah") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = editLocation,
                        onValueChange = { editLocation = it },
                        label = { Text("Lokasi / Area") },
                        placeholder = { Text("Contoh: Halaman Depan, Garasi") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = editStreamUrl,
                        onValueChange = { editStreamUrl = it },
                        label = { Text("URL Sumber Stream CCTV (Opsional)") },
                        placeholder = { Text("Contoh: rtsp://... atau biarkan kosong") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editName.isBlank()) {
                            Toast.makeText(context, "Nama kamera tidak boleh kosong", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        isSavingEdit = true
                        coroutineScope.launch {
                            val res = repository.updateCamera(
                                id = cameraId,
                                name = editName,
                                location = editLocation,
                                streamUrl = editStreamUrl
                            )
                            isSavingEdit = false
                            showEditDialog = false
                            res.onSuccess {
                                camera = it
                                Toast.makeText(context, "Informasi kamera berhasil diperbarui!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                    enabled = !isSavingEdit
                ) {
                    if (isSavingEdit) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text("Simpan Perubahan", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showEditDialog = false },
                    enabled = !isSavingEdit
                ) {
                    Text("Batal")
                }
            }
        )
    }

    // ── Dialog Lepas Kamera ─────────────────────────────────────────────────
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { if (!isDeleting) showDeleteDialog = false },
            title = {
                Text("Lepas Kamera dari Akun?", fontWeight = FontWeight.Bold, color = StatusDanger)
            },
            text = {
                Text(
                    text = "Kamera \"${camera?.name}\" akan dilepas dari daftar perangkat akun Anda. Anda dapat mengklaimnya kembali kapan saja dengan kode klaim perangkat.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        isDeleting = true
                        coroutineScope.launch {
                            repository.deleteCamera(cameraId)
                            isDeleting = false
                            showDeleteDialog = false
                            Toast.makeText(context, "Kamera berhasil dilepas dari akun", Toast.LENGTH_SHORT).show()
                            onNavigateBack()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusDanger),
                    enabled = !isDeleting
                ) {
                    if (isDeleting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text("Lepas Kamera", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteDialog = false },
                    enabled = !isDeleting
                ) {
                    Text("Batal")
                }
            }
        )
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
