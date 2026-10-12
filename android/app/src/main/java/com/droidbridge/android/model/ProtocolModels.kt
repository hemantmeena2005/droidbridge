package com.droidbridge.android.model

import com.droidbridge.android.otp.OtpResult

data class ProtocolMessage(
    val version: Int = 1,
    val type: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class HelloMessage(
    val version: Int = 1,
    val type: String = "hello",
    val timestamp: Long = System.currentTimeMillis(),
    val deviceId: String,
    val deviceName: String,
    val authProof: String? = null
)

data class PairRequestMessage(
    val version: Int = 1,
    val type: String = "pair_request",
    val timestamp: Long = System.currentTimeMillis(),
    val deviceId: String,
    val deviceName: String,
    val challengeResponse: String,
    val clientPublicKey: String? = null
)

data class PairConfirmMessage(
    val version: Int = 1,
    val type: String = "pair_confirm",
    val timestamp: Long = System.currentTimeMillis(),
    val macDeviceId: String,
    val macDeviceName: String,
    val authToken: String? = null,
    val success: Boolean,
    val errorReason: String? = null
)

data class NotificationPayload(
    val version: Int = 1,
    val type: String = "notification",
    val timestamp: Long = System.currentTimeMillis(),
    val id: String,
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    val postTime: Long,
    val hasOtp: Boolean,
    val otp: OtpInfo? = null
)

data class OtpInfo(
    val code: String,
    val confidence: String,
    val expiresInSeconds: Int
)

data class OtpDetectedMessage(
    val version: Int = 1,
    val type: String = "otp_detected",
    val timestamp: Long = System.currentTimeMillis(),
    val notificationId: String,
    val appName: String,
    val code: String,
    val confidence: String,
    val expiresInSeconds: Int
)

data class HeartbeatMessage(
    val version: Int = 1,
    val type: String = "heartbeat",
    val timestamp: Long = System.currentTimeMillis(),
    val ping: Boolean = true
)

data class DisconnectMessage(
    val version: Int = 1,
    val type: String = "disconnect",
    val timestamp: Long = System.currentTimeMillis(),
    val reason: String
)

data class QrPairingData(
    val v: Int = 1,
    val ip: String,
    val port: Int,
    val fingerprint: String,
    val challenge: String,
    val macName: String,
    val expiresAt: Long
)
