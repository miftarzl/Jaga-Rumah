package com.nayakacctv.ui.utils

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * AppLockManager mengatur status proteksi layar aplikasi mirip m-banking.
 * Memiliki toleransi waktu (grace period) 30 detik: jika aplikasi hanya
 * diminimalkan sebentar (< 30 detik), pengguna tidak perlu login/scan ulang.
 */
object AppLockManager {
    const val GRACE_PERIOD_MILLIS = 30_000L // 30 detik toleransi lock

    private val _isLocked = MutableStateFlow(false)
    val isLockedState: StateFlow<Boolean> = _isLocked.asStateFlow()

    private var backgroundTimestamp: Long = 0L

    // Flag untuk mengabaikan lock saat intent sistem tertentu sedang aktif (misal Google Auth / File Share)
    var isSuppressed: Boolean = false

    fun lockImmediately() {
        if (!isSuppressed) {
            _isLocked.value = true
        }
        backgroundTimestamp = 0L
    }

    // Alias untuk kompatibilitas
    fun lock() {
        lockImmediately()
    }

    fun unlock() {
        _isLocked.value = false
        backgroundTimestamp = 0L
    }

    fun isLocked(): Boolean = _isLocked.value

    fun onAppBackgrounded() {
        if (!isSuppressed && !_isLocked.value) {
            backgroundTimestamp = System.currentTimeMillis()
        }
    }

    fun onAppForegrounded() {
        if (isSuppressed) {
            backgroundTimestamp = 0L
            return
        }
        if (backgroundTimestamp > 0L) {
            val elapsed = System.currentTimeMillis() - backgroundTimestamp
            if (elapsed >= GRACE_PERIOD_MILLIS) {
                _isLocked.value = true
            }
            backgroundTimestamp = 0L
        }
    }
}
