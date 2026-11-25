package com.nayakacctv.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nayakacctv.data.repository.NayakaRepository
import com.nayakacctv.ui.components.NayakaButton
import com.nayakacctv.ui.components.NayakaTextField
import com.nayakacctv.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun ResetPasswordScreen(
    email: String,
    repository: NayakaRepository,
    onResetSuccess: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
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
            text = "Reset Kata Sandi",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        Text(
            text = "Masukkan kode verifikasi yang dikirim ke $email dan buat kata sandi baru.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(28.dp))

        NayakaTextField(
            value = code,
            onValueChange = { code = it; errorMessage = null },
            label = "Kode OTP / Verifikasi",
            placeholder = "6 digit kode",
            leadingIcon = Icons.Default.Pin,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )

        Spacer(modifier = Modifier.height(16.dp))

        NayakaTextField(
            value = newPassword,
            onValueChange = { newPassword = it.filterNot(Char::isWhitespace); errorMessage = null },
            label = "Kata Sandi Baru",
            placeholder = "Minimal 8 karakter",
            leadingIcon = Icons.Default.Lock,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )

        Spacer(modifier = Modifier.height(16.dp))

        NayakaTextField(
            value = confirmPassword,
            onValueChange = { confirmPassword = it.filterNot(Char::isWhitespace); errorMessage = null },
            label = "Konfirmasi Kata Sandi Baru",
            placeholder = "Ulangi kata sandi baru",
            leadingIcon = Icons.Default.Lock,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
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
            text = "Simpan Kata Sandi Baru",
            isLoading = isLoading,
            onClick = {
                if (code.isBlank() || newPassword.isBlank()) {
                    errorMessage = "Silakan lengkapi kode dan kata sandi baru."
                    return@NayakaButton
                }
                if (newPassword != confirmPassword) {
                    errorMessage = "Konfirmasi kata sandi tidak cocok."
                    return@NayakaButton
                }

                isLoading = true
                coroutineScope.launch {
                    val result = repository.resetPassword(email, code.trim(), newPassword)
                    isLoading = false
                    result.onSuccess {
                        onResetSuccess()
                    }.onFailure {
                        errorMessage = it.message ?: "Gagal mereset kata sandi."
                    }
                }
            }
        )
    }
}
