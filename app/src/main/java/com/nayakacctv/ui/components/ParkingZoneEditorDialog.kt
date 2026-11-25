package com.nayakacctv.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nayakacctv.data.model.ParkingZone
import com.nayakacctv.ui.theme.PrimaryBlue
import com.nayakacctv.ui.theme.TextSecondary
import kotlin.math.max
import kotlin.math.min

@Composable
fun ParkingZoneEditorDialog(
    cameraName: String,
    snapshotUrl: String,
    initialZone: ParkingZone?,
    onDismiss: () -> Unit,
    onRefreshSnapshot: () -> Unit,
    onSave: (ParkingZone?) -> Unit
) {
    var zone by remember(cameraName, initialZone) { mutableStateOf(initialZone) }
    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var snapshotError by remember(snapshotUrl) { mutableStateOf<String?>(null) }
    var zoneError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Zona Parkir • $cameraName", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Geser jari pada foto untuk menandai area parkir yang dipantau AI.",
                    color = TextSecondary
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black)
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { position ->
                                    val x = (position.x / size.width).coerceIn(0f, 1f)
                                    val y = (position.y / size.height).coerceIn(0f, 1f)
                                    dragStart = Offset(x, y)
                                    zone = ParkingZone(x, y, x, y)
                                    zoneError = null
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    val start = dragStart ?: return@detectDragGestures
                                    val x = (change.position.x / size.width).coerceIn(0f, 1f)
                                    val y = (change.position.y / size.height).coerceIn(0f, 1f)
                                    zone = ParkingZone(
                                        x1 = min(start.x, x),
                                        y1 = min(start.y, y),
                                        x2 = max(start.x, x),
                                        y2 = max(start.y, y)
                                    )
                                },
                                onDragEnd = {
                                    dragStart = null
                                    val currentZone = zone
                                    if (currentZone != null &&
                                        (currentZone.x2 - currentZone.x1 < 0.05f ||
                                            currentZone.y2 - currentZone.y1 < 0.05f)
                                    ) {
                                        zone = initialZone
                                        zoneError = "Zona harus memiliki lebar dan tinggi minimal 5%."
                                    }
                                },
                                onDragCancel = {
                                    dragStart = null
                                    zone = initialZone
                                }
                            )
                        }
                ) {
                    AsyncImage(
                        model = snapshotUrl,
                        contentDescription = "Foto kamera $cameraName untuk zona parkir",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.FillBounds,
                        onError = {
                            snapshotError = it.result.throwable.localizedMessage
                                ?: "Foto kamera tidak dapat dimuat."
                        }
                    )
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val currentZone = zone ?: return@Canvas
                        val left = currentZone.x1 * size.width
                        val top = currentZone.y1 * size.height
                        val width = (currentZone.x2 - currentZone.x1) * size.width
                        val height = (currentZone.y2 - currentZone.y1) * size.height
                        drawRect(
                            color = Color(0x3322D3EE),
                            topLeft = Offset(left, top),
                            size = Size(width, height)
                        )
                        drawRect(
                            color = Color(0xFF22D3EE),
                            topLeft = Offset(left, top),
                            size = Size(width, height),
                            style = Stroke(width = 3.dp.toPx())
                        )
                    }
                    if (snapshotError != null) {
                        Text(
                            text = snapshotError.orEmpty(),
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(12.dp),
                            color = Color.White
                        )
                    }
                }
                zoneError?.let { Text(it, color = Color(0xFFB91C1C)) }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { zone = null }) {
                        Text("Reset seluruh layar")
                    }
                    OutlinedButton(onClick = onRefreshSnapshot) {
                        Text("Segarkan foto")
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(zone) },
                enabled = zone == null ||
                    ((zone?.x2 ?: 0f) - (zone?.x1 ?: 0f) >= 0.05f &&
                        (zone?.y2 ?: 0f) - (zone?.y1 ?: 0f) >= 0.05f)
            ) {
                Text("Terapkan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}
