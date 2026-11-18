package com.nayakacctv.ui.screens.help

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nayakacctv.data.repository.NayakaRepository
import com.nayakacctv.ui.components.NayakaTopAppBar
import com.nayakacctv.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun TermsScreen(
    repository: NayakaRepository,
    onNavigateBack: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var termsContent by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        coroutineScope.launch {
            val res = repository.getTerms()
            termsContent = res.getOrNull()?.content ?: """
                1. Ketentuan Umum
                Dengan menggunakan aplikasi Nayaka CCTV, Anda menyetujui seluruh syarat dan ketentuan yang diatur oleh pihak penyedia layanan demi menjaga privasi dan keamanan bersama.

                2. Keamanan Akun & Kredensial
                Pengguna bertanggung jawab penuh atas kerahasiaan kata sandi, token otentikasi, serta akses biometrik pada perangkat masing-masing.

                3. Privasi Data & Siaran Video
                Rekaman video dan siaran langsung kamera diproses dengan enkripsi end-to-end. Pihak Nayaka tidak membagikan rekaman pribadi Anda kepada pihak ketiga tanpa izin resmi atau perintah hukum tertulis.

                4. Layanan Darurat (SOS)
                Fitur panggilan darurat dan sirine disediakan sebagai alat bantu. Respon dari otoritas terkait (Polisi, Pemadam Kebakaran, Ambulans) bergantung pada ketersediaan dan kebijakan operasional masing-masing instansi.
            """.trimIndent()
        }
    }

    Scaffold(
        topBar = {
            NayakaTopAppBar(
                title = "Syarat & Ketentuan",
                canNavigateBack = true,
                onNavigateBack = onNavigateBack
            )
        },
        containerColor = BackgroundSlate
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "Syarat & Ketentuan Penggunaan",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Pembaruan terakhir: 2026",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = termsContent,
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary,
                lineHeight = 24.sp
            )
        }
    }
}
