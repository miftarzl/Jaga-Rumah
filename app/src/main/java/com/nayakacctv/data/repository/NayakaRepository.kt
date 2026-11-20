package com.nayakacctv.data.repository

import android.content.Context
import com.google.gson.Gson
import com.nayakacctv.data.local.TokenManager
import com.nayakacctv.data.model.*
import com.nayakacctv.data.network.ApiService
import com.nayakacctv.ui.utils.BiometricHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response

class NayakaRepository(
    private val api: ApiService,
    private val tokenManager: TokenManager
) {
    private val gson = Gson()
    private val dashboardCache = TimedMemoryCache<DashboardSummary>(ttlMillis = 15_000)
    private val camerasCache = TimedMemoryCache<List<Camera>>(ttlMillis = 30_000)
    private val cameraDetailCache = TimedMemoryCache<Camera>(ttlMillis = 30_000)
    private val recordingsCache = TimedMemoryCache<List<Recording>>(ttlMillis = 30_000)
    private val alertsCache = TimedMemoryCache<List<AlertItem>>(ttlMillis = 15_000)

    private fun clearCameraCaches() {
        dashboardCache.clear()
        camerasCache.clear()
        cameraDetailCache.clear()
    }

    private fun clearDataCaches() {
        dashboardCache.clear()
        camerasCache.clear()
        cameraDetailCache.clear()
        recordingsCache.clear()
        alertsCache.clear()
    }

    private suspend fun <T> safeApiCall(call: suspend () -> Response<ApiResponse<T>>): Result<T> {
        return withContext(Dispatchers.IO) {
            try {
                val response = call()
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body?.data != null) {
                        Result.success(body.data)
                    } else {
                        Result.failure(Exception("Data kosong dari server"))
                    }
                } else {
                    val errString = response.errorBody()?.string()
                    val errorMsg = try {
                        val parsed = gson.fromJson(errString, ApiErrorResponse::class.java)
                        val baseMsg = parsed.error?.message ?: "Terjadi kesalahan (${response.code()})"
                        val fieldsMap = (parsed.error?.details?.get("fields") as? Map<*, *>)
                        if (!fieldsMap.isNullOrEmpty()) {
                            val detailLines = fieldsMap.entries.joinToString("\n") { "• ${it.key}: ${it.value}" }
                            "$baseMsg\n\nKolom bermasalah:\n$detailLines"
                        } else {
                            baseMsg
                        }
                    } catch (e: Exception) {
                        "Terjadi kesalahan pada server (${response.code()})"
                    }
                    Result.failure(Exception(errorMsg))
                }
            } catch (e: Exception) {
                Result.failure(Exception(e.localizedMessage ?: "Gagal terhubung ke jaringan"))
            }
        }
    }

    suspend fun login(email: String, pass: String): Result<Customer> {
        val req = LoginRequest(
            email = email.trim(),
            password = pass,
            installationId = tokenManager.getInstallationId(),
            platform = "android",
            appVersion = "1.0.0"
        )
        return safeApiCall { api.login(req) }.mapCatching { tokens ->
            tokenManager.saveTokens(tokens.accessToken, tokens.refreshToken)
            tokenManager.saveAuthProvider("password")
            val customer = tokens.customer ?: Customer("cust-1", "Pengguna Nayaka", email)
            tokenManager.saveCustomer(customer)
            clearDataCaches()
            customer
        }
    }

    suspend fun loginGoogle(idToken: String): Result<Customer> {
        val req = GoogleLoginRequest(idToken, tokenManager.getInstallationId())
        return safeApiCall { api.loginGoogle(req) }.mapCatching { tokens ->
            tokenManager.saveTokens(tokens.accessToken, tokens.refreshToken)
            tokenManager.saveAuthProvider("google")
            val customer = tokens.customer ?: Customer("cust-google", "Pengguna Google", "user@gmail.com")
            tokenManager.saveCustomer(customer)
            clearDataCaches()
            customer
        }
    }

    suspend fun register(name: String, email: String, pass: String): Result<Customer> {
        val req = RegisterRequest(
            fullName = name,
            email = email.trim(),
            password = pass,
            installationId = tokenManager.getInstallationId(),
            platform = "android",
            appVersion = "1.0.0"
        )
        return safeApiCall { api.register(req) }.mapCatching { tokens ->
            tokenManager.saveTokens(tokens.accessToken, tokens.refreshToken)
            tokenManager.saveAuthProvider("password")
            val customer = tokens.customer ?: Customer("cust-new", name, email)
            tokenManager.saveCustomer(customer)
            clearDataCaches()
            customer
        }
    }

    suspend fun forgotPassword(email: String): Result<String> {
        return safeApiCall { api.forgotPassword(ForgotPasswordRequest(email)) }.map { "Kode OTP telah dikirim ke email." }
    }

    suspend fun resetPassword(email: String, code: String, newPass: String): Result<String> {
        return safeApiCall { api.resetPassword(ResetPasswordRequest(email, code, newPass)) }.map { "Kata sandi berhasil direset." }
    }

    suspend fun getDashboard(forceRefresh: Boolean = false): Result<DashboardSummary> =
        dashboardCache.getOrLoad(forceRefresh = forceRefresh) {
            safeApiCall { api.getDashboard() }
        }

    suspend fun getCameras(forceRefresh: Boolean = false): Result<List<Camera>> =
        camerasCache.getOrLoad(forceRefresh = forceRefresh) {
            safeApiCall { api.getCameras() }.map { res ->
                val deleted = tokenManager.getDeletedCameraIds()
                val overrides = tokenManager.getCameraOverrides()
                res.cameras
                    .filter { it.id !in deleted }
                    .map { cam ->
                        val edit = overrides[cam.id]
                        if (edit != null) {
                            cam.copy(
                                name = edit.name.ifBlank { cam.name },
                                location = edit.location.ifBlank { cam.location },
                                streamUrl = edit.streamUrl.ifBlank { cam.streamUrl }
                            )
                        } else {
                            cam
                        }
                    }
            }
        }

    suspend fun getCameraDetail(id: String, forceRefresh: Boolean = false): Result<Camera> =
        cameraDetailCache.getOrLoad(
            key = id,
            forceRefresh = forceRefresh
        ) {
            safeApiCall { api.getCameraDetail(id) }.mapCatching { res ->
                val cam = res.camera ?: throw Exception("Kamera tidak ditemukan")
                val overrides = tokenManager.getCameraOverrides()
                val edit = overrides[cam.id]
                if (edit != null) {
                    cam.copy(
                        name = edit.name.ifBlank { cam.name },
                        location = edit.location.ifBlank { cam.location },
                        streamUrl = edit.streamUrl.ifBlank { cam.streamUrl }
                    )
                } else {
                    cam
                }
            }
        }

    suspend fun getCameraStream(id: String): Result<CameraStream> =
        safeApiCall { api.getCameraStream(id) }

    suspend fun updateCamera(id: String, name: String, location: String, streamUrl: String): Result<Camera> {
        tokenManager.saveCameraOverride(id, name.trim(), location.trim(), streamUrl.trim())
        clearCameraCaches()
        return getCameraDetail(id, forceRefresh = true)
    }

    suspend fun deleteCamera(id: String): Result<Boolean> {
        tokenManager.markCameraDeleted(id)
        clearCameraCaches()
        return Result.success(true)
    }

    suspend fun claimDevice(code: String): Result<ClaimDeviceResponse> {
        val cleanCode = code.trim().uppercase()
        val result = safeApiCall { api.claimDevice(ClaimDeviceRequest(cleanCode)) }
        result.onSuccess { claimed ->
            tokenManager.unmarkCameraDeleted(claimed.id)
            clearCameraCaches()
        }
        return result
    }

    suspend fun updateCameraSettings(id: String, motion: Boolean, notification: Boolean): Result<CameraSettings> {
        return safeApiCall { api.updateCameraSettings(id, UpdateCameraSettingsRequest(motion, notification)) }
            .onSuccess { clearCameraCaches() }
    }

    suspend fun getRecordings(
        cameraId: String? = null,
        from: String? = null,
        to: String? = null,
        forceRefresh: Boolean = false
    ): Result<List<Recording>> {
        val cacheKey = listOf(cameraId, from, to).joinToString("|") { value ->
            value?.let { "${it.length}:$it" } ?: "-:"
        }
        return recordingsCache.getOrLoad(key = cacheKey, forceRefresh = forceRefresh) {
            val localList = tokenManager.getLocalRecordings().filter { r ->
                cameraId == null || r.cameraId == cameraId
            }
            val remoteRes = safeApiCall { api.getRecordings(cameraId, from, to) }
            remoteRes.map { res ->
                val remoteList = res.recordings.map { r ->
                    r.copy(
                        duration = if (r.duration > 0) r.duration else (r.durationSeconds ?: 0L),
                        cameraId = if (r.cameraId.isNotBlank()) r.cameraId else (r.deviceId ?: "")
                    )
                }
                (localList + remoteList).distinctBy { it.id }
            }.recoverCatching { error ->
                if (localList.isNotEmpty()) localList else throw error
            }
        }
    }

    suspend fun getRecordingDetail(id: String): Result<Recording> {
        val local = tokenManager.getLocalRecordings().find { it.id == id }
        if (local != null) {
            return Result.success(local)
        }
        return safeApiCall { api.getRecordingDetail(id) }.mapCatching { res ->
            val r = res.recording ?: throw Exception("Rekaman tidak ditemukan")
            r.copy(
                duration = if (r.duration > 0) r.duration else (r.durationSeconds ?: 0L),
                cameraId = if (r.cameraId.isNotBlank()) r.cameraId else (r.deviceId ?: "")
            )
        }
    }

    fun saveLocalRecording(recording: Recording) {
        tokenManager.saveLocalRecording(recording)
        recordingsCache.clear()
    }

    fun deleteLocalRecording(id: String) {
        tokenManager.deleteLocalRecording(id)
        recordingsCache.clear()
    }

    suspend fun getAlerts(forceRefresh: Boolean = false): Result<List<AlertItem>> =
        alertsCache.getOrLoad(forceRefresh = forceRefresh) {
            safeApiCall { api.getAlerts() }.map { res ->
                res.alerts.map { a ->
                    a.copy(
                        time = if (a.time.isNotBlank()) a.time else (a.occurredAt ?: ""),
                        read = a.isRead ?: a.read,
                        cameraId = if (a.cameraId.isNotBlank()) a.cameraId else (a.deviceId ?: "")
                    )
                }
            }
        }

    suspend fun markAlertRead(id: String): Result<Boolean> =
        safeApiCall { api.markAlertRead(id) }.map { true }.onSuccess { alertsCache.clear() }

    suspend fun markAllAlertsRead(): Result<Boolean> =
        safeApiCall { api.markAllAlertsRead() }.map { true }.onSuccess { alertsCache.clear() }

    suspend fun getEmergencyContacts(): Result<List<EmergencyContact>> = safeApiCall { api.getEmergencyContacts() }.map { it.contacts }

    suspend fun triggerSos(): Result<String> = safeApiCall { api.triggerSos(TriggerSosRequest()) }.map { "Peringatan darurat berhasil dipancarkan." }

    suspend fun getSubscriptions(): Result<List<Subscription>> = safeApiCall { api.getMe() }.map { res ->
        val sub = res.subscription
        if (sub != null) listOf(sub) else emptyList()
    }

    suspend fun getHelpArticles(): Result<List<HelpArticle>> = safeApiCall { api.getHelpArticles() }.map { it.articles }

    suspend fun getTerms(): Result<TermsDoc> = safeApiCall { api.getTerms() }.mapCatching {
        it.document ?: TermsDoc()
    }

    suspend fun logout(clearBiometric: Boolean = false) {
        clearDataCaches()
        withContext(Dispatchers.IO) {
            try {
                api.logout()
            } catch (_: Exception) {}
            // Keep biometric credentials if enabled so user can log back in with biometrics unless explicitly switching account
            tokenManager.clearSession(preserveBiometric = !clearBiometric)
        }
        clearDataCaches()
    }

    suspend fun loginWithBiometric(): Result<Customer> {
        val biometricRefreshToken = tokenManager.getBiometricRefreshToken()
            ?: return Result.failure(Exception("Sesi biometrik belum tersedia. Silakan masuk dengan kata sandi."))

        val req = RefreshTokenRequest(
            refreshToken = biometricRefreshToken,
            installationId = tokenManager.getInstallationId()
        )
        return safeApiCall { api.refreshToken(req) }.mapCatching { tokens ->
            tokenManager.saveTokens(tokens.accessToken, tokens.refreshToken)
            tokenManager.updateBiometricToken(tokens.refreshToken)
            clearDataCaches()

            val customer = try {
                val meRes = api.getMe()
                meRes.body()?.data?.profile
                    ?: tokenManager.getBiometricCustomer()
                    ?: Customer("cust-bio", "Pengguna Nayaka", "")
            } catch (_: Exception) {
                tokenManager.getBiometricCustomer() ?: Customer("cust-bio", "Pengguna Nayaka", "")
            }
            tokenManager.saveCustomer(customer)
            customer
        }.recoverCatching { error ->
            val msg = error.message ?: ""
            if (msg.contains("401") || msg.contains("tidak berlaku") || msg.contains("expired") || msg.contains("TOKEN_REVOKED")) {
                throw Exception("Sesi biometrik telah berakhir. Silakan masuk menggunakan kata sandi Anda.")
            } else {
                throw error
            }
        }
    }

    fun enableBiometric(): Boolean {
        val refreshToken = tokenManager.getRefreshToken() ?: return false
        val customer = tokenManager.getCustomer()
        tokenManager.enableBiometric(refreshToken, customer)
        return true
    }

    fun disableBiometric() {
        tokenManager.disableBiometric()
    }

    fun canUseBiometric(context: Context): Boolean {
        return tokenManager.isBiometricEnabled() &&
                !tokenManager.getBiometricRefreshToken().isNullOrBlank() &&
                BiometricHelper.isBiometricAvailable(context)
    }

    fun getBiometricCustomer(): Customer? = tokenManager.getBiometricCustomer()

    fun isLoggedIn(): Boolean = tokenManager.isLoggedIn()
    fun isGoogleUser(): Boolean = tokenManager.isGoogleUser()
    fun getCurrentCustomer(): Customer? = tokenManager.getCustomer()
    fun isBiometricEnabled(): Boolean = tokenManager.isBiometricEnabled()
    fun setBiometricEnabled(enabled: Boolean) = tokenManager.setBiometricEnabled(enabled)
}
