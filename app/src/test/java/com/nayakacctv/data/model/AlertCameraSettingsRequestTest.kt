package com.nayakacctv.data.model

import com.google.gson.Gson
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertCameraSettingsRequestTest {

    @Test
    fun serializesParkingZoneResetAsExplicitNull() {
        val request = AlertCameraSettingsRequest(
            cameraId = "camera-1",
            motionDetectionEnabled = true,
            personDetectionEnabled = true,
            vehicleDetectionEnabled = true,
            vehicleGuardMode = false,
            parkingZone = ParkingZonePayload(null),
            notificationsEnabled = true
        )

        val json = JsonParser.parseString(Gson().toJson(request)).asJsonObject

        assertTrue(json.has("parking_zone"))
        assertTrue(json.get("parking_zone").isJsonNull)
        assertEquals(true, json.get("vehicle_detection_enabled").asBoolean)
        assertEquals(false, json.get("vehicle_guard_mode").asBoolean)
    }

    @Test
    fun serializesNormalizedParkingZoneCoordinates() {
        val request = AlertCameraSettingsRequest(
            cameraId = "camera-1",
            motionDetectionEnabled = true,
            personDetectionEnabled = true,
            vehicleDetectionEnabled = true,
            vehicleGuardMode = true,
            parkingZone = ParkingZonePayload(ParkingZone(0.1f, 0.2f, 0.8f, 0.9f)),
            notificationsEnabled = true
        )

        val zone = JsonParser.parseString(Gson().toJson(request))
            .asJsonObject.getAsJsonObject("parking_zone")

        assertEquals(0.1, zone.get("x1").asDouble, 0.0001)
        assertEquals(0.2, zone.get("y1").asDouble, 0.0001)
        assertEquals(0.8, zone.get("x2").asDouble, 0.0001)
        assertEquals(0.9, zone.get("y2").asDouble, 0.0001)
    }
}
