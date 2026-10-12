# DroidBridge Protocol v1

DroidBridge uses a secure WebSocket connection (`wss://`) over the local Wi-Fi network.

## Message Types

1. `hello` - Initial handshake sent by Android to Mac with device identity & auth proof.
2. `pair_request` - Pairing attempt with solved challenge and ephemeral public key.
3. `pair_confirm` - Mac acceptance with long-lived session token or rejection reason.
4. `notification` - Forwarded notification payload (filtered according to user settings).
5. `otp_detected` - Dedicated OTP event with verification code, confidence level, and expiry window.
6. `heartbeat` - Liveness check (`ping: true / false`).
7. `disconnect` - Clean session termination.
8. `error` - Protocol or authentication errors.

## Security Guarantees
- Single-use pairing challenge with short expiration (60s).
- TLS with self-signed certificate pinned via SHA-256 fingerprint in QR code.
- Ephemeral pairing challenge verification via HMAC-SHA256.
- Strict payload size limits (max 64 KB).
- No plaintext transmission of OTPs.
