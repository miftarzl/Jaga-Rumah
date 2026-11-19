package com.nayakacctv.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CameraStreamTest {
    @Test
    fun playbackUrlUsesHlsAndIgnoresRtsp() {
        assertEquals(
            "https://stream.example/camera/index.m3u8",
            CameraStream(
                hlsUrl = "https://stream.example/camera/index.m3u8",
                rtspUrl = "rtsp://stream.example/camera"
            ).playbackUrl
        )
        assertNull(CameraStream(rtspUrl = "rtsp://stream.example/camera").playbackUrl)
        assertNull(CameraStream(webRtcUrl = "https://stream.example/whep").playbackUrl)
    }
}
