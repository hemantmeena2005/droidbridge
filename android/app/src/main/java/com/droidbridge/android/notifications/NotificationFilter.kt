package com.droidbridge.android.notifications

import android.content.Context
import android.content.SharedPreferences

/**
 * Filter rules for notification forwarding.
 * Controls which applications can forward notifications or OTPs.
 */
class NotificationFilter(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "droidbridge_filter_prefs"
        private const val PREF_FORWARD_ALL = "forward_all_apps"
        private const val PREF_FORWARD_OTP_ONLY = "forward_otp_only"
        private const val PREF_BLOCKED_PACKAGES = "blocked_packages"
        private const val PREF_ALLOWED_PACKAGES = "allowed_packages"
    }

    var forwardOtpOnly: Boolean
        get() = prefs.getBoolean(PREF_FORWARD_OTP_ONLY, false)
        set(value) = prefs.edit().putBoolean(PREF_FORWARD_OTP_ONLY, value).apply()

    var forwardAllApps: Boolean
        get() = prefs.getBoolean(PREF_FORWARD_ALL, true)
        set(value) = prefs.edit().putBoolean(PREF_FORWARD_ALL, value).apply()

    fun isAppBlocked(packageName: String): Boolean {
        val blocked = prefs.getStringSet(PREF_BLOCKED_PACKAGES, emptySet()) ?: emptySet()
        return blocked.contains(packageName)
    }

    fun isAppAllowed(packageName: String): Boolean {
        if (isAppBlocked(packageName)) return false
        if (forwardAllApps) return true
        val allowed = prefs.getStringSet(PREF_ALLOWED_PACKAGES, emptySet()) ?: emptySet()
        return allowed.contains(packageName)
    }

    fun shouldForward(packageName: String, hasOtp: Boolean): Boolean {
        if (!isAppAllowed(packageName)) return false
        if (forwardOtpOnly && !hasOtp) return false
        return true
    }
}
