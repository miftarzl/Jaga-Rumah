package com.nayakacctv.ui.utils

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

class MicrophoneCapture {
    private var audioRecord: AudioRecord? = null
    private var captureJob: Job? = null

    fun start(
        scope: CoroutineScope,
        onLevelChanged: (Float) -> Unit,
        onError: (String) -> Unit
    ): Boolean {
        if (captureJob?.isActive == true) return true

        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBufferSize <= 0) {
            onError("Format audio mikrofon tidak didukung.")
            return false
        }

        val recorder = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBufferSize * 2, SAMPLE_RATE)
            )
        } catch (e: SecurityException) {
            onError("Izin mikrofon belum diberikan.")
            return false
        } catch (e: IllegalArgumentException) {
            onError("Mikrofon tidak dapat diinisialisasi.")
            return false
        }

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            onError("Mikrofon tidak siap digunakan.")
            return false
        }

        try {
            recorder.startRecording()
        } catch (e: IllegalStateException) {
            recorder.release()
            onError("Gagal memulai mikrofon.")
            return false
        }

        audioRecord = recorder
        captureJob = scope.launch(Dispatchers.IO) {
            val samples = ShortArray(maxOf(minBufferSize, SAMPLE_RATE) / 2)
            try {
                while (isActive) {
                    val count = recorder.read(samples, 0, samples.size)
                    if (count < 0) {
                        throw IllegalStateException("Gagal membaca mikrofon ($count).")
                    }
                    if (count > 0) {
                        var sumSquares = 0.0
                        for (index in 0 until count) {
                            val sample = samples[index].toDouble()
                            sumSquares += sample * sample
                        }
                        val level = (sqrt(sumSquares / count) / Short.MAX_VALUE)
                            .toFloat()
                            .coerceIn(0f, 1f)
                        withContext(Dispatchers.Main) {
                            onLevelChanged(level)
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onError(e.localizedMessage ?: "Mikrofon terputus.")
                }
            }
        }
        return true
    }

    fun stop() {
        captureJob?.cancel()
        captureJob = null
        val recorder = audioRecord
        audioRecord = null
        if (recorder != null) {
            try {
                recorder.stop()
            } catch (_: IllegalStateException) {
                // The recorder may already have stopped after a read failure.
            } finally {
                recorder.release()
            }
        }
    }

    companion object {
        private const val SAMPLE_RATE = 16_000
    }
}
