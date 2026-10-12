# DroidBridge

Privacy-first local Wi-Fi notification and OTP bridge between Samsung Android phones and macOS.

## Progress Checklist
- [x] Protocol schema definition (`protocol/schema/messages.json`)
- [x] Android companion app architecture
  - [x] `NotificationListenerService` implementation
  - [x] OTP detection engine with unit tests
  - [x] Keystore AES-GCM secure credentials storage
  - [x] TLS certificate pinning (`FingerprintTrustManager`)
  - [x] Exponential backoff reconnection manager
  - [x] Scanning and permission UI
- [x] macOS application frontend
  - [x] Vite + React + TypeScript desktop UI
  - [x] OTP card with live expiration timer & one-click copy
  - [x] Visual QR pairing modal
  - [x] Tested and passing frontend production build
- [x] Tauri 2 Rust Desktop backend scaffolding
  - [x] Cargo configuration with local WSS & TLS support
  - [ ] Rust WebSocket TLS Server implementation (In Progress)

## Building & Running

### Mac Desktop Application
```bash
cd mac
npm install
npm run build
npm run tauri dev # or npm run tauri build
```

### Android Companion
Open `android/` in Android Studio or build with Gradle:
```bash
cd android
./gradlew test
./gradlew assembleDebug
```
