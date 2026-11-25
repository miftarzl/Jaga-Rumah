package com.nayakacctv.ui.utils

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * AppLockManager mengatur status proteksi layar aplikasi mirip m-banking.
 * Fitur auto-lock sementara dinonaktifkan melalui APP_LOCK_ENABLED.
 */
object AppLockManager {
    const val APP_LOCK_ENABLED = false
    const val GRACE_PERIOD_MILLIS = 30_000L // 30 detik toleransi lock

    private val _isLocked = MutableStateFlow(false)
    val isLockedState: StateFlow<Boolean> = _isLocked.asStateFlow()

    private var backgroundTimestamp: Long = 0L

    // Flag untuk mengabaikan lock saat intent sistem tertentu sedang aktif (misal Google Auth / File Share)
    var isSuppressed: Boolean = false

    fun lockImmediately() {
        if (!APP_LOCK_ENABLED) {
            _isLocked.value = false
            backgroundTimestamp = 0L
            return
        }
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
        if (!APP_LOCK_ENABLED) {
            backgroundTimestamp = 0L
            _isLocked.value = false
            return
        }
        if (!isSuppressed && !_isLocked.value) {
            backgroundTimestamp = System.currentTimeMillis()
        }
    }

    fun onAppForegrounded() {
        if (!APP_LOCK_ENABLED || isSuppressed) {
            backgroundTimestamp = 0L
            if (!APP_LOCK_ENABLED) {
                _isLocked.value = false
            }
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
