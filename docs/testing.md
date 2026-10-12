# DroidBridge Testing Strategy

## Automated Tests
- `android/app/src/test/java/com/droidbridge/android/OtpDetectorTest.kt`:
  - 4 to 8 digit verification codes.
  - Leading zeros preservation (`004812`).
  - Contextual patterns ("verification code is", "use X as your OTP", "security code:").
  - False positive rejection (prices `$1234`, `Rs. 5420`, years `2026`).

## Manual Hardware Test Plan
1. **Samsung Notification Access:**
   - Go to Android Settings -> Apps -> Special App Access -> Device & app notifications.
   - Enable DroidBridge.
2. **Local Pairing:**
   - Open DroidBridge on Mac -> click "Pair Phone".
   - Open DroidBridge on Samsung phone -> click "Scan Pairing QR Code".
   - Verify fingerprint confirmation prompt matches.
3. **End-to-End OTP Forwarding:**
   - Trigger a real SMS or 2FA notification on the Samsung phone.
   - Confirm immediate card appearance on Mac with countdown and one-click copy.
   - Confirm code is copied to Mac clipboard without full notification noise.
