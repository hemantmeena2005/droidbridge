package com.droidbridge.android.notifications

import android.app.Notification
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.droidbridge.android.model.NotificationPayload
import com.droidbridge.android.model.OtpInfo
import com.droidbridge.android.network.WebSocketClientManager
import com.droidbridge.android.otp.OtpDetector
import com.droidbridge.android.security.SecureKeyStorage

/**
 * Android NotificationListenerService implementation.
 * 
 * Inspects incoming notifications, extracts text extras, detects OTPs locally,
 * and passes approved messages to the WebSocketClientManager.
 * 
 * Never logs notification contents, sender names, or OTPs.
 */
class DroidBridgeNotificationService : NotificationListenerService() {

    private val otpDetector = OtpDetector()
    private lateinit var filter: NotificationFilter
    private lateinit var storage: SecureKeyStorage

    companion object {
        private const val TAG = "DroidBridgeNotifSvc"
        var isServiceConnected = false
            private set
    }

    override fun onCreate() {
        super.onCreate()
        filter = NotificationFilter(this)
        storage = SecureKeyStorage(this)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        isServiceConnected = true
        Log.i(TAG, "NotificationListenerService connected")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        isServiceConnected = false
        Log.i(TAG, "NotificationListenerService disconnected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return
        if (!storage.isPaired()) return

        val packageName = sbn.packageName ?: return
        val notification = sbn.notification ?: return

        // Ignore ongoing notifications like media playback or foreground service banners
        if ((notification.flags and Notification.FLAG_ONGOING_EVENT) != 0) {
            return
        }

        val extras = notification.extras ?: return

        // Extract title and text safely from extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim() ?: ""
        var text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim() ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim() ?: ""
        val textLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)

        var effectiveText = if (bigText.isNotEmpty()) bigText else text
        if (textLines != null && textLines.isNotEmpty()) {
            val joinedLines = textLines.joinToString("\n") { it.toString().trim() }
            if (joinedLines.isNotEmpty()) {
                effectiveText = "$effectiveText\n$joinedLines".trim()
            }
        }

        // Support Android Notification.MessagingStyle bundles
        val messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
        if (messages != null && messages.isNotEmpty()) {
            val extracted = messages.mapNotNull {
                (it as? android.os.Bundle)?.getCharSequence("text")?.toString()
            }.joinToString("\n")
            if (extracted.isNotEmpty()) {
                effectiveText = "$effectiveText\n$extracted".trim()
            }
        }

        if (title.isEmpty() && effectiveText.isEmpty()) {
            return
        }

        // Get friendly app name
        val appName = try {
            val pm = packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            packageName
        }

        // Run OTP detection on title + text
        val combinedContent = "$title $effectiveText"
        val otpResult = otpDetector.detectOtp(combinedContent)
        val hasOtp = otpResult != null

        // Apply forwarding filters
        if (!filter.shouldForward(packageName, hasOtp)) {
            return
        }

        val notificationId = "${sbn.id}_${sbn.postTime}"

        val otpInfo = if (otpResult != null) {
            OtpInfo(
                code = otpResult.code,
                confidence = otpResult.confidence.name.lowercase(),
                expiresInSeconds = otpResult.expiresInSeconds
            )
        } else null

        val payload = NotificationPayload(
            id = notificationId,
            packageName = packageName,
            appName = appName,
            title = title,
            text = effectiveText,
            postTime = sbn.postTime,
            hasOtp = hasOtp,
            otp = otpInfo
        )

        // Send via WebSocketClientManager
        WebSocketClientManager.getInstance(applicationContext).sendNotification(payload)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        // Handled if dismissal synchronization is desired
    }
}
