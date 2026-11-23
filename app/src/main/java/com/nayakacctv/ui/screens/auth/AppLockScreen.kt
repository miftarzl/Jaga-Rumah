package com.nayakacctv.ui.screens.auth

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nayakacctv.data.repository.NayakaRepository
import com.nayakacctv.ui.components.GoogleSignInButton
import com.nayakacctv.ui.components.NayakaButton
import com.nayakacctv.ui.components.NayakaTextField
import com.nayakacctv.ui.components.OrDivider
import com.nayakacctv.ui.theme.*
import com.nayakacctv.ui.utils.AppLockManager
import com.nayakacctv.ui.utils.BiometricHelper
import com.nayakacctv.ui.utils.GoogleAuthHelper
import com.nayakacctv.ui.utils.findFragmentActivity
import kotlinx.coroutines.launch

@Composable
fun AppLockScreen(
    repository: NayakaRepository,
    onUnlock: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val activity = remember(context) { context.findFragmentActivity() }

    // Saat layar terkunci, tombol back meminimalkan aplikasi ke layar beranda HP
    BackHandler {
        activity?.moveTaskToBack(true)
    }

    val customer = remember {
        repository.getCurrentCustomer() ?: repository.getBiometricCustomer()
    }
    val userEmail = customer?.email ?: ""
    val userName = customer?.fullName ?: "Pengguna Nayaka"
    val isGoogleUser = remember { repository.isGoogleUser() }
    val webClientId = remember {
        try {
            context.getString(com.nayakacctv.R.string.google_web_client_id)
        } catch (e: Exception) {
            ""
        }
    }

    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var isGoogleLoading by remember { mutableStateOf(false) }
    var isBiometricVerifying by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showPasswordForm by remember { mutableStateOf(false) }

    val isBiometricAvailable = remember {
        BiometricHelper.isBiometricAvailable(context)
    }
    val shouldUseBiometric = remember {
        repository.isBiometricEnabled() && isBiometricAvailable
    }

    // Fungsi otentikasi Google untuk akun Google
    val triggerGoogleAuth = {
        if (webClientId.isBlank()) {
            errorMessage = "Google Sign-In belum dikonfigurasi di strings.xml."
        } else {
            isGoogleLoading = true
            errorMessage = null
            AppLockManager.isSuppressed = true
            coroutineScope.launch {
                try {
                    val tokenRes = GoogleAuthHelper.getGoogleIdToken(context, webClientId)
                    tokenRes.onSuccess { idToken ->
                        val loginRes = repository.loginGoogle(idToken)
                        isGoogleLoading = false
                        loginRes.onSuccess {
                            onUnlock()
                        }.onFailure {
                            errorMessage = it.message ?: "Gagal verifikasi dengan Google."
                        }
                    }.onFailure {
                        isGoogleLoading = false
                        errorMessage = it.message ?: "Gagal otentikasi Google."
                    }
                } finally {
                    AppLockManager.isSuppressed = false
                }
            }
        }
    }

    // Fungsi pemanggil dialog biometrik
    val triggerBiometricAuth = {
        if (activity != null && shouldUseBiometric) {
            errorMessage = null
            BiometricHelper.showBiometricPrompt(
                activity = activity,
                title = "Verifikasi Sidik Jari",
                subtitle = "Pindai biometrik Anda untuk membuka JAGA RUMAH",
                negativeButtonText = if (isGoogleUser) "Buka dengan Google" else "Gunakan Kata Sandi",
                onSuccess = {
                    if (repository.isLoggedIn()) {
                        // Jika sesi lokal masih ada, verifikasi biometrik langsung membuka kunci instan
                        onUnlock()
                    } else {
                        // Jika sesi perlu disegarkan (refresh token)
                        isBiometricVerifying = true
                        coroutineScope.launch {
                            val res = repository.loginWithBiometric()
                            isBiometricVerifying = false
                            res.onSuccess {
                                onUnlock()
                            }.onFailure { err ->
                                errorMessage = err.message ?: "Gagal memverifikasi biometrik."
                                if (!isGoogleUser) {
                                    showPasswordForm = true
                                }
                            }
                        }
                    }
                },
                onError = { err ->
                    errorMessage = err
                },
                onCancel = {
                    if (!isGoogleUser) {
                        showPasswordForm = true
                    }
                }
            )
        } else {
            if (!isGoogleUser) {
                showPasswordForm = true
            }
        }
    }

    // Munculkan prompt biometrik secara otomatis layaknya m-banking (dengan delay agar Activity sudah RESUMED)
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(350)
        if (shouldUseBiometric) {
            triggerBiometricAuth()
        } else {
            if (!isGoogleUser) {
                showPasswordForm = true
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundSlate)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.height(36.dp))

            Image(
                painter = painterResource(id = com.nayakacctv.R.drawable.ic_jaga_rumah_app_icon),
                contentDescription = "Logo JAGA RUMAH",
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(88.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "JAGA RUMAH",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Text(
                text = "Sesi Anda terkunci demi keamanan",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Kartu Profil Pengguna
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = SurfaceWhite,
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(PrimaryBlueLight),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = userName.take(1).uppercase(),
                            color = PrimaryBlue,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Selamat Datang Kembali,",
                            fontSize = 12.sp,
                            color = TextMuted
                        )
                        Text(
                            text = userName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        if (userEmail.isNotBlank()) {
                            Text(
                                text = userEmail,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Pesan Error jika ada
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
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // ── Opsi Masuk Berdasarkan Tipe Akun ─────────────────────────
            if (isGoogleUser) {
                // ── AKUN GOOGLE: Sidik Jari + Buka dengan Google ──────────
                if (shouldUseBiometric) {
                    Button(
                        onClick = { triggerBiometricAuth() },
                        enabled = !isLoading && !isGoogleLoading && !isBiometricVerifying,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryBlue,
                            contentColor = Color.White,
                            disabledContainerColor = PrimaryBlue.copy(alpha = 0.6f),
                            disabledContentColor = Color.White
                        )
                    ) {
                        if (isBiometricVerifying) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Memverifikasi Sidik Jari...", color = Color.White, fontWeight = FontWeight.SemiBold)
                        } else {
                            Icon(
                                imageVector = Icons.Default.Fingerprint,
                                contentDescription = "Pindai Biometrik",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Buka dengan Sidik Jari",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    OrDivider()
                    Spacer(modifier = Modifier.height(12.dp))
                }

                GoogleSignInButton(
                    text = "Buka dengan Google",
                    isLoading = isGoogleLoading,
                    enabled = !isLoading && !isBiometricVerifying,
                    onClick = { triggerGoogleAuth() }
                )
            } else {
                // ── AKUN EMAIL & PASSWORD: Sidik Jari + Form Kata Sandi ───
                if (shouldUseBiometric) {
                    Button(
                        onClick = { triggerBiometricAuth() },
                        enabled = !isLoading && !isBiometricVerifying,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryBlue,
                            contentColor = Color.White,
                            disabledContainerColor = PrimaryBlue.copy(alpha = 0.6f),
                            disabledContentColor = Color.White
                        )
                    ) {
                        if (isBiometricVerifying) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Memverifikasi Sidik Jari...", color = Color.White, fontWeight = FontWeight.SemiBold)
                        } else {
                            Icon(
                                imageVector = Icons.Default.Fingerprint,
                                contentDescription = "Pindai Biometrik",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Buka dengan Sidik Jari",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Tombol Tampilkan Form Password (jika disembunyikan)
                if (shouldUseBiometric && !showPasswordForm) {
                    TextButton(
                        onClick = { showPasswordForm = true }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = PrimaryBlue
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Masuk dengan Kata Sandi",
                            color = PrimaryBlue,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Form Input Password
                AnimatedVisibility(visible = showPasswordForm || !shouldUseBiometric) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        NayakaTextField(
                            value = password,
                            onValueChange = {
                                password = it
                                errorMessage = null
                            },
                            label = "Kata Sandi",
                            placeholder = "Masukkan kata sandi akun",
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

                        Spacer(modifier = Modifier.height(16.dp))

                        NayakaButton(
                            text = "Buka Kunci",
                            isLoading = isLoading,
                            onClick = {
                                if (password.isBlank()) {
                                    errorMessage = "Masukkan kata sandi Anda."
                                    return@NayakaButton
                                }
                                if (userEmail.isBlank()) {
                                    errorMessage = "Data akun tidak lengkap. Silakan ganti akun."
                                    return@NayakaButton
                                }
                                isLoading = true
                                coroutineScope.launch {
                                    val result = repository.login(userEmail, password)
                                    isLoading = false
                                    result.onSuccess {
                                        onUnlock()
                                    }.onFailure { err ->
                                        errorMessage = err.message ?: "Kata sandi salah. Silakan coba lagi."
                                    }
                                }
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Tombol Ganti Akun / Logout
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onLogout() }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Logout,
                    contentDescription = null,
                    tint = StatusDanger,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Ganti Akun atau Keluar",
                    color = StatusDanger,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp
                )
            }

            Spacer(modifier = Modifier.height(36.dp))
        }
    }
}
