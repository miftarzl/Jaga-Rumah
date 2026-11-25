package com.nayakacctv.data.model

import com.google.gson.Gson
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Test

class CameraSirenRequestTest {
    @Test
    fun serializesDefaultDurationUsingBackendField() {
        val json = JsonParser.parseString(
            Gson().toJson(CameraSirenRequest())
        ).asJsonObject

        assertEquals(8, json.get("duration_seconds").asInt)
    }
}
