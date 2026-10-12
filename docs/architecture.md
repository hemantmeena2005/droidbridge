# DroidBridge Architecture

DroidBridge establishes a secure, local, peer-to-peer bridge over Wi-Fi between an Android device and a macOS desktop application.

```mermaid
graph TD
    A[Android Notification Listener] -->|Detects OTP & Filters| B[Android Companion App]
    B -->|WSS Local Wi-Fi Pinning| C[Mac Tauri Rust Server :8765]
    C -->|Events IPC| D[Mac React Desktop UI]
    D -->|Click to Copy| E[macOS System Clipboard]
    C -->|Native Notification| F[macOS Notification Center]
```

## Security Model
- **TLS 1.3**: Ephemeral self-signed certificate on macOS, pinned via SHA-256 fingerprint inside the pairing QR code.
- **Challenge-Response**: Single-use 60s pairing challenge verified via HMAC-SHA256.
- **Keystore Backed**: Android pairing credentials stored securely in AndroidKeyStore using AES-GCM.
- **Privacy First**: OTPs kept strictly in-memory with automatic 60s expiration countdowns. No OTPs logged in plaintext.
