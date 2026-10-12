package com.droidbridge.android.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import android.util.Base64
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Secure storage utilizing Android Keystore and AES-256 GCM encryption.
 * Stores paired Mac credentials, TLS fingerprints, and authentication tokens.
 */
class SecureKeyStorage(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "droidbridge_secure_prefs"
        private const val KEY_ALIAS = "droidbridge_master_key"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128

        private const val PREF_MAC_FINGERPRINT = "mac_cert_fingerprint"
        private const val PREF_AUTH_TOKEN = "auth_token"
        private const val PREF_MAC_HOST = "mac_host"
        private const val PREF_MAC_PORT = "mac_port"
        private const val PREF_MAC_NAME = "mac_name"
        private const val PREF_PAIRED = "is_paired"
    }

    init {
        ensureMasterKey()
    }

    private fun ensureMasterKey() {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            val spec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            keyGenerator.init(spec)
            keyGenerator.generateKey()
        }
    }

    private fun getSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)
        return keyStore.getKey(KEY_ALIAS, null) as SecretKey
    }

    fun savePairing(host: String, port: Int, macName: String, fingerprint: String, authToken: String) {
        prefs.edit()
            .putString(PREF_MAC_HOST, host)
            .putInt(PREF_MAC_PORT, port)
            .putString(PREF_MAC_NAME, macName)
            .putString(PREF_MAC_FINGERPRINT, fingerprint)
            .putString(PREF_AUTH_TOKEN, encrypt(authToken))
            .putBoolean(PREF_PAIRED, true)
            .apply()
    }

    fun isPaired(): Boolean = prefs.getBoolean(PREF_PAIRED, false)

    fun getMacHost(): String? = prefs.getString(PREF_MAC_HOST, null)
    fun getMacPort(): Int = prefs.getInt(PREF_MAC_PORT, 8765)
    fun getMacName(): String? = prefs.getString(PREF_MAC_NAME, null)
    fun getMacFingerprint(): String? = prefs.getString(PREF_MAC_FINGERPRINT, null)
    fun getAuthToken(): String? {
        val encrypted = prefs.getString(PREF_AUTH_TOKEN, null) ?: return null
        return decrypt(encrypted)
    }

    fun unpair() {
        prefs.edit()
            .remove(PREF_MAC_HOST)
            .remove(PREF_MAC_PORT)
            .remove(PREF_MAC_NAME)
            .remove(PREF_MAC_FINGERPRINT)
            .remove(PREF_AUTH_TOKEN)
            .putBoolean(PREF_PAIRED, false)
            .apply()
    }

    private fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())
        val iv = cipher.iv
        val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val combined = ByteArray(iv.size + cipherText.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(cipherText, 0, combined, iv.size, cipherText.size)
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    private fun decrypt(encryptedBase64: String): String? {
        return try {
            val combined = Base64.decode(encryptedBase64, Base64.NO_WRAP)
            val iv = ByteArray(12)
            val cipherText = ByteArray(combined.size - 12)
            System.arraycopy(combined, 0, iv, 0, 12)
            System.arraycopy(combined, 12, cipherText, 0, cipherText.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), spec)
            String(cipher.doFinal(cipherText), Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Compute challenge response using HMAC-SHA256
     */
    fun computeChallengeResponse(challenge: String, secret: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        val key = SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256")
        mac.init(key)
        val hash = mac.doFinal(challenge.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }
}
