package com.nayakacctv.ui.screens.dashboard

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
import com.nayakacctv.data.model.DashboardSummary
import com.nayakacctv.data.repository.NayakaRepository
import com.nayakacctv.ui.components.AlertItemCard
import com.nayakacctv.ui.components.CameraCard
import com.nayakacctv.ui.theme.*
import com.nayakacctv.ui.utils.DashboardSnapshotStore
import kotlinx.coroutines.launch

@Composable
fun DashboardScreen(
    repository: NayakaRepository,
    onNavigateToCameraDetail: (String) -> Unit,
    onNavigateToLiveView: (String) -> Unit,
    onNavigateToEmergency: () -> Unit,
    onNavigateToAlerts: () -> Unit,
    onNavigateToDevices: () -> Unit,
    onNavigateToAutomation: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val dashboardSnapshots by DashboardSnapshotStore.snapshots.collectAsState()
    var summary by remember { mutableStateOf<DashboardSummary?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var snapshotRefreshKey by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val customer = repository.getCurrentCustomer()

    val refreshData: (Boolean) -> Unit = { forceRefresh ->
        snapshotRefreshKey = maxOf(System.currentTimeMillis(), snapshotRefreshKey + 1)
        coroutineScope.launch {
            isLoading = true
            val res = repository.getDashboard(forceRefresh = forceRefresh)
            val dashSummary = res.getOrNull()
            if (dashSummary != null) {
                if (dashSummary.cameras.isEmpty()) {
                    val camRes = repository.getCameras(forceRefresh = forceRefresh)
                    val camList = camRes.getOrDefault(emptyList())
                    if (camList.isNotEmpty()) {
                        summary = dashSummary.copy(
                            fallbackCameras = camList,
                            fallbackTotal = if (dashSummary.totalCameras == 0) camList.size else dashSummary.totalCameras,
                            fallbackOnline = if (dashSummary.onlineCameras == 0) camList.count { it.status == "online" } else dashSummary.onlineCameras,
                            fallbackRecording = if (dashSummary.recordingCameras == 0) camList.count { it.isRecording } else dashSummary.recordingCameras,
                            fallbackOffline = if (dashSummary.offlineCameras == 0) camList.count { it.status == "offline" } else dashSummary.offlineCameras
                        )
                    } else {
                        summary = dashSummary
                    }
                } else {
                    summary = dashSummary
                }
            } else {
                val camRes = repository.getCameras(forceRefresh = forceRefresh)
                val camList = camRes.getOrDefault(emptyList())
                if (camList.isNotEmpty()) {
                    summary = DashboardSummary(
                        fallbackCameras = camList,
                        fallbackTotal = camList.size,
                        fallbackOnline = camList.count { it.status == "online" },
                        fallbackRecording = camList.count { it.isRecording },
                        fallbackOffline = camList.count { it.status == "offline" }
                    )
                }
            }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        refreshData(false)
    }

    Scaffold(
        containerColor = BackgroundSlate
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Profile Greeting
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Halo, ${customer?.fullName ?: "Pengguna"}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Hunian Anda dalam pantauan aktif",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        // Refresh Button
                        IconButton(
                            onClick = { refreshData(true) },
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFEFF6FF))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Muat Ulang",
                                tint = PrimaryBlue,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // Emergency Quick Icon
                        IconButton(
                            onClick = onNavigateToEmergency,
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFEE2E2))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Darurat SOS",
                                tint = StatusDanger,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Home, contentDescription = null, tint = PrimaryBlue)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Rumah Saya",
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFFEFF6FF)
                            ) {
                                Text(
                                    text = "PRATINJAU",
                                    color = PrimaryBlue,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                        Text(
                            text = "Kontrol lampu, pintu, TV, dan perangkat rumah lainnya akan tersedia setelah integrasi perangkat siap.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = onNavigateToDevices,
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Icon(Icons.Default.Devices, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Perangkat", fontSize = 12.sp)
                            }
                            OutlinedButton(
                                onClick = onNavigateToAutomation,
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Icon(Icons.Default.AutoMode, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Otomasi", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // Stat Summary Cards (Grid 2x2 or Row)
            item {
                summary?.let { data ->
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            StatCard(
                                title = "Total Kamera",
                                value = data.totalCameras.toString(),
                                icon = Icons.Default.Videocam,
                                iconBg = Color(0xFFEFF6FF),
                                iconColor = PrimaryBlue,
                                modifier = Modifier.weight(1f)
                            )
                            StatCard(
                                title = "Online",
                                value = data.onlineCameras.toString(),
                                icon = Icons.Default.CheckCircle,
                                iconBg = Color(0xFFDCFCE7),
                                iconColor = StatusOnline,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            StatCard(
                                title = "Sedang Merekam",
                                value = data.recordingCameras.toString(),
                                icon = Icons.Default.FiberManualRecord,
                                iconBg = Color(0xFFFEF9C3),
                                iconColor = StatusRecording,
                                modifier = Modifier.weight(1f)
                            )
                            StatCard(
                                title = "Offline",
                                value = data.offlineCameras.toString(),
                                icon = Icons.Default.CloudOff,
                                iconBg = Color(0xFFF1F5F9),
                                iconColor = StatusOffline,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Emergency SOS Banner
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onNavigateToEmergency),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFDC2626))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Emergency,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "BANTUAN DARURAT (SOS)",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = "Panggil Polisi, Damkar, atau Satpam Lingkungan",
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 12.sp
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = Color.White
                        )
                    }
                }
            }

            // Cameras Section Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Siaran Kamera (Snapshot Terkini)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
            }

            // Cameras List
            val cameras = summary?.cameras ?: emptyList()
            if (cameras.isEmpty() && !isLoading) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.VideocamOff,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Belum ada kamera yang terhubung.",
                                color = TextSecondary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Pastikan perangkat CCTV telah ditugaskan ke akun ini di panel admin.",
                                color = TextMuted,
                                fontSize = 12.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedButton(
                                onClick = { refreshData(true) },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Muat Ulang")
                            }
                        }
                    }
                }
            } else {
                items(cameras.take(3)) { cam ->
                    CameraCard(
                        camera = cam,
                        onClick = { onNavigateToLiveView(cam.id) },
                        snapshotFile = if (cam.status.equals("online", ignoreCase = true)) {
                            null
                        } else {
                            dashboardSnapshots[cam.id]?.file
                                ?: DashboardSnapshotStore.cachedSnapshot(context, cam.id)
                        },
                        loadSnapshotFromServer = true,
                        snapshotRefreshKey = snapshotRefreshKey
                    )
                }
            }

            // Recent Alerts Section Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Insiden & Peringatan Terkini",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Lihat Semua",
                        color = PrimaryBlue,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        modifier = Modifier.clickable { onNavigateToAlerts() }
                    )
                }
            }

            val alerts = summary?.recentAlerts ?: emptyList()
            if (alerts.isEmpty() && !isLoading) {
                item {
                    Text(
                        text = "Tidak ada peringatan ancaman saat ini.",
                        color = TextMuted,
                        fontSize = 14.sp
                    )
                }
            } else {
                items(alerts.take(4)) { alert ->
                    AlertItemCard(
                        alert = alert,
                        onClick = { onNavigateToAlerts() }
                    )
                }
            }
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconBg: Color,
    iconColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
        }
    }
}
