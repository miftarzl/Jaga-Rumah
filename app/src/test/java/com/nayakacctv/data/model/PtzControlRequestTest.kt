package com.nayakacctv.data.model

import com.google.gson.Gson
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Test

class PtzControlRequestTest {

    @Test
    fun serializesPtzCommandUsingBackendFieldsAndDefaults() {
        val json = JsonParser.parseString(
            Gson().toJson(PtzControlRequest(PtzDirection.LEFT))
        ).asJsonObject

        assertEquals("left", json.get("direction").asString)
        assertEquals(0.5, json.get("speed").asDouble, 0.001)
        assertEquals(400, json.get("duration_ms").asInt)
    }

    @Test
    fun serializesContinuousJoystickMoveAndStopDuration() {
        val json = JsonParser.parseString(
            Gson().toJson(
                PtzControlRequest(
                    direction = PtzDirection.RIGHT,
                    speed = 0.8f,
                    durationMs = 0
                )
            )
        ).asJsonObject

        assertEquals("right", json.get("direction").asString)
        assertEquals(0.8, json.get("speed").asDouble, 0.001)
        assertEquals(0, json.get("duration_ms").asInt)
    }
}
