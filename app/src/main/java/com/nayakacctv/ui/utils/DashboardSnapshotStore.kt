package com.nayakacctv.ui.utils

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

object DashboardSnapshotStore {
    data class Snapshot(val file: File, val capturedAtMillis: Long)

    private val _snapshots = MutableStateFlow<Map<String, Snapshot>>(emptyMap())
    val snapshots = _snapshots.asStateFlow()

    fun cachedSnapshot(context: Context, cameraId: String): File? =
        _snapshots.value[cameraId]?.file?.takeIf(File::isFile)
            ?: snapshotFile(context, cameraId).takeIf(File::isFile)

    suspend fun save(context: Context, cameraId: String, bitmap: Bitmap): File =
        withContext(Dispatchers.IO) {
            val applicationContext = context.applicationContext
            val safeCameraId = cameraId.filter(Char::isLetterOrDigit).ifBlank { "camera" }
            val capturedAtMillis = System.currentTimeMillis()
            val destination = snapshotFile(applicationContext, safeCameraId, capturedAtMillis)
            val temporaryFile = File(destination.parentFile, "${destination.name}.tmp")

            temporaryFile.outputStream().buffered().use { output ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 88, output)) {
                    throw IOException("Gagal mengubah frame stream menjadi JPEG.")
                }
            }
            if (!temporaryFile.renameTo(destination)) {
                temporaryFile.copyTo(destination, overwrite = true)
                temporaryFile.delete()
            }

            applicationContext.cacheDir
                .listFiles { file ->
                    file.name.startsWith("dashboard_snapshot_${safeCameraId}_") &&
                        file.extension == "jpg" &&
                        file != destination
                }
                ?.forEach { it.delete() }
            _snapshots.update {
                it + (cameraId to Snapshot(destination, capturedAtMillis))
            }
            destination
        }

    private fun snapshotFile(context: Context, cameraId: String): File =
        snapshotFile(context, cameraId, timestamp = null)

    private fun snapshotFile(context: Context, cameraId: String, timestamp: Long?): File {
        val safeCameraId = cameraId.filter(Char::isLetterOrDigit).ifBlank { "camera" }
        val prefix = "dashboard_snapshot_$safeCameraId"
        if (timestamp != null) {
            return File(context.applicationContext.cacheDir, "${prefix}_$timestamp.jpg")
        }

        return context.applicationContext.cacheDir
            .listFiles { file -> file.name.startsWith("${prefix}_") && file.extension == "jpg" }
            ?.maxByOrNull { it.lastModified() }
            ?: File(context.applicationContext.cacheDir, "$prefix.jpg")
    }
}
