# DroidBridge Security Specifications

## 1. Overview
DroidBridge bridges notifications and OTPs strictly across the local area network (LAN) using a client-pinned WebSocket over TLS (`wss://`).

## 2. Cryptographic Controls
- **TLS 1.3 Pinning:** Self-signed ECDSA certificate generated on Mac launch. Android pins certificate by verifying the SHA-256 fingerprint delivered via the visual QR code channel.
- **Challenge-Response Authentication:** Single-use 60-second expiration challenge generated on Mac. Android must return `HMAC-SHA256(challenge, challenge)` to successfully pair.
- **In-Memory Volatility:** OTPs and verification pins are kept solely in volatile RAM with strict countdown timers (default: 60s) and erased immediately upon dismissal or expiration.
- **Zero Plaintext Logging:** Logs must never print notification titles, bodies, tokens, or OTP codes.
