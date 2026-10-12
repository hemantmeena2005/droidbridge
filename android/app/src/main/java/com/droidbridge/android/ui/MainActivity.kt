package com.droidbridge.android.ui

import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.widget.Button
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.droidbridge.android.R
import com.droidbridge.android.model.QrPairingData
import com.droidbridge.android.network.WebSocketClientManager
import com.droidbridge.android.notifications.DroidBridgeNotificationService
import com.droidbridge.android.notifications.NotificationFilter
import com.droidbridge.android.otp.OtpDetector
import com.droidbridge.android.security.SecureKeyStorage
import com.google.gson.Gson
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

class MainActivity : AppCompatActivity(), WebSocketClientManager.ConnectionListener {

    private lateinit var statusText: TextView
    private lateinit var pairedMacText: TextView
    private lateinit var btnScanQr: Button
    private lateinit var btnConnect: Button
    private lateinit var btnUnpair: Button
    private lateinit var btnNotificationSettings: Button
    private lateinit var btnTestNotification: Button
    private lateinit var switchOtpOnly: Switch

    private lateinit var storage: SecureKeyStorage
    private lateinit var filter: NotificationFilter
    private lateinit var wsManager: WebSocketClientManager

    // QR scanner launcher
    private val barcodeLauncher = registerForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            handleScannedQr(result.contents)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        storage = SecureKeyStorage(this)
        filter = NotificationFilter(this)
        wsManager = WebSocketClientManager.getInstance(this)

        statusText = findViewById(R.id.tv_status)
        pairedMacText = findViewById(R.id.tv_paired_mac)
        btnScanQr = findViewById(R.id.btn_scan_qr)
        btnConnect = findViewById(R.id.btn_connect)
        btnUnpair = findViewById(R.id.btn_unpair)
        btnNotificationSettings = findViewById(R.id.btn_notification_settings)
        btnTestNotification = findViewById(R.id.btn_test_notification)
        switchOtpOnly = findViewById(R.id.switch_otp_only)

        switchOtpOnly.isChecked = filter.forwardOtpOnly
        switchOtpOnly.setOnCheckedChangeListener { _, isChecked ->
            filter.forwardOtpOnly = isChecked
        }

        btnScanQr.setOnClickListener {
            val options = ScanOptions().apply {
                setPrompt("Scan Mac pairing QR code")
                setBeepEnabled(false)
                setOrientationLocked(true)
            }
            barcodeLauncher.launch(options)
        }

        btnConnect.setOnClickListener {
            wsManager.connect()
        }

        btnUnpair.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Unpair Mac")
                .setMessage("Are you sure you want to unpair from this Mac?")
                .setPositiveButton("Unpair") { _, _ ->
                    wsManager.unpair()
                    updateUi()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        btnNotificationSettings.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        btnTestNotification = findViewById(R.id.btn_test_notification)
        btnTestNotification.setOnClickListener {
            triggerLocalTestOtp()
        }

        updateUi()
    }

    override fun onResume() {
        super.onResume()
        wsManager.addListener(this)
        updateUi()
    }

    override fun onPause() {
        super.onPause()
        wsManager.removeListener(this)
    }

    private fun updateUi() {
        val hasListenerPermission = isNotificationServiceEnabled()
        btnNotificationSettings.text = if (hasListenerPermission) {
            "Notification Access: Granted ✓"
        } else {
            "Grant Notification Access (Required)"
        }

        if (storage.isPaired()) {
            pairedMacText.text = "Paired with: ${storage.getMacName()}"
            btnConnect.isEnabled = true
            btnUnpair.isEnabled = true
            btnScanQr.text = "Re-pair with new Mac"
        } else {
            pairedMacText.text = "Not paired with any Mac"
            btnConnect.isEnabled = false
            btnUnpair.isEnabled = false
            btnScanQr.text = "Scan Pairing QR Code"
        }
    }

    private fun handleScannedQr(contents: String) {
        try {
            val qrData = Gson().fromJson(contents, QrPairingData::class.java)
            if (qrData.v != 1 || qrData.ip.isNullOrBlank() || qrData.challenge.isNullOrBlank()) {
                Toast.makeText(this, "Invalid QR code format", Toast.LENGTH_LONG).show()
                return
            }

            AlertDialog.Builder(this)
                .setTitle("Pair with ${qrData.macName}?")
                .setMessage("Fingerprint: ${qrData.fingerprint.take(16)}...\nTarget: ${qrData.ip}:${qrData.port}")
                .setPositiveButton("Confirm Pairing") { _, _ ->
                    val deviceName = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
                    wsManager.startPairing(qrData, deviceName) { success, msg ->
                        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                        updateUi()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to parse QR code", Toast.LENGTH_SHORT).show()
        }
    }

    private fun triggerLocalTestOtp() {
        val testDetector = OtpDetector()
        val sample = "Your DroidBridge verification code is 482731. Do not share."
        val res = testDetector.detectOtp(sample)
        if (res != null) {
            Toast.makeText(this, "Test OTP detected: ${res.code} (${res.confidence})", Toast.LENGTH_SHORT).show()
        }
    }

    private fun isNotificationServiceEnabled(): Boolean {
        val pkgName = packageName
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        if (!TextUtils.isEmpty(flat)) {
            val names = flat.split(":")
            for (name in names) {
                val cn = ComponentName.unflattenFromString(name)
                if (cn != null && TextUtils.equals(pkgName, cn.packageName)) {
                    return true
                }
            }
        }
        return false
    }

    override fun onStateChanged(connected: Boolean, status: String) {
        runOnUiThread {
            statusText.text = "Status: $status"
        }
    }

    override fun onPairingResult(success: Boolean, message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            updateUi()
        }
    }
}
