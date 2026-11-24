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

enum class PtzDirection {
    @SerializedName("up") UP,
    @SerializedName("down") DOWN,
    @SerializedName("left") LEFT,
    @SerializedName("right") RIGHT,
    @SerializedName("zoom_in") ZOOM_IN,
    @SerializedName("zoom_out") ZOOM_OUT,
    @SerializedName("stop") STOP
}

data class PtzControlRequest(
    @SerializedName("direction") val direction: PtzDirection,
    @SerializedName("speed") val speed: Float = 0.5f,
    @SerializedName("duration_ms") val durationMs: Int = 400
)

const val MAX_CAMERA_TALK_AUDIO_BYTES = 5L * 1024L * 1024L

data class TriggerSosRequest(
    @SerializedName("latitude") val latitude: Double? = null,
    @SerializedName("longitude") val longitude: Double? = null,
    @SerializedName("message") val message: String = "Panggilan Darurat Dikirim dari Aplikasi Mobile"
)

data class PushTokenRequest(
    @SerializedName("installation_id") val installationId: String,
    @SerializedName("platform") val platform: String = "android",
    @SerializedName("token") val token: String,
    @SerializedName("permission") val permission: String = "granted",
    @SerializedName("app_version") val appVersion: String = "1.0.0"
)

data class AlertCameraSettingsRequest(
    @SerializedName("camera_id") val cameraId: String,
    @SerializedName("motion_detection_enabled") val motionDetectionEnabled: Boolean,
    @SerializedName("person_detection_enabled") val personDetectionEnabled: Boolean,
    @SerializedName("notifications_enabled") val notificationsEnabled: Boolean
)

data class AlertSleepModeRequest(
    @SerializedName("enabled") val enabled: Boolean,
    @SerializedName("start_time") val startTime: String,
    @SerializedName("end_time") val endTime: String,
    @SerializedName("active_days") val activeDays: List<String>,
    @SerializedName("camera_ids") val cameraIds: List<String>
)

data class AlertSettingsRequest(
    @SerializedName("cameras") val cameras: List<AlertCameraSettingsRequest>,
    @SerializedName("sleep_mode") val sleepMode: AlertSleepModeRequest
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
