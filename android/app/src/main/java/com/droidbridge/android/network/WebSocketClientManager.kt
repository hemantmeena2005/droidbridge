package com.droidbridge.android.network

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.droidbridge.android.model.*
import com.droidbridge.android.security.FingerprintTrustManager
import com.droidbridge.android.security.SecureKeyStorage
import com.google.gson.Gson
import okhttp3.*
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import kotlin.math.min
import kotlin.random.Random

/**
 * Manages WebSocket client lifecycle, TLS pinning, heartbeats, and bounded exponential backoff reconnection.
 */
class WebSocketClientManager private constructor(private val context: Context) {

    private val gson = Gson()
    private val storage = SecureKeyStorage(context)
    private var webSocket: WebSocket? = null
    private var isConnected = false
    private val handler = Handler(Looper.getMainLooper())

    private var retryAttempt = 0
    private var isReconnecting = false

    private val listeners = mutableListOf<ConnectionListener>()

    interface ConnectionListener {
        fun onStateChanged(connected: Boolean, status: String)
        fun onPairingResult(success: Boolean, message: String)
    }

    companion object {
        private const val TAG = "DroidBridgeWs"
        private const val BASE_RETRY_DELAY_MS = 2000L
        private const val MAX_RETRY_DELAY_MS = 30000L
        private const val HEARTBEAT_INTERVAL_MS = 25000L

        @Volatile
        private var INSTANCE: WebSocketClientManager? = null

        fun getInstance(context: Context): WebSocketClientManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: WebSocketClientManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val heartbeatRunnable = object : Runnable {
        override fun run() {
            if (isConnected && webSocket != null) {
                val ping = HeartbeatMessage()
                webSocket?.send(gson.toJson(ping))
                handler.postDelayed(this, HEARTBEAT_INTERVAL_MS)
            }
        }
    }

    fun addListener(listener: ConnectionListener) {
        listeners.add(listener)
        listener.onStateChanged(isConnected, if (isConnected) "Connected" else "Disconnected")
    }

    fun removeListener(listener: ConnectionListener) {
        listeners.remove(listener)
    }

    private fun notifyState(connected: Boolean, status: String) {
        isConnected = connected
        handler.post {
            listeners.forEach { it.onStateChanged(connected, status) }
        }
    }

    /**
     * Start pairing flow using scanned QR data
     */
    fun startPairing(qrData: QrPairingData, deviceName: String, callback: (Boolean, String) -> Unit) {
        val now = System.currentTimeMillis()
        if (now > qrData.expiresAt) {
            callback(false, "Pairing QR code has expired. Please regenerate on Mac.")
            return
        }

        try {
            val trustManager = FingerprintTrustManager(qrData.fingerprint)
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, arrayOf<TrustManager>(trustManager), SecureRandom())

            val client = OkHttpClient.Builder()
                .sslSocketFactory(sslContext.socketFactory, trustManager)
                .hostnameVerifier { _, _ -> true } // Fingerprint pinned in trustManager
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .build()

            val url = "wss://${qrData.ip}:${qrData.port}/ws"
            val request = Request.Builder().url(url).build()

            notifyState(false, "Connecting to Mac for pairing...")

            client.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(ws: WebSocket, response: Response) {
                    Log.i(TAG, "Pairing socket opened. Sending challenge response...")
                    val deviceId = android.provider.Settings.Secure.getString(
                        context.contentResolver,
                        android.provider.Settings.Secure.ANDROID_ID
                    ) ?: "unknown_device"

                    // Compute HMAC challenge response using shared challenge as key
                    val challengeResponse = storage.computeChallengeResponse(qrData.challenge, qrData.challenge)

                    val pairReq = PairRequestMessage(
                        deviceId = deviceId,
                        deviceName = deviceName,
                        challengeResponse = challengeResponse
                    )
                    ws.send(gson.toJson(pairReq))
                }

                override fun onMessage(ws: WebSocket, text: String) {
                    try {
                        val base = gson.fromJson(text, ProtocolMessage::class.java)
                        if (base.type == "pair_confirm") {
                            val confirm = gson.fromJson(text, PairConfirmMessage::class.java)
                            if (confirm.success && confirm.authToken != null) {
                                storage.savePairing(
                                    host = qrData.ip,
                                    port = qrData.port,
                                    macName = confirm.macDeviceName,
                                    fingerprint = qrData.fingerprint,
                                    authToken = confirm.authToken
                                )
                                notifyState(true, "Paired and connected to ${confirm.macDeviceName}")
                                handler.post { callback(true, "Pairing successful!") }
                                webSocket = ws
                                startHeartbeat()
                            } else {
                                ws.close(1000, "Pairing rejected")
                                notifyState(false, "Pairing rejected: ${confirm.errorReason}")
                                handler.post { callback(false, confirm.errorReason ?: "Pairing failed") }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed parsing pairing message: ${e.message}")
                    }
                }

                override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                    notifyState(false, "Connection error: ${t.message}")
                    handler.post { callback(false, "Connection error: ${t.message}") }
                }

                override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                    notifyState(false, "Disconnected")
                }
            })
        } catch (e: Exception) {
            callback(false, "Setup failed: ${e.message}")
        }
    }

    /**
     * Connect to paired Mac using stored credentials
     */
    fun connect() {
        if (!storage.isPaired()) {
            notifyState(false, "Not paired")
            return
        }

        val host = storage.getMacHost() ?: return
        val port = storage.getMacPort()
        val fingerprint = storage.getMacFingerprint() ?: return
        val token = storage.getAuthToken() ?: return

        try {
            val trustManager = FingerprintTrustManager(fingerprint)
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, arrayOf<TrustManager>(trustManager), SecureRandom())

            val client = OkHttpClient.Builder()
                .sslSocketFactory(sslContext.socketFactory, trustManager)
                .hostnameVerifier { _, _ -> true }
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .build()

            val url = "wss://$host:$port/ws"
            val request = Request.Builder().url(url).build()

            notifyState(false, "Connecting...")

            client.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(ws: WebSocket, response: Response) {
                    webSocket = ws
                    retryAttempt = 0
                    notifyState(true, "Connected to ${storage.getMacName()}")
                    startHeartbeat()

                    // Send hello with authProof
                    val deviceId = android.provider.Settings.Secure.getString(
                        context.contentResolver,
                        android.provider.Settings.Secure.ANDROID_ID
                    ) ?: "unknown_device"

                    val hello = HelloMessage(
                        deviceId = deviceId,
                        deviceName = android.os.Build.MODEL,
                        authProof = token
                    )
                    ws.send(gson.toJson(hello))
                }

                override fun onMessage(ws: WebSocket, text: String) {
                    // Handle messages from Mac (e.g., heartbeat, disconnect)
                    try {
                        val base = gson.fromJson(text, ProtocolMessage::class.java)
                        if (base.type == "disconnect") {
                            disconnect()
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Malformed message received from Mac")
                    }
                }

                override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                    notifyState(false, "Disconnected (${t.message})")
                    stopHeartbeat()
                    scheduleReconnect()
                }

                override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                    notifyState(false, "Disconnected")
                    stopHeartbeat()
                    if (code != 1000) {
                        scheduleReconnect()
                    }
                }
            })
        } catch (e: Exception) {
            notifyState(false, "Connection error: ${e.message}")
            scheduleReconnect()
        }
    }

    private fun scheduleReconnect() {
        if (!storage.isPaired() || isReconnecting) return
        isReconnecting = true
        retryAttempt++

        // Bounded exponential backoff with jitter
        val baseDelay = min(BASE_RETRY_DELAY_MS * (1L shl min(retryAttempt, 5)), MAX_RETRY_DELAY_MS)
        val jitter = Random.nextLong(0, 1000)
        val delay = baseDelay + jitter

        notifyState(false, "Reconnecting in ${delay / 1000}s (attempt $retryAttempt)...")

        handler.postDelayed({
            isReconnecting = false
            connect()
        }, delay)
    }

    private fun startHeartbeat() {
        handler.removeCallbacks(heartbeatRunnable)
        handler.postDelayed(heartbeatRunnable, HEARTBEAT_INTERVAL_MS)
    }

    private fun stopHeartbeat() {
        handler.removeCallbacks(heartbeatRunnable)
    }

    fun sendNotification(payload: NotificationPayload) {
        if (isConnected && webSocket != null) {
            webSocket?.send(gson.toJson(payload))
        }
    }

    fun disconnect() {
        stopHeartbeat()
        webSocket?.close(1000, "User disconnected")
        webSocket = null
        notifyState(false, "Disconnected")
    }

    fun unpair() {
        disconnect()
        storage.unpair()
        notifyState(false, "Unpaired")
    }
}
