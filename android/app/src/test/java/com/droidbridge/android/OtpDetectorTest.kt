package com.droidbridge.android

import com.droidbridge.android.otp.OtpDetector
import com.droidbridge.android.otp.OtpConfidence
import org.junit.Assert.*
import org.junit.Test

class OtpDetectorTest {

    private val detector = OtpDetector(defaultExpirySeconds = 60)

    @Test
    fun testExplicitVerificationCodePattern() {
        val message = "Your verification code is 123456."
        val result = detector.detectOtp(message)
        assertNotNull(result)
        assertEquals("123456", result?.code)
        assertEquals(OtpConfidence.HIGH, result?.confidence)
    }

    @Test
    fun testUseAsOtpPattern() {
        val message = "Use 482731 as your OTP."
        val result = detector.detectOtp(message)
        assertNotNull(result)
        assertEquals("482731", result?.code)
        assertEquals(OtpConfidence.HIGH, result?.confidence)
    }

    @Test
    fun testSecurityCodeColonPattern() {
        val message = "Your security code: 729104."
        val result = detector.detectOtp(message)
        assertNotNull(result)
        assertEquals("729104", result?.code)
        assertEquals(OtpConfidence.HIGH, result?.confidence)
    }

    @Test
    fun testPreservesLeadingZeros() {
        val message = "Your login OTP is 004812."
        val result = detector.detectOtp(message)
        assertNotNull(result)
        assertEquals("004812", result?.code)
        assertTrue(result?.code?.startsWith("00") == true)
    }

    @Test
    fun testEightDigitOtp() {
        val message = "Your verification code: 08912345"
        val result = detector.detectOtp(message)
        assertNotNull(result)
        assertEquals("08912345", result?.code)
    }

    @Test
    fun testFourDigitPin() {
        val message = "Enter 9421 as your verification pin"
        val result = detector.detectOtp(message)
        assertNotNull(result)
        assertEquals("9421", result?.code)
    }

    @Test
    fun testRejectsPriceAmounts() {
        val message = "You paid $1234 to Store."
        val result = detector.detectOtp(message)
        assertNull(result)
    }

    @Test
    fun testRejectsInrAmounts() {
        val message = "Debited Rs. 5420 from A/C 9988."
        val result = detector.detectOtp(message)
        assertNull(result)
    }

    @Test
    fun testRejectsYearsInContextWithoutOtpKeywords() {
        val message = "Happy New Year 2026 to you!"
        val result = detector.detectOtp(message)
        assertNull(result)
    }

    @Test
    fun testEmptyOrNullReturnsNull() {
        assertNull(detector.detectOtp(null))
        assertNull(detector.detectOtp(""))
        assertNull(detector.detectOtp("   "))
    }
}
