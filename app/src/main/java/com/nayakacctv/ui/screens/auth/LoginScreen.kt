package com.nayakacctv.ui.screens.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.nayakacctv.data.repository.NayakaRepository
import com.nayakacctv.ui.components.GoogleSignInButton
import com.nayakacctv.ui.components.NayakaButton
import com.nayakacctv.ui.components.NayakaTextField
import com.nayakacctv.ui.components.OrDivider
import com.nayakacctv.ui.theme.*
import com.nayakacctv.ui.utils.BiometricHelper
import com.nayakacctv.ui.utils.GoogleAuthHelper
import com.nayakacctv.ui.utils.findFragmentActivity
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    repository: NayakaRepository,
    onLoginSuccess: () -> Unit,
    onNavigateToRegister: () -> Unit,
    onNavigateToForgotPassword: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val biometricCustomer = remember { repository.getBiometricCustomer() }
    var email by remember { mutableStateOf(biometricCustomer?.email ?: "") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var isGoogleLoading by remember { mutableStateOf(false) }
    var isBiometricLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val webClientId = remember {
        try {
            context.getString(com.nayakacctv.R.string.google_web_client_id)
        } catch (_: Exception) {
            ""
        }
    }

    val canUseBiometric = remember {
        repository.canUseBiometric(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundSlate)
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(40.dp))

        // App Logo
        Image(
            painter = painterResource(id = com.nayakacctv.R.drawable.ic_jaga_rumah_logo),
            contentDescription = "Logo JAGA RUMAH",
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(88.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "JAGA RUMAH",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        Text(
            text = "Masuk untuk memantau keamanan hunian Anda",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(36.dp))

        // Form Fields
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
            onValueChange = { password = it.filterNot(Char::isWhitespace); errorMessage = null },
            label = "Kata Sandi",
            placeholder = "Masukkan kata sandi",
            leadingIcon = Icons.Default.Lock,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = "Toggle password",
                        tint = TextMuted
                    )
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )

        // Forgot Password link
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            Text(
                text = "Lupa Kata Sandi?",
                color = PrimaryBlue,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.clickable { onNavigateToForgotPassword() }
            )
        }

        // Error Banner
        if (errorMessage != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFFEE2E2))
                    .padding(12.dp)
            ) {
                Text(
                    text = errorMessage ?: "",
                    color = StatusDanger,
                    fontSize = 13.sp
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Login Button
        NayakaButton(
            text = "Masuk",
            isLoading = isLoading,
            onClick = {
                if (email.isBlank() || password.isBlank()) {
                    errorMessage = "Silakan lengkapi email dan kata sandi."
                    return@NayakaButton
                }
                isLoading = true
                coroutineScope.launch {
                    val result = repository.login(email.trim(), password)
                    isLoading = false
                    result.onSuccess {
                        onLoginSuccess()
                    }.onFailure {
                        errorMessage = it.message ?: "Gagal masuk."
                    }
                }
            }
        )

        // Divider
        OrDivider()

        // Google Sign-In Button
        GoogleSignInButton(
            text = "Masuk dengan Google",
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
                            onLoginSuccess()
                        }.onFailure {
                            errorMessage = it.message ?: "Gagal masuk dengan Google."
                        }
                    }.onFailure {
                        isGoogleLoading = false
                        errorMessage = it.message ?: "Gagal otentikasi Google."
                    }
                }
            }
        )

        // Biometric Option
        if (canUseBiometric) {
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(
                onClick = {
                    val activity = context.findFragmentActivity()
                    if (activity != null) {
                        val name = biometricCustomer?.fullName ?: "Pengguna"
                        BiometricHelper.showBiometricPrompt(
                            activity = activity,
                            title = "Masuk dengan Biometrik",
                            subtitle = "Pindai sidik jari Anda untuk melanjutkan sebagai $name",
                            negativeButtonText = "Gunakan Kata Sandi",
                            onSuccess = {
                                isBiometricLoading = true
                                errorMessage = null
                                coroutineScope.launch {
                                    val result = repository.loginWithBiometric()
                                    isBiometricLoading = false
                                    result.onSuccess {
                                        onLoginSuccess()
                                    }.onFailure { err ->
                                        errorMessage = err.message ?: "Gagal masuk via biometrik."
                                    }
                                }
                            },
                            onError = { err ->
                                errorMessage = err
                            }
                        )
                    } else {
                        errorMessage = "Modul autentikasi biometrik tidak dapat diakses."
                    }
                },
                enabled = !isLoading && !isGoogleLoading && !isBiometricLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryBlue)
            ) {
                if (isBiometricLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = PrimaryBlue,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Memverifikasi Sidik Jari...", fontWeight = FontWeight.SemiBold)
                } else {
                    Icon(imageVector = Icons.Default.Fingerprint, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    val label = if (!biometricCustomer?.fullName.isNullOrBlank()) {
                        "Masuk Cepat (${biometricCustomer?.fullName})"
                    } else {
                        "Masuk dengan Biometrik"
                    }
                    Text(label, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Register Link
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Belum punya akun? ", color = TextSecondary, fontSize = 14.sp)
            Text(
                text = "Daftar Sekarang",
                color = PrimaryBlue,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier.clickable { onNavigateToRegister() }
            )
        }

        Spacer(modifier = Modifier.height(30.dp))
    }
}
