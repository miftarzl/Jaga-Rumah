package com.nayakacctv.ui.screens.help

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nayakacctv.data.model.HelpArticle
import com.nayakacctv.data.repository.NayakaRepository
import com.nayakacctv.ui.components.NayakaTopAppBar
import com.nayakacctv.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun HelpScreen(
    repository: NayakaRepository,
    onNavigateBack: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var articles by remember { mutableStateOf<List<HelpArticle>>(emptyList()) }

    LaunchedEffect(Unit) {
        coroutineScope.launch {
            val res = repository.getHelpArticles()
            articles = res.getOrDefault(
                listOf(
                    HelpArticle("1", "Cara Menghubungkan Kamera CCTV Baru", "Panduan langkah demi langkah memindai QR code dan menghubungkan kamera ke jaringan WiFi rumah."),
                    HelpArticle("2", "Mengatur Sensitivitas Deteksi Gerakan", "Tips menyetel ambang deteksi agar tidak terganggu oleh hewan peliharaan atau bayangan."),
                    HelpArticle("3", "Mengunduh & Menyimpan Video Rekaman", "Cara mengekspor klip rekaman ke galeri ponsel Anda untuk keperluan arsip atau bukti."),
                    HelpArticle("4", "Menggunakan Tombol Bantuan Darurat (SOS)", "Penjelasan alur kerja ketika tombol darurat ditekan dan siapa saja yang dihubungi.")
                )
            )
        }
    }

    Scaffold(
        topBar = {
            NayakaTopAppBar(
                title = "Pusat Bantuan",
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(articles) { article ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = article.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = article.summary,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }
            }
        }
    }
}
