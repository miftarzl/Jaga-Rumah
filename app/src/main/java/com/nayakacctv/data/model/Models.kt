package com.nayakacctv.data.model

import com.google.gson.annotations.SerializedName

// ── Generic API Envelopes ──────────────────────────────────────────────
data class ApiResponse<T>(
    @SerializedName("data") val data: T?,
    @SerializedName("meta") val meta: Meta? = null
)

data class Meta(
    @SerializedName("request_id") val requestId: String? = null,
    @SerializedName("pagination") val pagination: Pagination? = null
)

data class Pagination(
    @SerializedName("next_cursor") val nextCursor: String? = null,
    @SerializedName("has_more") val hasMore: Boolean = false,
    @SerializedName("limit") val limit: Int = 20
)

data class ApiErrorResponse(
    @SerializedName("error") val error: ApiErrorDetail? = null
)

data class ApiErrorDetail(
    @SerializedName("code") val code: String? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("details") val details: Map<String, Any>? = null,
    @SerializedName("request_id") val requestId: String? = null
)

// ── Customer / User ────────────────────────────────────────────────────
data class Customer(
    @SerializedName("id") val id: String,
    @SerializedName("full_name") val fullName: String,
    @SerializedName("email") val email: String,
    @SerializedName("avatar_url") val avatarUrl: String? = null,
    @SerializedName("status") val status: String? = "active"
)

// ── Auth Tokens ────────────────────────────────────────────────────────
data class AuthTokens(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("refresh_token") val refreshToken: String,
    @SerializedName("token_type") val tokenType: String = "Bearer",
    @SerializedName("expires_in") val expiresIn: Long = 900,
    @SerializedName("customer") val customer: Customer? = null
)

// ── Camera ─────────────────────────────────────────────────────────────
data class Camera(
    @SerializedName("id") val id: String = "",
    @SerializedName("name") val name: String = "",
    @SerializedName("serial_number") val serialNumber: String = "",
    @SerializedName("model") val model: String = "",
    @SerializedName("location") val location: String = "",
    @SerializedName("ip") val ip: String = "",
    @SerializedName("status") val rawStatus: String = "offline", // active, online, offline, recording, streaming, unknown
    @SerializedName("recording_status") val recordingStatus: String = "idle",
    @SerializedName("thumbnail_url") val thumbnailUrl: String? = null,
    @SerializedName("stream_url") val streamUrl: String = "",
    @SerializedName("thumbnail_expires_at") val thumbnailExpiresAt: String? = null,
    @SerializedName("last_seen_at") val lastSeenAt: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("type") val type: String = "indoor",
    @SerializedName("resolution") val resolution: String = "1080p",
    @SerializedName("fov") val fov: Int = 110,
    @SerializedName("battery") val battery: Int? = null,
    @SerializedName("is_recording") val isRecordingField: Boolean? = null,
    @SerializedName("has_alert") val hasAlert: Boolean = false
) {
    val status: String
        get() = when (rawStatus.lowercase()) {
            "active", "online", "streaming" -> "online"
            "recording" -> "recording"
            else -> "offline"
        }

    val isRecording: Boolean
        get() = isRecordingField ?: (recordingStatus.equals("recording", ignoreCase = true) || rawStatus.equals("recording", ignoreCase = true))
}

data class CustomerCameraEdit(
    val id: String,
    val name: String,
    val location: String,
    val streamUrl: String
)

data class CameraSettings(
    @SerializedName("motion_detection") val motionDetection: Boolean = true,
    @SerializedName("notification_enabled") val notificationEnabled: Boolean = true
)

data class CameraListResponse(
    @SerializedName("cameras") val cameras: List<Camera> = emptyList(),
    @SerializedName("pagination") val pagination: Pagination? = null
)

data class CameraDetailResponse(
    @SerializedName("camera") val camera: Camera? = null
)

data class CameraStream(
    @SerializedName("hls_url") val hlsUrl: String? = null,
    @SerializedName("rtsp_url") val rtspUrl: String? = null,
    @SerializedName("webrtc_url") val webRtcUrl: String? = null,
    @SerializedName("stream_url") val streamUrl: String? = null,
    @SerializedName("mjpeg_url") val mjpegUrl: String? = null
) {
    val playbackUrl: String?
        get() = hlsUrl?.trim()?.takeIf(String::isNotBlank)
            ?: streamUrl?.trim()?.takeIf(String::isNotBlank)
            ?: mjpegUrl?.trim()?.takeIf(String::isNotBlank)
            ?: rtspUrl?.trim()?.takeIf(String::isNotBlank)
}

// ── Recording ──────────────────────────────────────────────────────────
data class Recording(
    @SerializedName("id") val id: String = "",
    @SerializedName("camera_id") val cameraId: String = "",
    @SerializedName("device_id") val deviceId: String? = null,
    @SerializedName("camera_name") val cameraName: String = "",
    @SerializedName("title") val title: String = "",
    @SerializedName("started_at") val startedAt: String = "",
    @SerializedName("ended_at") val endedAt: String? = null,
    @SerializedName("duration_seconds") val durationSeconds: Long? = null,
    @SerializedName("duration") val duration: Long = 0,
    @SerializedName("size_bytes") val sizeBytes: Long = 0,
    @SerializedName("file_size_bytes") val fileSizeBytes: Long? = null,
    @SerializedName("status") val status: String = "available",
    @SerializedName("has_motion") val hasMotion: Boolean = false,
    @SerializedName("thumbnail_url") val thumbnailUrl: String? = null,
    @SerializedName("playback_url") val playbackUrl: String? = null
)

data class RecordingListResponse(
    @SerializedName("recordings") val recordings: List<Recording> = emptyList(),
    @SerializedName("pagination") val pagination: Pagination? = null
)

data class RecordingDetailResponse(
    @SerializedName("recording") val recording: Recording? = null
)

// ── Alert ───────────────────────────────────────────────────────────────
data class AlertItem(
    @SerializedName("id") val id: String = "",
    @SerializedName("camera_id") val cameraId: String = "",
    @SerializedName("device_id") val deviceId: String? = null,
    @SerializedName("camera_name") val cameraName: String = "",
    @SerializedName("type") val type: String = "motion",
    @SerializedName("severity") val severity: String = "info",
    @SerializedName("title") val title: String = "",
    @SerializedName("message") val message: String = "",
    @SerializedName("occurred_at") val occurredAt: String? = null,
    @SerializedName("time") val time: String = "",
    @SerializedName("is_read") val isRead: Boolean? = null,
    @SerializedName("read") val read: Boolean = false
)

data class AlertListResponse(
    @SerializedName("alerts") val alerts: List<AlertItem> = emptyList(),
    @SerializedName("pagination") val pagination: Pagination? = null
)

data class AlertDetailResponse(
    @SerializedName("alert") val alert: AlertItem? = null
)

// ── Dashboard Summary ──────────────────────────────────────────────────
data class CameraSummary(
    @SerializedName("total_count") val totalCount: Int = 0,
    @SerializedName("active_count") val activeCount: Int = 0,
    @SerializedName("recording_count") val recordingCount: Int = 0,
    @SerializedName("offline_count") val offlineCount: Int = 0,
    @SerializedName("unknown_count") val unknownCount: Int = 0
)

data class AlertSummary(
    @SerializedName("unread_count") val unreadCount: Int = 0
)

data class DashboardSummary(
    @SerializedName("camera_summary") val cameraSummary: CameraSummary? = null,
    @SerializedName("alert_summary") val alertSummary: AlertSummary? = null,
    @SerializedName("active_cameras") val activeCameras: CameraListResponse? = null,
    @SerializedName("recent_alerts") val alertsList: List<AlertItem>? = null,

    // Backward compatibility fallback fields
    @SerializedName("total_cameras") val fallbackTotal: Int? = null,
    @SerializedName("online_cameras") val fallbackOnline: Int? = null,
    @SerializedName("recording_cameras") val fallbackRecording: Int? = null,
    @SerializedName("offline_cameras") val fallbackOffline: Int? = null,
    @SerializedName("unread_alerts") val fallbackUnread: Int? = null,
    @SerializedName("cameras") val fallbackCameras: List<Camera>? = null
) {
    val totalCameras: Int
        get() = cameraSummary?.totalCount ?: fallbackTotal ?: cameras.size

    val onlineCameras: Int
        get() = cameraSummary?.activeCount ?: fallbackOnline ?: cameras.count { it.status == "online" }

    val recordingCameras: Int
        get() = cameraSummary?.recordingCount ?: fallbackRecording ?: cameras.count { it.isRecording }

    val offlineCameras: Int
        get() = cameraSummary?.offlineCount ?: fallbackOffline ?: (totalCameras - onlineCameras).coerceAtLeast(0)

    val unreadAlerts: Int
        get() = alertSummary?.unreadCount ?: fallbackUnread ?: 0

    val cameras: List<Camera>
        get() = activeCameras?.cameras ?: fallbackCameras ?: emptyList()

    val recentAlerts: List<AlertItem>
        get() = alertsList ?: emptyList()
}

// ── Emergency Contact ──────────────────────────────────────────────────
data class EmergencyContact(
    @SerializedName("id") val id: String = "",
    @SerializedName("name") val name: String = "",
    @SerializedName("phone") val phone: String = "",
    @SerializedName("description") val description: String? = null,
    @SerializedName("category") val category: String? = "police"
)

data class EmergencyContactsResponse(
    @SerializedName("contacts") val contacts: List<EmergencyContact> = emptyList()
)

// ── Subscription ───────────────────────────────────────────────────────
data class Subscription(
    @SerializedName("id") val id: String = "",
    @SerializedName("plan_name") val planName: String = "Standar Cloud",
    @SerializedName("status") val status: String = "active",
    @SerializedName("starts_at") val startsAt: String? = null,
    @SerializedName("expires_at") val expiresAt: String? = null,
    @SerializedName("price") val price: Long = 75000,
    @SerializedName("storage_days") val storageDays: Int = 14
)

data class MeResponse(
    @SerializedName("profile") val profile: Customer? = null,
    @SerializedName("subscription") val subscription: Subscription? = null
)

// ── Help Article & Terms ───────────────────────────────────────────────
data class HelpArticle(
    @SerializedName("id") val id: String = "",
    @SerializedName("title") val title: String = "",
    @SerializedName("summary") val summary: String = "",
    @SerializedName("content") val content: String? = null
)

data class HelpArticlesResponse(
    @SerializedName("articles") val articles: List<HelpArticle> = emptyList(),
    @SerializedName("pagination") val pagination: Pagination? = null
)

data class TermsDoc(
    @SerializedName("title") val title: String = "Syarat & Ketentuan Layanan",
    @SerializedName("updated_at") val updatedAt: String = "2026-01-01",
    @SerializedName("content") val content: String = ""
)

data class TermsResponse(
    @SerializedName("document") val document: TermsDoc? = null
)
