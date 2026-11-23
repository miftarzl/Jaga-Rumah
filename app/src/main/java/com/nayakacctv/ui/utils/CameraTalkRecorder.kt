package com.nayakacctv.ui.utils

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

class CameraTalkRecorder {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    fun start(context: Context): Result<Unit> {
        if (recorder != null) {
            return Result.failure(IllegalStateException("Rekaman bicara sedang berlangsung."))
        }

        val file = File(context.cacheDir, "camera-talk-${System.currentTimeMillis()}.m4a")
        val mediaRecorder = try {
            createMediaRecorder(context)
        } catch (e: Exception) {
            file.delete()
            return Result.failure(e)
        }
        return try {
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            mediaRecorder.setAudioSamplingRate(16_000)
            mediaRecorder.setAudioEncodingBitRate(64_000)
            mediaRecorder.setOutputFile(file.absolutePath)
            mediaRecorder.prepare()
            mediaRecorder.start()
            recorder = mediaRecorder
            outputFile = file
            Result.success(Unit)
        } catch (e: Exception) {
            mediaRecorder.release()
            file.delete()
            Result.failure(e)
        }
    }

    fun stop(): Result<File> {
        val activeRecorder = recorder
            ?: return Result.failure(IllegalStateException("Rekaman bicara tidak sedang berjalan."))
        val file = outputFile
        recorder = null
        outputFile = null

        return try {
            activeRecorder.stop()
            val recordedFile = file
                ?: return Result.failure(IllegalStateException("File rekaman tidak ditemukan."))
            if (!recordedFile.isFile || recordedFile.length() <= 0L) {
                recordedFile.delete()
                Result.failure(IllegalStateException("Rekaman terlalu singkat atau kosong. Tahan tombol sedikit lebih lama."))
            } else {
                Result.success(recordedFile)
            }
        } catch (e: Exception) {
            file?.delete()
            Result.failure(IllegalStateException("Rekaman gagal diselesaikan. Tahan tombol sedikit lebih lama.", e))
        } finally {
            activeRecorder.release()
        }
    }

    fun cancel() {
        val activeRecorder = recorder
        recorder = null
        val file = outputFile
        outputFile = null
        if (activeRecorder != null) {
            try {
                activeRecorder.stop()
            } catch (_: Exception) {
                // A short recording may not have enough data to finalize.
            } finally {
                activeRecorder.release()
            }
        }
        file?.delete()
    }

    @Suppress("DEPRECATION")
    private fun createMediaRecorder(context: Context): MediaRecorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            MediaRecorder()
        }
}
