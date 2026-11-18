package com.nayakacctv.data.repository

import com.google.gson.Gson
import com.nayakacctv.data.local.TokenManager
import com.nayakacctv.data.model.*
import com.nayakacctv.data.network.ApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response

class NayakaRepository(
    private val api: ApiService,
    private val tokenManager: TokenManager
) {
    private val gson = Gson()

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
            val customer = tokens.customer ?: Customer("cust-1", "Pengguna Nayaka", email)
            tokenManager.saveCustomer(customer)
            customer
        }
    }

    suspend fun loginGoogle(idToken: String): Result<Customer> {
        val req = GoogleLoginRequest(idToken, tokenManager.getInstallationId())
        return safeApiCall { api.loginGoogle(req) }.mapCatching { tokens ->
            tokenManager.saveTokens(tokens.accessToken, tokens.refreshToken)
            val customer = tokens.customer ?: Customer("cust-google", "Pengguna Google", "user@gmail.com")
            tokenManager.saveCustomer(customer)
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
            val customer = tokens.customer ?: Customer("cust-new", name, email)
            tokenManager.saveCustomer(customer)
            customer
        }
    }

    suspend fun forgotPassword(email: String): Result<String> {
        return safeApiCall { api.forgotPassword(ForgotPasswordRequest(email)) }.map { "Kode OTP telah dikirim ke email." }
    }

    suspend fun resetPassword(email: String, code: String, newPass: String): Result<String> {
        return safeApiCall { api.resetPassword(ResetPasswordRequest(email, code, newPass)) }.map { "Kata sandi berhasil direset." }
    }

    suspend fun getDashboard(): Result<DashboardSummary> = safeApiCall { api.getDashboard() }

    suspend fun getCameras(): Result<List<Camera>> = safeApiCall { api.getCameras() }.map { it.cameras }

    suspend fun getCameraDetail(id: String): Result<Camera> = safeApiCall { api.getCameraDetail(id) }.mapCatching {
        it.camera ?: throw Exception("Kamera tidak ditemukan")
    }

    suspend fun updateCameraSettings(id: String, motion: Boolean, notification: Boolean): Result<CameraSettings> {
        return safeApiCall { api.updateCameraSettings(id, UpdateCameraSettingsRequest(motion, notification)) }
    }

    suspend fun getRecordings(cameraId: String? = null, from: String? = null, to: String? = null): Result<List<Recording>> {
        return safeApiCall { api.getRecordings(cameraId, from, to) }.map { res ->
            res.recordings.map { r ->
                r.copy(
                    duration = if (r.duration > 0) r.duration else (r.durationSeconds ?: 0L),
                    cameraId = if (r.cameraId.isNotBlank()) r.cameraId else (r.deviceId ?: "")
                )
            }
        }
    }

    suspend fun getRecordingDetail(id: String): Result<Recording> {
        return safeApiCall { api.getRecordingDetail(id) }.mapCatching { res ->
            val r = res.recording ?: throw Exception("Rekaman tidak ditemukan")
            r.copy(
                duration = if (r.duration > 0) r.duration else (r.durationSeconds ?: 0L),
                cameraId = if (r.cameraId.isNotBlank()) r.cameraId else (r.deviceId ?: "")
            )
        }
    }

    suspend fun getAlerts(): Result<List<AlertItem>> = safeApiCall { api.getAlerts() }.map { res ->
        res.alerts.map { a ->
            a.copy(
                time = if (a.time.isNotBlank()) a.time else (a.occurredAt ?: ""),
                read = a.isRead ?: a.read,
                cameraId = if (a.cameraId.isNotBlank()) a.cameraId else (a.deviceId ?: "")
            )
        }
    }

    suspend fun markAlertRead(id: String): Result<Boolean> = safeApiCall { api.markAlertRead(id) }.map { true }

    suspend fun markAllAlertsRead(): Result<Boolean> = safeApiCall { api.markAllAlertsRead() }.map { true }

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

    suspend fun logout() {
        withContext(Dispatchers.IO) {
            try {
                api.logout()
            } catch (_: Exception) {}
            tokenManager.clearSession()
        }
    }

    fun isLoggedIn(): Boolean = tokenManager.isLoggedIn()
    fun getCurrentCustomer(): Customer? = tokenManager.getCustomer()
    fun isBiometricEnabled(): Boolean = tokenManager.isBiometricEnabled()
    fun setBiometricEnabled(enabled: Boolean) = tokenManager.setBiometricEnabled(enabled)
}
