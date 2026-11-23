package com.nayakacctv.data.repository

import android.content.Context
import android.util.Base64
import com.google.gson.Gson
import com.nayakacctv.data.local.TokenManager
import com.nayakacctv.data.model.*
import com.nayakacctv.data.network.ApiService
import com.nayakacctv.ui.utils.BiometricHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

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
            tokenManager.updateBiometricToken(tokens.refreshToken, customer)
            clearDataCaches()
            customer
        }
    }

    suspend fun loginGoogle(idToken: String): Result<Customer> {
        val req = GoogleLoginRequest(idToken, tokenManager.getInstallationId())
        return safeApiCall { api.loginGoogle(req) }.mapCatching { tokens ->
            tokenManager.saveTokens(tokens.accessToken, tokens.refreshToken)
            tokenManager.saveAuthProvider("google")
            val customer = tokens.customer ?: googleCustomerFallback(idToken)
            tokenManager.saveCustomer(customer)
            tokenManager.updateBiometricToken(tokens.refreshToken, customer)
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
            tokenManager.updateBiometricToken(tokens.refreshToken, customer)
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

    suspend fun controlCameraPtz(
        cameraId: String,
        direction: PtzDirection,
        speed: Float = 0.5f,
        durationMs: Int = 400
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val response = api.controlCameraPtz(
                cameraId,
                PtzControlRequest(
                    direction = direction,
                    speed = speed,
                    durationMs = durationMs
                )
            )
            if (response.isSuccessful) {
                val responseText = response.body()?.use { it.string() }.orEmpty()
                Result.success(
                    responseText.takeIf { it.isNotBlank() }
                        ?: "HTTP ${response.code()}; server tidak memberikan detail gerakan kamera."
                )
            } else {
                val responseText = response.errorBody()?.use { it.string() }.orEmpty()
                val apiMessage = try {
                    gson.fromJson(responseText, ApiErrorResponse::class.java)?.error?.message
                } catch (_: Exception) {
                    null
                }
                Result.failure(
                    Exception(
                        apiMessage?.takeIf { it.isNotBlank() }
                            ?: responseText.takeIf { it.isNotBlank() }?.take(500)
                            ?: "Gagal mengontrol kamera (HTTP ${response.code()})."
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(Exception(e.localizedMessage ?: "Gagal mengirim perintah PTZ."))
        }
    }

    suspend fun sendCameraTalk(cameraId: String, audioFile: File): Result<String> =
        withContext(Dispatchers.IO) {
            if (!audioFile.isFile || audioFile.length() <= 0L) {
                return@withContext Result.failure(Exception("Rekaman audio kosong atau tidak ditemukan."))
            }
            if (audioFile.length() > MAX_CAMERA_TALK_AUDIO_BYTES) {
                return@withContext Result.failure(Exception("Ukuran audio melebihi batas 5 MB."))
            }

            try {
                val audioPart = MultipartBody.Part.createFormData(
                    "audio",
                    audioFile.name,
                    audioFile.asRequestBody("audio/mp4".toMediaType())
                )
                val response = api.sendCameraTalk(cameraId, audioPart)
                if (response.isSuccessful) {
                    val responseText = response.body()?.use { it.string() }.orEmpty()
                    Result.success(
                        responseText.takeIf { it.isNotBlank() }
                            ?: "Server menerima audio untuk diputar di speaker kamera."
                    )
                } else {
                    val responseText = response.errorBody()?.use { it.string() }.orEmpty()
                    val apiMessage = try {
                        gson.fromJson(responseText, ApiErrorResponse::class.java)?.error?.message
                    } catch (_: Exception) {
                        null
                    }
                    Result.failure(
                        Exception(
                            apiMessage?.takeIf { it.isNotBlank() }
                                ?: responseText.takeIf { it.isNotBlank() }?.take(500)
                                ?: "Gagal mengirim audio ke kamera (HTTP ${response.code()})."
                        )
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(Exception(e.localizedMessage ?: "Gagal mengirim audio ke kamera."))
            }
        }

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
                (cameraId == null || r.cameraId == cameraId) &&
                    isWithinRange(r.startedAt, from, to)
            }
            val remoteRecordings = mutableListOf<Recording>()
            val seenCursors = mutableSetOf<String>()
            var cursor: String? = null
            var pageCount = 0
            var paginationError: Throwable? = null
            var hasMore = true

            while (hasMore && pageCount < 100) {
                val pageResult = safeApiCall {
                    api.getRecordings(cameraId, from, to, limit = 20, cursor = cursor)
                }
                if (pageResult.isFailure) {
                    paginationError = pageResult.exceptionOrNull()
                    break
                }
                val page = pageResult.getOrThrow()
                remoteRecordings += page.recordings
                val nextCursor = page.pagination?.nextCursor
                if (page.pagination?.hasMore == true) {
                    if (nextCursor.isNullOrBlank() || !seenCursors.add(nextCursor)) {
                        paginationError = Exception("Cursor halaman rekaman dari server tidak valid.")
                        hasMore = false
                    } else {
                        cursor = nextCursor
                    }
                } else {
                    hasMore = false
                }
                pageCount++
            }

            if (hasMore && paginationError == null) {
                paginationError = Exception("Terlalu banyak halaman rekaman untuk dimuat.")
            }
            if (paginationError != null) {
                return@getOrLoad if (localList.isNotEmpty()) {
                    Result.success(localList)
                } else {
                    Result.failure(paginationError!!)
                }
            }

            val remoteList = remoteRecordings.map { r ->
                r.copy(
                    duration = if (r.duration > 0) r.duration else (r.durationSeconds ?: 0L),
                    cameraId = if (r.cameraId.isNotBlank()) r.cameraId else (r.deviceId ?: ""),
                    sizeBytes = if (r.sizeBytes > 0) r.sizeBytes else (r.fileSizeBytes ?: 0L)
                )
            }
            Result.success((localList + remoteList).distinctBy { it.id }.sortedByDescending {
                parseIsoTimestamp(it.startedAt) ?: 0L
            })
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
                cameraId = if (r.cameraId.isNotBlank()) r.cameraId else (r.deviceId ?: ""),
                sizeBytes = if (r.sizeBytes > 0) r.sizeBytes else (r.fileSizeBytes ?: 0L)
            )
        }
    }

    suspend fun downloadRecording(id: String): Result<ResponseBody> = withContext(Dispatchers.IO) {
        try {
            val response = api.downloadRecording(id)
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) Result.success(body)
                else Result.failure(Exception("Data video kosong dari server"))
            } else {
                response.errorBody()?.close()
                Result.failure(Exception("Gagal mengunduh rekaman (${response.code()})"))
            }
        } catch (e: Exception) {
            Result.failure(Exception(e.localizedMessage ?: "Gagal mengunduh rekaman"))
        }
    }

    suspend fun downloadRecordingClip(
        cameraId: String,
        from: String,
        to: String
    ): Result<ResponseBody> = withContext(Dispatchers.IO) {
        try {
            val response = api.downloadRecordingClip(cameraId, from, to)
            if (response.isSuccessful) {
                val body = response.body()
                if (body == null) {
                    Result.failure(Exception("Data klip kosong dari server"))
                } else {
                    val contentType = body.contentType()?.toString().orEmpty()
                    if (contentType.startsWith("video/mp4") ||
                        contentType.startsWith("application/octet-stream")
                    ) {
                        Result.success(body)
                    } else {
                        val responseText = body.string().take(500)
                        Result.failure(
                            Exception(
                                extractClipErrorMessage(
                                    responseText,
                                    "Server mengembalikan format klip yang tidak didukung ($contentType)"
                                )
                            )
                        )
                    }
                }
            } else {
                val responseText = response.errorBody()?.use { it.string() }.orEmpty()
                Result.failure(
                    Exception(
                        extractClipErrorMessage(
                            responseText,
                            "Gagal mengambil klip (${response.code()})"
                        )
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(Exception(e.localizedMessage ?: "Gagal mengambil klip"))
        }
    }

    private fun extractClipErrorMessage(responseText: String, fallback: String): String {
        val apiMessage = try {
            gson.fromJson(responseText, ApiErrorResponse::class.java)?.error?.message
        } catch (_: Exception) {
            null
        }
        return apiMessage?.takeIf { it.isNotBlank() }
            ?: responseText.takeIf { it.isNotBlank() }?.take(500)
            ?: fallback
    }

    private fun isWithinRange(value: String, from: String?, to: String?): Boolean {
        if (from == null && to == null) return true
        val timestamp = parseIsoTimestamp(value) ?: return false
        val fromTimestamp = from?.let(::parseIsoTimestamp)
        val toTimestamp = to?.let(::parseIsoTimestamp)
        return (fromTimestamp == null || timestamp >= fromTimestamp) &&
            (toTimestamp == null || timestamp <= toTimestamp)
    }

    private fun parseIsoTimestamp(value: String): Long? {
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSSX",
            "yyyy-MM-dd'T'HH:mm:ssX",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'"
        )
        for (pattern in patterns) {
            val parsed = runCatching {
                SimpleDateFormat(pattern, Locale.US).parse(value)?.time
            }.getOrNull()
            if (parsed != null) return parsed
        }
        return null
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

    private fun googleCustomerFallback(idToken: String): Customer {
        val claims = runCatching {
            val payload = idToken.split('.').getOrNull(1) ?: return@runCatching null
            val decoded = Base64.decode(
                payload,
                Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
            )
            gson.fromJson(String(decoded, Charsets.UTF_8), com.google.gson.JsonObject::class.java)
        }.getOrNull()
        val subject = runCatching { claims?.get("sub")?.asString }.getOrNull()
        val email = runCatching { claims?.get("email")?.asString }.getOrNull().orEmpty()
        val name = runCatching { claims?.get("name")?.asString }.getOrNull()
            ?.takeIf { it.isNotBlank() } ?: "Pengguna Google"
        return Customer(
            id = subject?.takeIf { it.isNotBlank() }?.let { "google-$it" } ?: "cust-google",
            fullName = name,
            email = email.ifBlank { "user@gmail.com" }
        )
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
        val customer = tokenManager.getCustomer() ?: return false
        return tokenManager.enableBiometric(refreshToken, customer)
    }

    fun disableBiometric() {
        tokenManager.disableBiometric()
    }

    fun canUseBiometric(context: Context): Boolean {
        return !tokenManager.getBiometricRefreshToken().isNullOrBlank() &&
                BiometricHelper.isBiometricAvailable(context)
    }

    fun getBiometricCustomer(): Customer? = tokenManager.getBiometricCustomer()

    fun isLoggedIn(): Boolean = tokenManager.isLoggedIn()
    fun isGoogleUser(): Boolean = tokenManager.isGoogleUser()
    fun getCurrentCustomer(): Customer? = tokenManager.getCustomer()
    fun isBiometricEnabled(): Boolean = tokenManager.isBiometricEnabled()
    fun setBiometricEnabled(enabled: Boolean) = tokenManager.setBiometricEnabled(enabled)
}
