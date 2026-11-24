package com.nayakacctv.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.SurfaceView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.nayakacctv.data.local.TokenManager
import com.nayakacctv.ui.theme.PrimaryBlue
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.webrtc.AudioTrack
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.EglBase
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.MediaStreamTrack
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@Composable
internal fun WebRtcCameraStreamPlayer(
    streamUrl: String,
    modifier: Modifier = Modifier,
    isMuted: Boolean,
    controller: CameraStreamController?,
    onStateChange: (String) -> Unit,
    onError: (String) -> Unit
) {
    val context = LocalContext.current
    val eglBase = remember { EglBase.create() }
    val currentOnStateChange by rememberUpdatedState(onStateChange)
    val currentOnError by rememberUpdatedState(onError)
    var renderer by remember { mutableStateOf<SurfaceViewRenderer?>(null) }
    var session by remember { mutableStateOf<WebRtcWhepSession?>(null) }
    var retryCount by remember(streamUrl) { mutableStateOf(0) }
    var errorMessage by remember(streamUrl, retryCount) { mutableStateOf<String?>(null) }
    var isPlaying by remember(streamUrl, retryCount) { mutableStateOf(false) }

    AndroidView(
        modifier = modifier.background(Color.Black),
        factory = { viewContext ->
            SurfaceViewRenderer(viewContext).apply {
                init(eglBase.eglBaseContext, null)
                setEnableHardwareScaler(true)
                setMirror(false)
                renderer = this
            }
        },
        update = {
            renderer = it
        }
    )

    DisposableEffect(controller, renderer) {
        val surfaceView = renderer
        if (controller != null && surfaceView != null) {
            controller.captureFrameCallback = { onCaptured ->
                if (surfaceView.width > 0 && surfaceView.height > 0) {
                    val bitmap = Bitmap.createBitmap(
                        surfaceView.width,
                        surfaceView.height,
                        Bitmap.Config.ARGB_8888
                    )
                    PixelCopy.request(
                        surfaceView,
                        bitmap,
                        { result ->
                            onCaptured(if (result == PixelCopy.SUCCESS) bitmap else null)
                        },
                        Handler(Looper.getMainLooper())
                    )
                } else {
                    onCaptured(null)
                }
            }
        }
        onDispose {
            if (controller?.captureFrameCallback != null) {
                controller.captureFrameCallback = null
            }
        }
    }

    DisposableEffect(streamUrl, renderer, retryCount) {
        val surfaceView = renderer
        if (surfaceView == null) {
            onDispose {}
        } else {
            errorMessage = null
            isPlaying = false
            currentOnStateChange("connecting")
            val newSession = WebRtcWhepSession(
                context = context,
                streamUrl = streamUrl,
                renderer = surfaceView,
                eglBase = eglBase,
                onConnected = {
                    isPlaying = true
                    currentOnStateChange("webrtc_playing")
                },
                onFailure = { message ->
                    errorMessage = message
                    currentOnStateChange("error")
                    currentOnError(message)
                }
            )
            session = newSession
            newSession.start()
            onDispose {
                if (session === newSession) session = null
                newSession.release()
            }
        }
    }

    SideEffect {
        session?.setMuted(isMuted)
    }

    DisposableEffect(eglBase) {
        onDispose {
            renderer?.release()
            eglBase.release()
        }
    }

    if (errorMessage == null && !isPlaying) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = PrimaryBlue)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Menghubungkan WebRTC...", color = Color.White, fontSize = 12.sp)
            }
        }
    }

    errorMessage?.let { message ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x99000000))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "WebRTC gagal terhubung",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(message, color = Color.LightGray, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = { retryCount++ }) {
                    Text("Coba lagi")
                }
            }
        }
    }
}

private class WebRtcWhepSession(
    context: Context,
    private val streamUrl: String,
    private val renderer: SurfaceViewRenderer,
    eglBase: EglBase,
    private val onConnected: () -> Unit,
    private val onFailure: (String) -> Unit
) {
    private val applicationContext = context.applicationContext
    private val eglContext = eglBase.eglBaseContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val networkExecutor = Executors.newSingleThreadExecutor()
    private val timeoutExecutor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private val released = AtomicBoolean(false)
    private val offerSent = AtomicBoolean(false)
    private val failureReported = AtomicBoolean(false)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    @Volatile private var peerConnectionFactory: PeerConnectionFactory? = null
    @Volatile private var peerConnection: PeerConnection? = null
    @Volatile private var remoteVideoTrack: VideoTrack? = null
    @Volatile private var muted = false

    fun start() {
        ensureWebRtcInitialized(applicationContext)
        timeoutExecutor.schedule({
            if (!offerSent.get()) {
                fail("ICE belum selesai dalam 15 detik.")
            }
        }, 15, TimeUnit.SECONDS)
        networkExecutor.execute {
            if (released.get()) return@execute
            createPeerConnection()
        }
    }

    fun setMuted(muted: Boolean) {
        this.muted = muted
        peerConnection?.receivers?.forEach { receiver ->
            (receiver.track() as? AudioTrack)?.setEnabled(!muted)
        }
    }

    private fun createPeerConnection() {
        try {
            val factory = PeerConnectionFactory.builder()
                .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglContext))
                .createPeerConnectionFactory()
            peerConnectionFactory = factory

            val configuration = PeerConnection.RTCConfiguration(emptyList()).apply {
                sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            }
            val peer = factory.createPeerConnection(configuration, createObserver())
                ?: throw IOException("Tidak dapat membuat koneksi WebRTC.")
            peerConnection = peer
            peer.addTransceiver(
                MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO,
                RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY)
            )
            peer.addTransceiver(
                MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO,
                RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY)
            )
            peer.createOffer(object : SdpObserver {
                override fun onCreateSuccess(description: SessionDescription?) {
                    if (description == null || released.get()) {
                        fail("WebRTC tidak menghasilkan SDP offer.")
                        return
                    }
                    peer.setLocalDescription(object : SdpObserver {
                        override fun onSetSuccess() = Unit
                        override fun onSetFailure(error: String?) {
                            fail(error ?: "Gagal menyiapkan SDP offer.")
                        }
                        override fun onCreateSuccess(description: SessionDescription?) = Unit
                        override fun onCreateFailure(error: String?) = Unit
                    }, description)
                }

                override fun onSetSuccess() = Unit
                override fun onCreateFailure(error: String?) {
                    fail(error ?: "Gagal membuat SDP offer.")
                }
                override fun onSetFailure(error: String?) = Unit
            }, MediaConstraints())
        } catch (error: Exception) {
            fail(error.localizedMessage ?: "Gagal menyiapkan WebRTC.")
        }
    }

    private fun createObserver() = object : PeerConnection.Observer {
        override fun onSignalingChange(state: PeerConnection.SignalingState) = Unit

        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
            when (state) {
                PeerConnection.IceConnectionState.CONNECTED,
                PeerConnection.IceConnectionState.COMPLETED -> mainHandler.post {
                    if (!released.get()) onConnected()
                }
                PeerConnection.IceConnectionState.FAILED -> fail("Negosiasi ICE gagal.")
                else -> Unit
            }
        }

        override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit

        override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {
            if (state == PeerConnection.IceGatheringState.COMPLETE) {
                postOffer()
            }
        }

        override fun onIceCandidate(candidate: org.webrtc.IceCandidate) = Unit
        override fun onIceCandidatesRemoved(candidates: Array<out org.webrtc.IceCandidate>) = Unit
        override fun onAddStream(stream: MediaStream) {
            stream.videoTracks.forEach(::attachVideoTrack)
            stream.audioTracks.forEach { it.setEnabled(!muted) }
        }
        override fun onRemoveStream(stream: MediaStream) {
            stream.videoTracks.forEach { it.removeSink(renderer) }
        }
        override fun onDataChannel(dataChannel: org.webrtc.DataChannel) = Unit
        override fun onRenegotiationNeeded() = Unit
        override fun onAddTrack(receiver: RtpReceiver, mediaStreams: Array<out MediaStream>) {
            when (val track = receiver.track()) {
                is VideoTrack -> attachVideoTrack(track)
                is AudioTrack -> track.setEnabled(!muted)
            }
        }
        override fun onTrack(transceiver: RtpTransceiver) {
            when (val track = transceiver.receiver.track()) {
                is VideoTrack -> attachVideoTrack(track)
                is AudioTrack -> track.setEnabled(!muted)
            }
        }
    }

    private fun attachVideoTrack(track: VideoTrack) {
        remoteVideoTrack?.removeSink(renderer)
        remoteVideoTrack = track
        track.addSink(renderer)
    }

    private fun postOffer() {
        if (!offerSent.compareAndSet(false, true) || released.get()) return
        val localDescription = peerConnection?.localDescription
        if (localDescription == null) {
            fail("SDP lokal belum siap untuk dikirim.")
            return
        }
        networkExecutor.execute {
            try {
                val token = TokenManager(applicationContext).getAccessToken()
                val requestBuilder = Request.Builder()
                    .url(streamUrl)
                    .header("Accept", "application/sdp")
                    .post(localDescription.description.toRequestBody("application/sdp".toMediaType()))
                if (!token.isNullOrBlank()) {
                    requestBuilder.header("Authorization", "Bearer $token")
                }
                httpClient.newCall(requestBuilder.build()).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("Server WHEP merespons HTTP ${response.code}.")
                    }
                    val answer = response.body?.string()?.takeIf(String::isNotBlank)
                        ?: throw IOException("SDP answer kosong dari server WHEP.")
                    val peer = peerConnection ?: return@use
                    peer.setRemoteDescription(object : SdpObserver {
                        override fun onSetSuccess() = Unit
                        override fun onSetFailure(error: String?) {
                            fail(error ?: "Gagal menerapkan SDP answer.")
                        }
                        override fun onCreateSuccess(description: SessionDescription?) = Unit
                        override fun onCreateFailure(error: String?) = Unit
                    }, SessionDescription(SessionDescription.Type.ANSWER, answer))
                }
            } catch (error: Exception) {
                fail(error.localizedMessage ?: "Gagal menghubungi server WHEP.")
            }
        }
    }

    private fun fail(message: String) {
        if (released.get() || !failureReported.compareAndSet(false, true)) return
        mainHandler.post {
            if (!released.get()) onFailure(message)
        }
    }

    fun release() {
        if (!released.compareAndSet(false, true)) return
        remoteVideoTrack?.removeSink(renderer)
        peerConnection?.close()
        peerConnection?.dispose()
        peerConnection = null
        peerConnectionFactory?.dispose()
        peerConnectionFactory = null
        httpClient.dispatcher.cancelAll()
        httpClient.connectionPool.evictAll()
        networkExecutor.shutdownNow()
        timeoutExecutor.shutdownNow()
    }

    companion object {
        @Volatile private var initialized = false

        private fun ensureWebRtcInitialized(context: Context) {
            if (initialized) return
            synchronized(this) {
                if (initialized) return
                PeerConnectionFactory.initialize(
                    PeerConnectionFactory.InitializationOptions.builder(context)
                        .createInitializationOptions()
                )
                initialized = true
            }
        }
    }
}
