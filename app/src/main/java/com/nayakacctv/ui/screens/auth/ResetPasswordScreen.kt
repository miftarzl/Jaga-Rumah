package com.nayakacctv.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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
    var currentStep by remember { mutableStateOf(1) } // 1: Input OTP, 2: Input New Password
    var code by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var newPasswordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var isResendingOtp by remember { mutableStateOf(false) }
    var successNotice by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundSlate)
            .padding(24.dp)
    ) {
        IconButton(
            onClick = {
                if (currentStep == 2) {
                    currentStep = 1
                    errorMessage = null
                } else {
                    onNavigateBack()
                }
            },
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali", tint = TextPrimary)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Indikator Step
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(PrimaryBlue)
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (currentStep == 2) PrimaryBlue else BorderSubtle)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        if (currentStep == 1) {
            // ── STEP 1: VERIFIKASI KODE OTP ──────────────────────────────────────────
            Text(
                text = "Verifikasi Kode OTP",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                text = "Masukkan kode verifikasi 6 digit yang telah dikirim ke email $email.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(28.dp))

            NayakaTextField(
                value = code,
                onValueChange = {
                    val sanitized = it.filter(Char::isDigit).take(6)
                    code = sanitized
                    errorMessage = null
                    successNotice = null
                },
                label = "Kode OTP / Verifikasi",
                placeholder = "6 digit angka",
                leadingIcon = Icons.Default.Pin,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Resend OTP link
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Tidak menerima kode?",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
                Text(
                    text = if (isResendingOtp) "Mengirim ulang..." else "Kirim Ulang Kode",
                    color = if (isResendingOtp) TextMuted else PrimaryBlue,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable(enabled = !isResendingOtp) {
                        isResendingOtp = true
                        errorMessage = null
                        coroutineScope.launch {
                            val res = repository.forgotPassword(email)
                            isResendingOtp = false
                            res.onSuccess {
                                successNotice = "Kode OTP baru telah dikirim ke email."
                            }.onFailure { err ->
                                errorMessage = err.message ?: "Gagal mengirim ulang kode."
                            }
                        }
                    }
                )
            }

            if (successNotice != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFDCFCE7))
                        .padding(12.dp)
                ) {
                    Text(successNotice ?: "", color = Color(0xFF15803D), fontSize = 13.sp)
                }
            }

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
                text = "Lanjutkan",
                isLoading = isLoading,
                onClick = {
                    if (code.trim().length < 4) {
                        errorMessage = "Masukkan kode verifikasi OTP yang valid (minimal 4-6 digit)."
                        return@NayakaButton
                    }
                    errorMessage = null
                    currentStep = 2
                }
            )
        } else {
            // ── STEP 2: BUAT KATA SANDI BARU ─────────────────────────────────────────
            Text(
                text = "Kata Sandi Baru",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                text = "Buat kata sandi baru untuk akun $email. Pastikan minimal 8 karakter.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(28.dp))

            NayakaTextField(
                value = newPassword,
                onValueChange = { newPassword = it.filterNot(Char::isWhitespace); errorMessage = null },
                label = "Kata Sandi Baru",
                placeholder = "Minimal 8 karakter",
                leadingIcon = Icons.Default.Lock,
                visualTransformation = if (newPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { newPasswordVisible = !newPasswordVisible }) {
                        Icon(
                            imageVector = if (newPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = "Toggle password",
                            tint = TextMuted
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )

            Spacer(modifier = Modifier.height(16.dp))

            NayakaTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it.filterNot(Char::isWhitespace); errorMessage = null },
                label = "Konfirmasi Kata Sandi Baru",
                placeholder = "Ulangi kata sandi baru",
                leadingIcon = Icons.Default.Lock,
                visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                        Icon(
                            imageVector = if (confirmPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = "Toggle confirm password",
                            tint = TextMuted
                        )
                    }
                },
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
                    if (newPassword.isBlank()) {
                        errorMessage = "Silakan masukkan kata sandi baru."
                        return@NayakaButton
                    }
                    if (newPassword.length < 8) {
                        errorMessage = "Kata sandi minimal 8 karakter."
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
                        }.onFailure { err ->
                            errorMessage = err.message ?: "Gagal mereset kata sandi."
                            // Jika kode OTP kedaluwarsa atau salah, kembalikan ke step 1
                            val msg = err.message.orEmpty().lowercase()
                            if (msg.contains("kode") || msg.contains("otp") || msg.contains("kedaluwarsa") || msg.contains("expired")) {
                                currentStep = 1
                            }
                        }
                    }
                }
            )
        }
    }
}
