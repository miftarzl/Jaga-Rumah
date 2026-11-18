package com.nayakacctv.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nayakacctv.data.repository.NayakaRepository
import com.nayakacctv.ui.components.NayakaButton
import com.nayakacctv.ui.components.NayakaTextField
import com.nayakacctv.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun ForgotPasswordScreen(
    repository: NayakaRepository,
    onCodeSent: (String) -> Unit,
    onNavigateBack: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundSlate)
            .padding(24.dp)
    ) {
        IconButton(
            onClick = onNavigateBack,
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali", tint = TextPrimary)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Lupa Kata Sandi",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        Text(
            text = "Masukkan alamat email yang terdaftar untuk menerima kode verifikasi OTP pemulihan sandi.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(32.dp))

        NayakaTextField(
            value = email,
            onValueChange = { email = it; errorMessage = null },
            label = "Email Terdaftar",
            placeholder = "nama@email.com",
            leadingIcon = Icons.Default.Email,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
        )

        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFFEE2E2))
                    .padding(12.dp)
            ) {
                Text(errorMessage ?: "", color = StatusDanger, fontSize = 13.sp)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        NayakaButton(
            text = "Kirim Kode Verifikasi",
            isLoading = isLoading,
            onClick = {
                if (email.isBlank()) {
                    errorMessage = "Silakan masukkan alamat email Anda."
                    return@NayakaButton
                }
                isLoading = true
                coroutineScope.launch {
                    val result = repository.forgotPassword(email.trim())
                    isLoading = false
                    result.onSuccess {
                        onCodeSent(email.trim())
                    }.onFailure {
                        errorMessage = it.message ?: "Gagal mengirim kode."
                    }
                }
            }
        )
    }
}
