package com.nayakacctv.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nayakacctv.data.repository.NayakaRepository
import com.nayakacctv.ui.components.NayakaTopAppBar
import com.nayakacctv.ui.theme.*
import com.nayakacctv.ui.utils.BiometricHelper
import com.nayakacctv.ui.utils.BiometricStatus
import com.nayakacctv.ui.utils.findFragmentActivity
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(
    repository: NayakaRepository,
    onNavigateToSubscription: () -> Unit,
    onNavigateToResetPassword: (String) -> Unit = {},
    onNavigateToHelp: () -> Unit,
    onNavigateToTerms: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val customer = repository.getCurrentCustomer()
    var isBiometricEnabled by remember { mutableStateOf(repository.isBiometricEnabled()) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showResetPasswordDialog by remember { mutableStateOf(false) }
    var isSendingOtp by remember { mutableStateOf(false) }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Konfirmasi Keluar", fontWeight = FontWeight.Bold) },
            text = { Text("Apakah Anda yakin ingin keluar dari akun ini?") },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutDialog = false
                        coroutineScope.launch {
                            repository.logout()
                            onLogout()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusDanger)
                ) {
                    Text("Keluar", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }

    if (showResetPasswordDialog) {
        val userEmail = customer?.email?.trim().orEmpty()
        AlertDialog(
            onDismissRequest = { if (!isSendingOtp) showResetPasswordDialog = false },
            title = { Text("Reset Kata Sandi", fontWeight = FontWeight.Bold) },
            text = {
                Text("Kode verifikasi OTP akan dikirimkan ke email:\n$userEmail\n\nLanjutkan untuk menerima kode?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (userEmail.isBlank()) {
                            showResetPasswordDialog = false
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Email pengguna tidak ditemukan.")
                            }
                            return@Button
                        }
                        isSendingOtp = true
                        coroutineScope.launch {
                            val res = repository.forgotPassword(userEmail)
                            isSendingOtp = false
                            showResetPasswordDialog = false
                            res.onSuccess {
                                onNavigateToResetPassword(userEmail)
                            }.onFailure { err ->
                                snackbarHostState.showSnackbar(err.message ?: "Gagal mengirim kode OTP ke email.")
                            }
                        }
                    },
                    enabled = !isSendingOtp,
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) {
                    if (isSendingOtp) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(if (isSendingOtp) "Mengirim OTP..." else "Kirim Kode OTP", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showResetPasswordDialog = false },
                    enabled = !isSendingOtp
                ) {
                    Text("Batal")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            NayakaTopAppBar(title = "Profil Pengguna")
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = BackgroundSlate
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Profile Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(PrimaryBlue),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = (customer?.fullName?.take(1) ?: "N").uppercase(),
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column {
                        Text(
                            text = customer?.fullName ?: "Pengguna Nayaka",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = customer?.email ?: "user@nayaka.com",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }
            }

            // Security Settings
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Keamanan Akun",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Fingerprint, contentDescription = null, tint = PrimaryBlue)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Masuk via Biometrik", color = TextPrimary, fontWeight = FontWeight.Medium)
                                Text(
                                    "Gunakan sidik jari untuk masuk cepat",
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            }
                        }
                        Switch(
                            checked = isBiometricEnabled,
                            onCheckedChange = { enable ->
                                if (enable) {
                                    when (BiometricHelper.checkBiometricStatus(context)) {
                                        BiometricStatus.NOT_SUPPORTED -> {
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar("Perangkat tidak mendukung autentikasi biometrik.")
                                            }
                                        }
                                        BiometricStatus.NOT_ENROLLED -> {
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar("Sidik jari belum didaftarkan di Pengaturan HP Anda.")
                                            }
                                        }
                                        BiometricStatus.UNAVAILABLE -> {
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar("Sensor biometrik saat ini tidak tersedia.")
                                            }
                                        }
                                        BiometricStatus.AVAILABLE -> {
                                            val activity = context.findFragmentActivity()
                                            if (activity != null) {
                                                BiometricHelper.showBiometricPrompt(
                                                    activity = activity,
                                                    title = "Konfirmasi Biometrik",
                                                    subtitle = "Pindai sidik jari Anda untuk mengaktifkan fitur ini",
                                                    negativeButtonText = "Batal",
                                                    onSuccess = {
                                                        val success = repository.enableBiometric()
                                                        if (success) {
                                                            isBiometricEnabled = true
                                                            coroutineScope.launch {
                                                                snackbarHostState.showSnackbar("Masuk via Biometrik berhasil diaktifkan.")
                                                            }
                                                        } else {
                                                            isBiometricEnabled = false
                                                            coroutineScope.launch {
                                                                snackbarHostState.showSnackbar("Gagal mengaktifkan biometrik. Sesi akun tidak ditemukan.")
                                                            }
                                                        }
                                                    },
                                                    onError = { err ->
                                                        isBiometricEnabled = false
                                                        coroutineScope.launch {
                                                            snackbarHostState.showSnackbar(err)
                                                        }
                                                    },
                                                    onCancel = {
                                                        isBiometricEnabled = false
                                                    }
                                                )
                                            } else {
                                                coroutineScope.launch {
                                                    snackbarHostState.showSnackbar("Gagal membuka modul autentikasi biometrik.")
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    repository.disableBiometric()
                                    isBiometricEnabled = false
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("Masuk via Biometrik dinonaktifkan.")
                                    }
                                }
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = PrimaryBlue)
                        )
                    }

                    Divider(color = BorderSubtle, modifier = Modifier.padding(vertical = 12.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showResetPasswordDialog = true }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.LockReset, contentDescription = null, tint = PrimaryBlue)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Reset / Ganti Kata Sandi", color = TextPrimary, fontWeight = FontWeight.Medium)
                                Text(
                                    "Kirim kode OTP ke email untuk membuat sandi baru",
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = TextMuted
                        )
                    }
                }
            }

            // General Menu
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
            ) {
                Column(modifier = Modifier.padding(vertical = 6.dp)) {
                    ProfileMenuItem(
                        icon = Icons.Default.CloudQueue,
                        title = "Paket Langganan Cloud",
                        onClick = onNavigateToSubscription
                    )
                    Divider(color = BorderSubtle, modifier = Modifier.padding(horizontal = 16.dp))
                    ProfileMenuItem(
                        icon = Icons.Default.HelpOutline,
                        title = "Pusat Bantuan & Panduan",
                        onClick = onNavigateToHelp
                    )
                    Divider(color = BorderSubtle, modifier = Modifier.padding(horizontal = 16.dp))
                    ProfileMenuItem(
                        icon = Icons.Default.Description,
                        title = "Syarat & Ketentuan Layanan",
                        onClick = onNavigateToTerms
                    )
                }
            }

            // Logout Button
            Button(
                onClick = { showLogoutDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFEE2E2),
                    contentColor = StatusDanger
                )
            ) {
                Icon(Icons.Default.Logout, contentDescription = null, tint = StatusDanger)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Keluar dari Akun", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ProfileMenuItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = icon, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = title, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
        }
        Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null, tint = TextMuted)
    }
}
