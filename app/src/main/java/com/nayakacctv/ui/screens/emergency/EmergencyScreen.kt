package com.nayakacctv.ui.screens.emergency

import android.content.Intent
import android.net.Uri
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
import com.nayakacctv.data.model.EmergencyContact
import com.nayakacctv.data.repository.NayakaRepository
import com.nayakacctv.ui.components.NayakaTopAppBar
import com.nayakacctv.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun EmergencyScreen(
    repository: NayakaRepository,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var contacts by remember { mutableStateOf<List<EmergencyContact>>(emptyList()) }
    var showConfirmDialog by remember { mutableStateOf(false) }
    var isTriggeringSos by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        coroutineScope.launch {
            val res = repository.getEmergencyContacts()
            contacts = res.getOrDefault(
                listOf(
                    EmergencyContact("c-1", "Kepolisian RI", "110", "Sentra Pelayanan Kepolisian Terpadu", "police"),
                    EmergencyContact("c-2", "Pemadam Kebakaran", "113", "Posko Damkar & Penyelamatan", "fire"),
                    EmergencyContact("c-3", "Ambulans & Medis", "119", "Layanan Gawat Darurat Medis", "medical"),
                    EmergencyContact("c-4", "Pos Keamanan Lingkungan", "081234567890", "Regu Jaga Satpam Komplek", "security"),
                    EmergencyContact("c-5", "Teknisi CCTV Darurat", "081987654321", "Dukungan Teknis 24 Jam", "support")
                )
            )
        }
    }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = {
                Text(
                    text = "Picu Alarm Darurat (SOS)?",
                    fontWeight = FontWeight.Bold,
                    color = StatusDanger
                )
            },
            text = {
                Text(
                    text = "Permintaan SOS akan dikirim ke server. Sirine hanya berbunyi jika backend dan kamera mendukung perintah alarm.",
                    color = TextPrimary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmDialog = false
                        isTriggeringSos = true
                        coroutineScope.launch {
                            try {
                                repository.triggerSos()
                                    .onSuccess {
                                        Toast.makeText(
                                            context,
                                            "Permintaan SOS berhasil dikirim ke server.",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                    .onFailure { error ->
                                        Toast.makeText(
                                            context,
                                            "Gagal mengirim SOS: ${error.localizedMessage ?: "periksa koneksi dan coba lagi."}",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                            } finally {
                                isTriggeringSos = false
                            }
                        }
                    },
                    enabled = !isTriggeringSos,
                    colors = ButtonDefaults.buttonColors(containerColor = StatusDanger)
                ) {
                    Text(
                        text = if (isTriggeringSos) "MENGIRIM..." else "AKTIFKAN SEKARANG",
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text("Batal", color = TextSecondary)
                }
            }
        )
    }

    Scaffold(
        topBar = {
            NayakaTopAppBar(
                title = "Layanan Darurat",
                canNavigateBack = true,
                onNavigateBack = onNavigateBack
            )
        },
        containerColor = BackgroundSlate
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Panic Button Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "TOMBOL DARURAT (PANIC BUTTON)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = StatusDanger
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Tekan tombol di bawah jika terdeteksi ancaman mendesak",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        // Large Red SOS Circle
                        Box(
                            modifier = Modifier
                                .size(110.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFEE2E2))
                                .clickable(enabled = !isTriggeringSos) { showConfirmDialog = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(90.dp)
                                    .clip(CircleShape)
                                    .background(StatusDanger),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "SOS",
                                    color = Color.White,
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        if (isTriggeringSos) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = StatusDanger
                                )
                                Text(
                                    text = "Mengirim permintaan darurat...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
            }

            // Emergency Contacts Header
            item {
                Text(
                    text = "Panggilan Langsung Nomor Darurat",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }

            // Contacts List
            items(contacts) { contact ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val intent = Intent(Intent.ACTION_DIAL).apply {
                                data = Uri.parse("tel:${contact.phone}")
                            }
                            context.startActivity(intent)
                        },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFEFF6FF)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = when (contact.category?.lowercase()) {
                                    "fire" -> Icons.Default.LocalFireDepartment
                                    "medical" -> Icons.Default.LocalHospital
                                    "police" -> Icons.Default.LocalPolice
                                    "security" -> Icons.Default.Shield
                                    else -> Icons.Default.Call
                                },
                                contentDescription = null,
                                tint = PrimaryBlue,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = contact.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            contact.description?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )
                            }
                            Text(
                                text = contact.phone,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = PrimaryBlue
                            )
                        }

                        Icon(
                            imageVector = Icons.Default.Phone,
                            contentDescription = "Telepon",
                            tint = StatusOnline
                        )
                    }
                }
            }
        }
    }
}
