package com.nayakacctv.data.model

import com.google.gson.annotations.SerializedName

data class LoginRequest(
    @SerializedName("email") val email: String,
    @SerializedName("password") val password: String,
    @SerializedName("installation_id") val installationId: String,
    @SerializedName("platform") val platform: String = "android",
    @SerializedName("app_version") val appVersion: String = "1.0.0"
)

data class RegisterRequest(
    @SerializedName("full_name") val fullName: String,
    @SerializedName("email") val email: String,
    @SerializedName("password") val password: String,
    @SerializedName("installation_id") val installationId: String,
    @SerializedName("platform") val platform: String = "android",
    @SerializedName("app_version") val appVersion: String = "1.0.0"
)

data class GoogleLoginRequest(
    @SerializedName("id_token") val idToken: String,
    @SerializedName("installation_id") val installationId: String,
    @SerializedName("platform") val platform: String = "android",
    @SerializedName("app_version") val appVersion: String = "1.0.0"
)

data class RefreshTokenRequest(
    @SerializedName("refresh_token") val refreshToken: String,
    @SerializedName("installation_id") val installationId: String
)

data class ForgotPasswordRequest(
    @SerializedName("email") val email: String
)

data class ResetPasswordRequest(
    @SerializedName("email") val email: String,
    @SerializedName("code") val code: String,
    @SerializedName("new_password") val newPassword: String
)

data class UpdateCameraSettingsRequest(
    @SerializedName("motion_detection") val motionDetection: Boolean,
    @SerializedName("notification_enabled") val notificationEnabled: Boolean
)

data class TriggerSosRequest(
    @SerializedName("latitude") val latitude: Double? = null,
    @SerializedName("longitude") val longitude: Double? = null,
    @SerializedName("message") val message: String = "Panggilan Darurat Dikirim dari Aplikasi Mobile"
)

data class ClaimDeviceRequest(
    @SerializedName("code") val code: String
)

data class ClaimDeviceResponse(
    @SerializedName("deviceId") val deviceId: String? = null,
    @SerializedName("deviceName") val deviceName: String? = null,
    @SerializedName("serialNumber") val serialNumber: String? = null,
    @SerializedName("model") val model: String? = null,
    @SerializedName("device_id") val deviceIdSnake: String? = null,
    @SerializedName("device_name") val deviceNameSnake: String? = null
) {
    val id: String get() = deviceId ?: deviceIdSnake ?: ""
    val name: String get() = deviceName ?: deviceNameSnake ?: "Kamera Baru"
}
