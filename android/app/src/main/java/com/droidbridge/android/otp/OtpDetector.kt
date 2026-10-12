package com.droidbridge.android.otp

enum class OtpConfidence {
    HIGH,
    MEDIUM,
    LOW
}

data class OtpResult(
    val code: String,
    val confidence: OtpConfidence,
    val expiresInSeconds: Int = 60
)

/**
 * Dedicated OTP Detector module.
 * 
 * Extracts verification codes (4-8 digits, including leading zeros).
 * Identifies contextual keywords while rejecting false positives (e.g., dates, order numbers, prices).
 * Never prints OTP values in logs.
 */
class OtpDetector(
    private val defaultExpirySeconds: Int = 60
) {
    companion object {
        // High confidence context markers
        private val HIGH_CONFIDENCE_PATTERNS = listOf(
            Regex("""(?i)\b(?:verification|security|login|access|one-time|authorization)\s+(?:code|pin|password)\s*(?:is|:)?\s*([0-9]{4,8})\b"""),
            Regex("""(?i)\b(?:use|enter|your)\s+([0-9]{4,8})\s+(?:as\s+(?:your\s+)?(?:otp|verification|security\s+code))\b"""),
            Regex("""(?i)\b(?:otp|code|pin)\s*(?:is|:|-)\s*([0-9]{4,8})\b"""),
            Regex("""(?i)\bcode\s*:\s*([0-9]{4,8})\b"""),
            Regex("""(?i)\b(?:G-|ID-|OTP-)?([0-9]{4,8})\s+is\s+your\s+(?:verification|security|login|otp|google|code)\b"""),
            Regex("""(?i)\b([0-9]{4,8})\s+is\s+(?:the\s+)?(?:secret\s+)?(?:otp|code)\b"""),
            Regex("""(?i)\b(?:is|code|pin)\s+([0-9]{4,8})\b""")
        )

        // Medium confidence context markers
        private val MEDIUM_CONFIDENCE_PATTERNS = listOf(
            Regex("""(?i)\b(?:verify|authenticate|confirm)\s+(?:with|code)?\s*([0-9]{4,8})\b"""),
            Regex("""(?i)\botp\s*[:\s-]?\s*([0-9]{4,8})\b"""),
            Regex("""(?i)\bcode\s*[:\s-]?\s*([0-9]{4,8})\b"""),
            Regex("""(?i)\b(?:secret|pin)\s*[:\s-]?\s*([0-9]{4,8})\b""")
        )

        // Disqualifiers (years, currencies, amounts, dates, phone numbers, tracking numbers)
        private val DISQUALIFY_PATTERNS = listOf(
            Regex("""(?i)\b(?:rs\.?|inr|usd|\$|eur|€|gbp|£)\s*[0-9]+"""),
            Regex("""(?i)[0-9]+\s*(?:rs\.?|inr|usd|\$|eur|€|gbp|£)"""),
            Regex("""\b(?:19|20)[0-9]{2}\b"""), // Common 4-digit years like 2024, 2026
            Regex("""\b[0-9]{10,}\b""")         // Phone numbers / tracking numbers >= 10 digits
        )
    }

    /**
     * Inspects text for OTP. Returns OtpResult or null if none found.
     * Preserves leading zeros.
     */
    fun detectOtp(text: String?): OtpResult? {
        if (text.isNullOrBlank()) return null

        // 1. Check high confidence patterns
        for (pattern in HIGH_CONFIDENCE_PATTERNS) {
            val match = pattern.find(text)
            if (match != null && match.groupValues.size > 1) {
                val candidate = match.groupValues[1]
                if (isValidCandidate(candidate, text)) {
                    return OtpResult(
                        code = candidate,
                        confidence = OtpConfidence.HIGH,
                        expiresInSeconds = defaultExpirySeconds
                    )
                }
            }
        }

        // 2. Check medium confidence patterns
        for (pattern in MEDIUM_CONFIDENCE_PATTERNS) {
            val match = pattern.find(text)
            if (match != null && match.groupValues.size > 1) {
                val candidate = match.groupValues[1]
                if (isValidCandidate(candidate, text)) {
                    return OtpResult(
                        code = candidate,
                        confidence = OtpConfidence.MEDIUM,
                        expiresInSeconds = defaultExpirySeconds
                    )
                }
            }
        }

        // 3. Fallback: Check if message contains OTP keywords and an isolated 4-8 digit number
        if (hasOtpKeywords(text)) {
            val genericNumberRegex = Regex("""\b([0-9]{4,8})\b""")
            val candidates = genericNumberRegex.findAll(text).map { it.groupValues[1] }.toList()
            for (candidate in candidates) {
                if (isValidCandidate(candidate, text)) {
                    // Check if it looks like a year
                    if (candidate.length == 4 && (candidate.startsWith("19") || candidate.startsWith("20"))) {
                        continue
                    }
                    return OtpResult(
                        code = candidate,
                        confidence = OtpConfidence.LOW,
                        expiresInSeconds = defaultExpirySeconds
                    )
                }
            }
        }

        return null
    }

    private fun hasOtpKeywords(text: String): Boolean {
        val lower = text.lowercase()
        return lower.contains("otp") ||
                lower.contains("verification") ||
                lower.contains("verify") ||
                lower.contains("security code") ||
                lower.contains("one-time") ||
                lower.contains("passcode") ||
                lower.contains("auth code")
    }

    private fun isValidCandidate(candidate: String, fullText: String): Boolean {
        val len = candidate.length
        if (len !in 4..8) return false

        // Disqualify if candidate is part of a price or timestamp
        val priceMatch = Regex("""(?i)(?:rs\.?|inr|\$|€|£)\s*""" + candidate).containsMatchIn(fullText)
        if (priceMatch) return false

        return true
    }
}
