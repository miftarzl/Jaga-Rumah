package com.nayakacctv.ui.screens.alerts

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nayakacctv.data.model.AlertItem
import com.nayakacctv.data.repository.NayakaRepository
import com.nayakacctv.ui.components.AlertItemCard
import com.nayakacctv.ui.components.NayakaTopAppBar
import com.nayakacctv.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun AlertsScreen(
    repository: NayakaRepository,
    onNavigateToDetail: (String) -> Unit = {}
) {
    val coroutineScope = rememberCoroutineScope()
    var alerts by remember { mutableStateOf<List<AlertItem>>(emptyList()) }
    var selectedFilter by remember { mutableStateOf("Semua") }
    var isLoading by remember { mutableStateOf(true) }

    val filters = listOf("Semua", "Kritis", "Belum Dibaca")

    val loadAlerts = {
        coroutineScope.launch {
            isLoading = true
            val res = repository.getAlerts()
            alerts = res.getOrDefault(emptyList())
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        loadAlerts()
    }

    val filteredAlerts = remember(alerts, selectedFilter) {
        alerts.filter { item ->
            when (selectedFilter) {
                "Kritis" -> item.severity.equals("critical", ignoreCase = true)
                "Belum Dibaca" -> !item.read
                else -> true
            }
        }
    }

    Scaffold(
        topBar = {
            NayakaTopAppBar(
                title = "Notifikasi Ancaman",
                actions = {
                    IconButton(onClick = {
                        coroutineScope.launch {
                            repository.markAllAlertsRead()
                            alerts = alerts.map { it.copy(read = true) }
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.DoneAll,
                            contentDescription = "Tandai Semua Dibaca",
                            tint = PrimaryBlue
                        )
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
            // Filter Chips
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
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

            // Alerts List
            if (filteredAlerts.isEmpty() && !isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Tidak ada notifikasi peringatan saat ini.",
                        color = TextMuted,
                        fontSize = 14.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredAlerts) { alert ->
                        AlertItemCard(
                            alert = alert,
                            onClick = {
                                if (!alert.read) {
                                    coroutineScope.launch {
                                        repository.markAlertRead(alert.id)
                                        alerts = alerts.map {
                                            if (it.id == alert.id) it.copy(read = true) else it
                                        }
                                    }
                                }
                                onNavigateToDetail(alert.id)
                            }
                        )
                    }
                }
            }
        }
    }
}
