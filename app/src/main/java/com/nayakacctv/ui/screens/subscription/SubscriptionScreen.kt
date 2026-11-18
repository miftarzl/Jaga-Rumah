package com.nayakacctv.ui.screens.subscription

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDone
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
import com.nayakacctv.data.model.Subscription
import com.nayakacctv.data.repository.NayakaRepository
import com.nayakacctv.ui.components.NayakaButton
import com.nayakacctv.ui.components.NayakaTopAppBar
import com.nayakacctv.ui.theme.*
import com.nayakacctv.ui.utils.Formatters
import kotlinx.coroutines.launch

@Composable
fun SubscriptionScreen(
    repository: NayakaRepository,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var subscriptions by remember { mutableStateOf<List<Subscription>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        coroutineScope.launch {
            isLoading = true
            val res = repository.getSubscriptions()
            subscriptions = res.getOrDefault(
                listOf(
                    Subscription("sub-1", "Paket Standar Cloud", "active", "2026-10-01", "2026-12-01", 75000, 14),
                    Subscription("sub-2", "Paket Bisnis Cloud Pro", "available", null, null, 150000, 30)
                )
            )
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            NayakaTopAppBar(
                title = "Langganan Cloud",
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
            item {
                Text(
                    text = "Penyimpanan Rekaman Cloud",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "Simpan rekaman rekaman kamera Anda secara aman di cloud terlindungi enkripsi 256-bit.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }

            items(subscriptions) { sub ->
                val isActive = sub.status.equals("active", ignoreCase = true)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isActive) Color(0xFFEFF6FF) else SurfaceWhite
                    ),
                    border = if (isActive) CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(PrimaryBlue)) else null,
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = sub.planName,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            if (isActive) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(PrimaryBlue)
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text("AKTIF", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "${Formatters.formatIDR(sub.price)} / bulan",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryBlue
                        )

                        Spacer(modifier = Modifier.height(14.dp))
                        PlanFeature("Penyimpanan riwayat rekaman hingga ${sub.storageDays} hari")
                        PlanFeature("Enkripsi data tingkat perbankan AES-256")
                        PlanFeature("Akses playback multi-perangkat tanpa batas")
                        PlanFeature("Pencadangan insiden otomatis saat alarm terpicu")

                        Spacer(modifier = Modifier.height(16.dp))

                        if (isActive) {
                            OutlinedButton(
                                onClick = { Toast.makeText(context, "Kelola langganan via portal web", Toast.LENGTH_SHORT).show() },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Kelola Paket")
                            }
                        } else {
                            NayakaButton(
                                text = "Pilih Paket Ini",
                                onClick = { Toast.makeText(context, "Mengarahkan ke pembayaran...", Toast.LENGTH_SHORT).show() }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlanFeature(text: String) {
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = null,
            tint = StatusOnline,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
    }
}
