package com.nayakacctv.ui.screens.smarthome

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nayakacctv.ui.theme.BackgroundSlate
import com.nayakacctv.ui.theme.PrimaryBlue
import com.nayakacctv.ui.theme.StatusInfo
import com.nayakacctv.ui.theme.SurfaceWhite
import com.nayakacctv.ui.theme.TextMuted
import com.nayakacctv.ui.theme.TextPrimary
import com.nayakacctv.ui.theme.TextSecondary

private data class PreviewDevice(
    val name: String,
    val room: String,
    val category: String,
    val icon: ImageVector,
    val previewOn: Boolean
)

private val previewDevices = listOf(
    PreviewDevice("Lampu utama", "Ruang Tamu", "Lampu", Icons.Default.Lightbulb, true),
    PreviewDevice("Televisi", "Ruang Tamu", "TV", Icons.Default.Tv, false),
    PreviewDevice("Kunci pintu", "Pintu Masuk", "Pintu", Icons.Default.Lock, true),
    PreviewDevice("AC", "Kamar Tidur", "AC", Icons.Default.AcUnit, false)
)

@Composable
fun SmartHomeDevicesScreen() {
    val categories = listOf("Semua", "Lampu", "Pintu", "TV", "AC")
    var selectedCategory by remember { mutableStateOf("Semua") }
    val devices = previewDevices.filter {
        selectedCategory == "Semua" || it.category == selectedCategory
    }
    val rooms = devices.groupBy { it.room }.toList()

    Scaffold(containerColor = BackgroundSlate) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                ScreenHeading(
                    title = "Perangkat Rumah",
                    subtitle = "Perangkat berdasarkan ruangan"
                )
            }
            item { PreviewNotice() }
            item {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    categories.forEach { category ->
                        FilterChip(
                            selected = selectedCategory == category,
                            onClick = { selectedCategory = category },
                            label = { Text(category) }
                        )
                    }
                }
            }
            items(rooms, key = { it.first }) { (room, roomDevices) ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = room,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        roomDevices.forEach { device ->
                            PreviewDeviceRow(device)
                        }
                    }
                }
            }
            item {
                Text(
                    text = "Contoh perangkat di atas hanya pratinjau UI; belum terhubung ke perangkat rumah.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }
}

@Composable
fun SmartHomeAutomationScreen() {
    val automations = listOf(
        Triple("Mode Pulang", "Ide: nyalakan lampu ruang tamu saat tiba di rumah.", Icons.Default.Person),
        Triple("Mode Tidur", "Ide: matikan lampu dan kunci pintu pada malam hari.", Icons.Default.Lock),
        Triple("Lampu Teras", "Ide: nyalakan lampu sesuai jadwal malam.", Icons.Default.Schedule)
    )

    Scaffold(containerColor = BackgroundSlate) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                ScreenHeading(
                    title = "Otomasi",
                    subtitle = "Rancang rutinitas rumah"
                )
            }
            item { PreviewNotice() }
            item {
                Text(
                    text = "CONTOH RUTINITAS",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary,
                    fontWeight = FontWeight.Bold
                )
            }
            items(automations, key = { it.first }) { (title, description, icon) ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PreviewIcon(icon)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = title,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = description,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            PreviewTag()
                        }
                    }
                }
            }
            item {
                OutlinedButton(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Tambah otomasi · segera hadir")
                }
            }
        }
    }
}

@Composable
fun SmartHomeMoreScreen(
    onNavigateToPlayback: () -> Unit,
    onNavigateToAlerts: () -> Unit,
    onNavigateToAlertSettings: () -> Unit,
    onNavigateToProfile: () -> Unit
) {
    Scaffold(containerColor = BackgroundSlate) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                ScreenHeading(
                    title = "Lainnya",
                    subtitle = "Aktivitas dan pengaturan aplikasi"
                )
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.History, contentDescription = null, tint = PrimaryBlue)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Aktivitas Smart Home",
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Riwayat perangkat rumah akan muncul setelah perangkat terhubung.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        PreviewTag()
                    }
                }
            }
            item {
                MoreActionCard(
                    icon = Icons.Default.Notifications,
                    title = "Pengaturan Alert",
                    subtitle = "Atur alert tiap kamera dan jadwal Sleep Mode",
                    onClick = onNavigateToAlertSettings
                )
            }
            item {
                MoreActionCard(
                    icon = Icons.Default.Movie,
                    title = "Playback CCTV",
                    subtitle = "Lihat rekaman kamera",
                    onClick = onNavigateToPlayback
                )
            }
            item {
                MoreActionCard(
                    icon = Icons.Default.Notifications,
                    title = "Peringatan CCTV",
                    subtitle = "Lihat notifikasi dan alert",
                    onClick = onNavigateToAlerts
                )
            }
            item {
                MoreActionCard(
                    icon = Icons.Default.Person,
                    title = "Profil dan Pengaturan",
                    subtitle = "Akun, bantuan, dan preferensi",
                    onClick = onNavigateToProfile
                )
            }
        }
    }
}

@Composable
private fun ScreenHeading(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
    }
}

@Composable
private fun PreviewNotice() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.PlayCircle, contentDescription = null, tint = PrimaryBlue)
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = "Pratinjau UI",
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "Belum terhubung ke perangkat. Kontrol belum mengirim perintah.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
private fun PreviewDeviceRow(device: PreviewDevice) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PreviewIcon(device.icon)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = device.name, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Text(
                text = if (device.previewOn) "Contoh: aktif" else "Contoh: nonaktif",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
        Switch(
            checked = device.previewOn,
            onCheckedChange = {},
            enabled = false
        )
    }
}

@Composable
private fun PreviewIcon(icon: ImageVector) {
    Surface(
        modifier = Modifier.size(42.dp),
        shape = CircleShape,
        color = Color(0xFFEFF6FF)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = PrimaryBlue,
            modifier = Modifier.padding(10.dp)
        )
    }
}

@Composable
private fun PreviewTag() {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = Color(0xFFEFF6FF)
    ) {
        Text(
            text = "PRATINJAU",
            color = StatusInfo,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun MoreActionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = PrimaryBlue)
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(title, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }
    }
}
