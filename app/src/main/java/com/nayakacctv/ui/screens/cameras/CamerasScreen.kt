package com.nayakacctv.ui.screens.cameras

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nayakacctv.data.model.Camera
import com.nayakacctv.data.repository.NayakaRepository
import com.nayakacctv.ui.components.CameraCard
import com.nayakacctv.ui.components.NayakaTopAppBar
import com.nayakacctv.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun CamerasScreen(
    repository: NayakaRepository,
    onNavigateToCameraDetail: (String) -> Unit,
    onNavigateToLiveView: (String) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var cameras by remember { mutableStateOf<List<Camera>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("Semua") }
    var isLoading by remember { mutableStateOf(true) }

    val filters = listOf("Semua", "Online", "Merekam", "Offline")

    val loadCameras = {
        coroutineScope.launch {
            isLoading = true
            val res = repository.getCameras()
            cameras = res.getOrDefault(emptyList())
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        loadCameras()
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

    Scaffold(
        topBar = {
            NayakaTopAppBar(
                title = "Daftar Kamera",
                actions = {
                    IconButton(onClick = { loadCameras() }) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "Muat Ulang", tint = PrimaryBlue)
                    }
                }
            )
        },
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
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                        Text(
                            text = if (searchQuery.isNotBlank() || selectedFilter != "Semua") {
                                "Tidak ada kamera yang cocok dengan kriteria pencarian."
                            } else {
                                "Belum ada kamera yang terhubung ke akun Anda."
                            },
                            color = TextMuted,
                            fontSize = 14.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(onClick = { loadCameras() }) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Muat Ulang")
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
                            onClick = { onNavigateToLiveView(camera.id) }
                        )
                    }
                }
            }
        }
    }
}
