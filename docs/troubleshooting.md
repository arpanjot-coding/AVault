[Back to Support Documentation](./SUPPORT.md)
# AVault Troubleshooting Guide

This guide addresses common questions and troubleshooting steps for **AVault**.

---

## 1. Battery Optimization & Background Execution

If AVault is stopped or killed when a call begins:
- Ensure the app is excluded from Android battery optimization (**Unrestricted** battery mode).
- On aggressive OEM battery managers (e.g. Samsung, Xiaomi, OnePlus), verify that AVault is exempt from automatic app sleep or process termination. Consult [dontkillmyapp.com](https://dontkillmyapp.com) for device-specific background guidelines.

---

## 2. Audio Capture & Route Troubleshooting

### Only one side of the call is recorded
- **Cellular / VoLTE calls:** Ensure the audio source is configured to **Voice call** or **Unprocessed** in Settings.
- **VoIP / Wi-Fi Calling (WhatsApp, Telegram, etc.):** On Android 14+, enable **Allow capture via the Playback API** under Audio configuration.
- **Stereo Channel Splitting:** Enable **Split Uplink/Downlink audio** in Settings to separate local and remote audio onto discrete left/right audio channels.

---

## 3. Storage & Auto-Cleanup Management

### How to ensure critical conversations are never deleted
- Enable **Auto-delete old conversations** in Settings.
- Open the **Calls** dashboard and tap the bookmark icon on any conversation you wish to preserve.
- Bookmarked conversations show a highlighted gold bookmark and are strictly exempt from automatic purge cycles.
- You can filter and view your preserved conversations anytime by tapping the **Saved** filter chip at the top of the Calls list.

---

## 4. Security & Cryptographic Vault

### How files are secured on disk
- All recordings are encrypted using AES-256-GCM via the Android hardware KeyStore before being saved with `.vault` extensions.
- Even if the device filesystem is accessed via USB or file explorers, audio data cannot be read without the hardware-isolated master key.
- Decryption occurs in ephemeral private memory only during active playback and is erased immediately when playback ends or the app is closed.

---

## 5. Diagnostic Export

If you encounter unexpected issues, you can generate a diagnostic report:
- Go to **Settings > Debug & Diagnostics**.
- Tap **Export Diagnostic Logs** to save an encrypted/redacted diagnostic bundle for review.
