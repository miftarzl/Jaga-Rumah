package com.nayakacctv.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nayakacctv.data.repository.NayakaRepository
import com.nayakacctv.ui.components.GoogleSignInButton
import com.nayakacctv.ui.components.NayakaButton
import com.nayakacctv.ui.components.NayakaTextField
import com.nayakacctv.ui.components.OrDivider
import com.nayakacctv.ui.theme.*
import com.nayakacctv.ui.utils.GoogleAuthHelper
import kotlinx.coroutines.launch

@Composable
fun RegisterScreen(
    repository: NayakaRepository,
    onRegisterSuccess: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var agreeTerms by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var isGoogleLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val webClientId = remember {
        try {
            context.getString(com.nayakacctv.R.string.google_web_client_id)
        } catch (_: Exception) {
            ""
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundSlate)
            .padding(24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        IconButton(
            onClick = onNavigateBack,
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali", tint = TextPrimary)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Daftar Akun Baru",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        Text(
            text = "Lengkapi data untuk memulai perlindungan hunian Anda",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(28.dp))

        NayakaTextField(
            value = fullName,
            onValueChange = { fullName = it; errorMessage = null },
            label = "Nama Lengkap",
            placeholder = "Contoh: Budi Santoso",
            leadingIcon = Icons.Default.Person
        )

        Spacer(modifier = Modifier.height(16.dp))

        NayakaTextField(
            value = email,
            onValueChange = { email = it; errorMessage = null },
            label = "Email",
            placeholder = "nama@email.com",
            leadingIcon = Icons.Default.Email,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
        )

        Spacer(modifier = Modifier.height(16.dp))

        NayakaTextField(
            value = password,
            onValueChange = { password = it; errorMessage = null },
            label = "Kata Sandi",
            placeholder = "Minimal 8 karakter",
            leadingIcon = Icons.Default.Lock,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )

        Spacer(modifier = Modifier.height(16.dp))

        NayakaTextField(
            value = confirmPassword,
            onValueChange = { confirmPassword = it; errorMessage = null },
            label = "Konfirmasi Kata Sandi",
            placeholder = "Ulangi kata sandi",
            leadingIcon = Icons.Default.Lock,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable { agreeTerms = !agreeTerms }
        ) {
            Checkbox(
                checked = agreeTerms,
                onCheckedChange = { agreeTerms = it },
                colors = CheckboxDefaults.colors(checkedColor = PrimaryBlue)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Saya menyetujui Syarat & Ketentuan Layanan",
                fontSize = 13.sp,
                color = TextSecondary
            )
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
            text = "Daftar Akun",
            isLoading = isLoading,
            onClick = {
                if (fullName.isBlank() || email.isBlank() || password.isBlank()) {
                    errorMessage = "Silakan lengkapi semua kolom pendaftaran."
                    return@NayakaButton
                }
                if (password != confirmPassword) {
                    errorMessage = "Konfirmasi kata sandi tidak cocok."
                    return@NayakaButton
                }
                if (!agreeTerms) {
                    errorMessage = "Harap setujui Syarat & Ketentuan Layanan."
                    return@NayakaButton
                }

                isLoading = true
                coroutineScope.launch {
                    val result = repository.register(fullName.trim(), email.trim(), password)
                    isLoading = false
                    result.onSuccess {
                        onRegisterSuccess()
                    }.onFailure {
                        errorMessage = it.message ?: "Pendaftaran gagal."
                    }
                }
            }
        )

        // Divider
        OrDivider()

        // Google Sign-In Button
        GoogleSignInButton(
            text = "Daftar dengan Google",
            isLoading = isGoogleLoading,
            onClick = {
                isGoogleLoading = true
                errorMessage = null
                coroutineScope.launch {
                    val tokenRes = GoogleAuthHelper.getGoogleIdToken(context, webClientId)
                    tokenRes.onSuccess { idToken ->
                        val loginRes = repository.loginGoogle(idToken)
                        isGoogleLoading = false
                        loginRes.onSuccess {
                            onRegisterSuccess()
                        }.onFailure {
                            errorMessage = it.message ?: "Gagal mendaftar dengan Google."
                        }
                    }.onFailure {
                        isGoogleLoading = false
                        errorMessage = it.message ?: "Gagal otentikasi Google."
                    }
                }
            }
        )

        Spacer(modifier = Modifier.height(30.dp))
    }
}
