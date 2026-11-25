package com.nayakacctv.data.network

import com.nayakacctv.data.model.*
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Streaming
import retrofit2.http.*

interface ApiService {

    // ── Authentication ──────────────────────────────────────────────────
    @POST("mobile/auth/register")
    suspend fun register(
        @Body request: RegisterRequest
    ): Response<ApiResponse<AuthTokens>>

    @POST("mobile/auth/login")
    suspend fun login(
        @Body request: LoginRequest
    ): Response<ApiResponse<AuthTokens>>

    @POST("mobile/auth/google")
    suspend fun loginGoogle(
        @Body request: GoogleLoginRequest
    ): Response<ApiResponse<AuthTokens>>

    @POST("mobile/auth/refresh")
    suspend fun refreshToken(
        @Body request: RefreshTokenRequest
    ): Response<ApiResponse<AuthTokens>>

    @POST("mobile/auth/logout")
    suspend fun logout(): Response<ApiResponse<Map<String, Any>>>

    @POST("mobile/auth/forgot-password")
    suspend fun forgotPassword(
        @Body request: ForgotPasswordRequest
    ): Response<ApiResponse<Map<String, Any>>>

    @POST("mobile/auth/reset-password")
    suspend fun resetPassword(
        @Body request: ResetPasswordRequest
    ): Response<ApiResponse<Map<String, Any>>>

    // ── Dashboard ───────────────────────────────────────────────────────
    @GET("mobile/dashboard")
    suspend fun getDashboard(): Response<ApiResponse<DashboardSummary>>

    // ── Cameras ─────────────────────────────────────────────────────────
    @GET("mobile/cameras")
    suspend fun getCameras(
        @Query("limit") limit: Int = 50,
        @Query("cursor") cursor: String? = null,
        @Query("status") status: String? = null
    ): Response<ApiResponse<CameraListResponse>>

    @GET("mobile/cameras/{id}")
    suspend fun getCameraDetail(
        @Path("id") id: String
    ): Response<ApiResponse<CameraDetailResponse>>

    @PATCH("mobile/cameras/{id}/settings")
    suspend fun updateCameraSettings(
        @Path("id") id: String,
        @Body settings: UpdateCameraSettingsRequest
    ): Response<ApiResponse<CameraSettings>>

    @GET("mobile/cameras/{id}/stream")
    suspend fun getCameraStream(
        @Path("id") id: String
    ): Response<ApiResponse<CameraStream>>

    @PATCH("mobile/cameras/{id}")
    suspend fun updateCameraInfo(
        @Path("id") id: String,
        @Body request: Map<String, String>
    ): Response<ApiResponse<CameraDetailResponse>>

    @DELETE("mobile/cameras/{id}")
    suspend fun deleteCamera(
        @Path("id") id: String
    ): Response<ResponseBody>

    @POST("mobile/cameras/{id}/ptz")
    suspend fun controlCameraPtz(
        @Path("id") id: String,
        @Body request: PtzControlRequest
    ): Response<ResponseBody>

    @Multipart
    @POST("mobile/cameras/{id}/talk")
    suspend fun sendCameraTalk(
        @Path("id") id: String,
        @Part audio: MultipartBody.Part
    ): Response<ResponseBody>

    @POST("mobile/devices/claim")
    suspend fun claimDevice(
        @Body request: ClaimDeviceRequest
    ): Response<ApiResponse<ClaimDeviceResponse>>

    // ── Recordings ──────────────────────────────────────────────────────
    @GET("mobile/recordings")
    suspend fun getRecordings(
        @Query("camera_id") cameraId: String? = null,
        @Query("from") fromDate: String? = null,
        @Query("to") toDate: String? = null,
        @Query("limit") limit: Int = 20,
        @Query("cursor") cursor: String? = null
    ): Response<ApiResponse<RecordingListResponse>>

    @GET("mobile/recordings/{id}")
    suspend fun getRecordingDetail(
        @Path("id") id: String
    ): Response<ApiResponse<RecordingDetailResponse>>

    @Streaming
    @GET("recordings/{id}/stream")
    suspend fun downloadRecording(
        @Path("id") id: String
    ): Response<ResponseBody>

    @Streaming
    @Headers("Accept: video/mp4")
    @GET("mobile/cameras/{id}/clip")
    suspend fun downloadRecordingClip(
        @Path("id") cameraId: String,
        @Query("from") from: String,
        @Query("to") to: String,
        @Query("download") download: Int = 1
    ): Response<ResponseBody>

    // ── Alerts ──────────────────────────────────────────────────────────
    @GET("mobile/alerts")
    suspend fun getAlerts(
        @Query("limit") limit: Int = 50,
        @Query("severity") severity: String? = null
    ): Response<ApiResponse<AlertListResponse>>

    @POST("mobile/alerts/{id}/read")
    suspend fun markAlertRead(
        @Path("id") id: String
    ): Response<ApiResponse<Map<String, Any>>>

    @POST("mobile/alerts/read-all")
    suspend fun markAllAlertsRead(): Response<ApiResponse<Map<String, Any>>>

    @GET("mobile/alerts/{id}")
    suspend fun getAlertDetail(
        @Path("id") id: String
    ): Response<ApiResponse<AlertDetailResponse>>

    @Streaming
    @GET("mobile/alerts/{id}/snapshot")
    suspend fun getAlertSnapshot(
        @Path("id") id: String
    ): Response<ResponseBody>

    @Streaming
    @GET("mobile/alerts/{id}/clip")
    suspend fun getAlertClip(
        @Path("id") id: String,
        @Query("download") download: Int? = null
    ): Response<ResponseBody>

    @POST("mobile/me/push-tokens")
    suspend fun registerPushToken(
        @Body request: PushTokenRequest
    ): Response<ApiResponse<Map<String, Any>>>

    @DELETE("mobile/me/push-tokens/{installationId}")
    suspend fun deletePushToken(
        @Path("installationId") installationId: String
    ): Response<ResponseBody>

    @GET("mobile/alerts/settings")
    suspend fun getAlertSettings(): Response<ApiResponse<AlertSettingsResponse>>

    @PUT("mobile/alerts/settings")
    suspend fun saveAlertSettings(
        @Body request: AlertSettingsRequest
    ): Response<ApiResponse<AlertSettingsResponse>>

    // ── Emergency ───────────────────────────────────────────────────────
    @GET("mobile/emergency-contacts")
    suspend fun getEmergencyContacts(): Response<ApiResponse<EmergencyContactsResponse>>

    @POST("mobile/emergency/trigger-sos")
    suspend fun triggerSos(
        @Body request: TriggerSosRequest
    ): Response<ApiResponse<Map<String, String>>>

    // ── Profile & Subscriptions (via /me) ──────────────────────────────
    @GET("mobile/me")
    suspend fun getMe(): Response<ApiResponse<MeResponse>>

    // ── Help & Terms ────────────────────────────────────────────────────
    @GET("mobile/help")
    suspend fun getHelpArticles(): Response<ApiResponse<HelpArticlesResponse>>

    @GET("mobile/terms")
    suspend fun getTerms(): Response<ApiResponse<TermsResponse>>
}
