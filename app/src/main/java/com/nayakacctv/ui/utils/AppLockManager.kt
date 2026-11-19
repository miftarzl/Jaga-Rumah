package com.nayakacctv.ui.utils

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * AppLockManager mengatur status proteksi layar aplikasi mirip m-banking.
 * Ketika aplikasi diminimalkan / beralih ke background atau dibuka kembali,
 * status lock aktif sehingga pengguna wajib melakukan verifikasi biometrik
 * atau kata sandi sebelum dapat mengakses layar monitoring CCTV.
 */
object AppLockManager {
    private val _isLocked = MutableStateFlow(false)
    val isLockedState: StateFlow<Boolean> = _isLocked.asStateFlow()

    // Flag untuk mengabaikan lock saat intent sistem tertentu sedang aktif
    var isSuppressed: Boolean = false

    fun lock() {
        if (!isSuppressed) {
            _isLocked.value = true
        }
    }

    fun unlock() {
        _isLocked.value = false
    }

    fun isLocked(): Boolean = _isLocked.value
}
