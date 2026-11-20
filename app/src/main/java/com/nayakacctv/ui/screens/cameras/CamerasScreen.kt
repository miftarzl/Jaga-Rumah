package com.nayakacctv.ui.screens.cameras

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nayakacctv.data.model.Camera
import com.nayakacctv.data.repository.NayakaRepository
import com.nayakacctv.ui.components.CameraCard
import com.nayakacctv.ui.components.NayakaTopAppBar
import com.nayakacctv.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun CamerasScreen(
    repository: NayakaRepository,
    onNavigateToCameraDetail: (String) -> Unit,
    onNavigateToLiveView: (String) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboardManager = LocalClipboardManager.current
    val focusRequester = remember { FocusRequester() }

    var cameras by remember { mutableStateOf<List<Camera>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("Semua") }
    var isLoading by remember { mutableStateOf(true) }

    // State untuk Dialog Klaim Kamera Baru
    var showClaimDialog by remember { mutableStateOf(false) }
    var claimCode by remember { mutableStateOf("") }
    var isClaiming by remember { mutableStateOf(false) }
    var claimError by remember { mutableStateOf<String?>(null) }
    var claimSuccess by remember { mutableStateOf<String?>(null) }

    val filters = listOf("Semua", "Online", "Merekam", "Offline")

    val loadCameras: (Boolean) -> Unit = { forceRefresh ->
        coroutineScope.launch {
            isLoading = true
            val res = repository.getCameras(forceRefresh = forceRefresh)
            cameras = res.getOrDefault(emptyList())
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        loadCameras(false)
    }

    // Auto-focus ke input kode klaim saat dialog terbuka
    LaunchedEffect(showClaimDialog) {
        if (showClaimDialog) {
            delay(200)
            try {
                focusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    val filteredCameras = remember(cameras, searchQuery, selectedFilter) {
        cameras.filter { cam ->
            val matchesSearch = cam.name.contains(searchQuery, ignoreCase = true) ||
                    cam.location.contains(searchQuery, ignoreCase = true)

            val matchesFilter = when (selectedFilter) {
                "Online" -> cam.status.equals("online", ignoreCase = true)
                "Merekam" -> cam.status.equals("recording", ignoreCase = true) || cam.isRecording
                "Offline" -> cam.status.equals("offline", ignoreCase = true)
                else -> true
            }
            matchesSearch && matchesFilter
        }
    }

    val submitClaim = {
        if (claimCode.isBlank()) {
            claimError = "Silakan ketik atau tempel kode klaim kamera terlebih dahulu."
            try { focusRequester.requestFocus() } catch (_: Exception) {}
        } else {
            isClaiming = true
            claimError = null
            claimSuccess = null
            coroutineScope.launch {
                val result = repository.claimDevice(claimCode.trim())
                isClaiming = false
                result.onSuccess { claimed ->
                    val cameraName = claimed.name
                    claimSuccess = "Kamera \"$cameraName\" berhasil diklaim!"
                    loadCameras(true)
                    showClaimDialog = false
                    snackbarHostState.showSnackbar("Kamera \"$cameraName\" berhasil ditambahkan.")
                }.onFailure { err ->
                    claimError = err.message ?: "Gagal mengklaim kamera. Periksa kembali kodenya."
                }
            }
        }
    }

    // Dialog Klaim Kamera Baru
    if (showClaimDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isClaiming) {
                    showClaimDialog = false
                }
            },
            icon = {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(PrimaryBlue.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Videocam,
                        contentDescription = null,
                        tint = PrimaryBlue,
                        modifier = Modifier.size(28.dp)
                    )
                }
            },
            title = {
                Text(
                    text = "Klaim Kamera Baru",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Masukkan kode klaim yang tertera pada kemasan, stiker perangkat, atau kartu garansi kamera CCTV Anda.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        lineHeight = 20.sp
                    )

                    OutlinedTextField(
                        value = claimCode,
                        onValueChange = {
                            claimCode = it.uppercase()
                            claimError = null
                        },
                        label = { Text("Kode Klaim") },
                        placeholder = { Text("Contoh: NYK-7842-9901") },
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.VpnKey, contentDescription = null, tint = PrimaryBlue)
                        },
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (claimCode.isNotEmpty() && !isClaiming) {
                                    IconButton(onClick = { claimCode = "" }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Hapus", tint = TextMuted)
                                    }
                                } else if (!isClaiming) {
                                    TextButton(
                                        onClick = {
                                            val text = clipboardManager.getText()?.text
                                            if (!text.isNullOrBlank()) {
                                                claimCode = text.trim().uppercase()
                                                claimError = null
                                            }
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp)
                                    ) {
                                        Text("Tempel", fontSize = 12.sp, color = PrimaryBlue, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        },
                        singleLine = true,
                        enabled = !isClaiming,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { submitClaim() }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryBlue,
                            unfocusedBorderColor = BorderSubtle,
                            focusedContainerColor = SurfaceWhite,
                            unfocusedContainerColor = SurfaceWhite
                        )
                    )

                    if (claimError != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFFEE2E2))
                                .padding(10.dp)
                        ) {
                            Text(
                                text = claimError ?: "",
                                color = StatusDanger,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }

                    if (claimSuccess != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFD1FAE5))
                                .padding(10.dp)
                        ) {
                            Text(
                                text = claimSuccess ?: "",
                                color = Color(0xFF065F46),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                lineHeight = 16.sp
                            )
                        }
                    }

                    Text(
                        text = "Catatan: Pastikan kamera telah terhubung ke jaringan lokal atau router OpenVPN agar streaming langsung aktif.",
                        fontSize = 11.sp,
                        color = TextMuted,
                        lineHeight = 15.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { submitClaim() },
                    enabled = !isClaiming,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryBlue,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    if (isClaiming) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Memverifikasi...", color = Color.White)
                    } else {
                        Text("Klaim Sekarang", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            },
            dismissButton = {
                if (!isClaiming) {
                    TextButton(onClick = { showClaimDialog = false }) {
                        Text("Batal", color = TextSecondary)
                    }
                }
            }
        )
    }

    Scaffold(
        topBar = {
            NayakaTopAppBar(
                title = "Daftar Kamera",
                actions = {
                    IconButton(
                        onClick = {
                            claimCode = ""
                            claimError = null
                            claimSuccess = null
                            showClaimDialog = true
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Tambah Kamera",
                            tint = PrimaryBlue
                        )
                    }
                    IconButton(onClick = { loadCameras(true) }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Muat Ulang",
                            tint = PrimaryBlue
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    claimCode = ""
                    claimError = null
                    claimSuccess = null
                    showClaimDialog = true
                },
                containerColor = PrimaryBlue,
                contentColor = Color.White
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Tambah Kamera")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = BackgroundSlate
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Search Input
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Cari nama kamera atau lokasi...", color = TextMuted) },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = TextSecondary)
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryBlue,
                    unfocusedBorderColor = BorderSubtle,
                    focusedContainerColor = SurfaceWhite,
                    unfocusedContainerColor = SurfaceWhite
                )
            )

            // Filter Chips
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filters) { f ->
                    val isSelected = selectedFilter == f
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedFilter = f },
                        label = { Text(f, fontSize = 13.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PrimaryBlue,
                            selectedLabelColor = Color.White,
                            containerColor = SurfaceWhite,
                            labelColor = TextSecondary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderColor = if (isSelected) PrimaryBlue else BorderSubtle,
                            enabled = true,
                            selected = isSelected
                        )
                    )
                }
            }

            // Camera List
            if (filteredCameras.isEmpty() && !isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (searchQuery.isNotBlank() || selectedFilter != "Semua") {
                                "Tidak ada kamera yang cocok dengan kriteria pencarian."
                            } else {
                                "Belum ada kamera yang terhubung ke akun Anda."
                            },
                            color = TextMuted,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = {
                                    claimCode = ""
                                    claimError = null
                                    claimSuccess = null
                                    showClaimDialog = true
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = PrimaryBlue,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Klaim Kamera Baru", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(onClick = { loadCameras(true) }) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Muat Ulang")
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(filteredCameras) { camera ->
                        CameraCard(
                            camera = camera,
                            onClick = { onNavigateToLiveView(camera.id) },
                            onSettingsClick = { onNavigateToCameraDetail(camera.id) }
                        )
                    }
                }
            }
        }
    }
}
