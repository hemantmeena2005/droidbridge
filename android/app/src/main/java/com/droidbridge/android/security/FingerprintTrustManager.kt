package com.droidbridge.android.security

import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager

/**
 * Custom X509TrustManager that validates server certificates against
 * the SHA-256 fingerprint verified during the QR code pairing process.
 * 
 * Protects against Man-In-The-Middle (MITM) attacks on self-signed local TLS.
 */
class FingerprintTrustManager(
    private val expectedSha256Fingerprint: String
) : X509TrustManager {

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        // Android client does not need client certificate in this setup
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        if (chain.isNullOrEmpty()) {
            throw CertificateException("Server certificate chain is empty")
        }

        val leafCert = chain[0]
        val sha256 = getSha256Fingerprint(leafCert)

        val cleanExpected = expectedSha256Fingerprint.replace(":", "").lowercase()
        val cleanActual = sha256.replace(":", "").lowercase()

        if (cleanExpected != cleanActual) {
            throw CertificateException("Server certificate fingerprint mismatch! MITM detected.")
        }
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()

    companion object {
        fun getSha256Fingerprint(cert: X509Certificate): String {
            val md = MessageDigest.getInstance("SHA-256")
            val der = cert.encoded
            md.update(der)
            val digest = md.digest()
            return digest.joinToString(":") { "%02X".format(it) }
        }
    }
}
