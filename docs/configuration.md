[Back to Support Documentation](./SUPPORT.md)
# AVault Configuration Guide

This guide covers setup, permissions, security settings, and storage retention options for **AVault**.

---

## 1. System Integration & Permissions

AVault requires specific Android runtime permissions to detect call state transitions and integrate with system audio capture:
- **Phone / Telecom Permissions:** Required to detect incoming and outgoing calls, identify contacts, and interface with the Android Telecom subsystem.
- **Battery Exemption:** Required to ensure Android's Power Manager does not terminate the vault during background call state transitions.
- **System Shell Integration:** Supported through [Shizuku](https://shizuku.rikka.app) to authorize internal audio capture without requiring device rooting.

---

## 2. Security & Privacy Settings

Inside AVault's **Settings > Security and App Lock**:
- **App Lock Protection:** Toggle authentication requirement to open AVault and view recorded conversations.
- **Biometric and Screen Lock:** Unlock using Fingerprint, Face recognition, or your device passcode.
- **Master PIN:** Configure an independent SHA-256 salted PIN as a dedicated vault password.
- **Screen & Recents Privacy:** Activates Android `FLAG_SECURE` to block screenshots and blur/hide conversation contents in the Recent Apps overview.
- **Hardware Encryption:** Ensures all stored recordings on disk are encrypted using AES-256-GCM via the Android KeyStore (`.vault`).

---

## 3. Storage & Auto-Cleanup Settings

Inside AVault's **Settings > Storage & Auto-Cleanup**:
- **Auto-Delete Old Conversations:** Automatically cleans up expired conversations to optimize storage space.
- **Retention Period:** Configurable thresholds including:
  - 7 days (1 week)
  - 14 days (2 weeks)
  - 30 days (1 month, default)
  - 60 days (2 months)
  - 90 days (3 months)
  - 180 days (6 months)
  - 365 days (1 year)
- **Bookmark Preservation:** When Auto-Delete mode is enabled, bookmark icons appear beside conversations in the Calls tab. Bookmarking any call marks it as **Saved forever**, making it completely immune to automatic purges.
- **Run Auto-Cleanup Now:** Perform an on-demand cleanup pass to immediately reclaim storage.

---

## 4. Audio Engine Configuration

Inside AVault's **Settings > Audio configuration**:
- **Audio Source:** Configure Voice Call, Playback Capture, or system microphone routes.
- **Audio Codec:** Select high-efficiency **Opus** or widely compatible **AAC**.
- **Bitrate:** Adjustable audio compression from 16 kbps to 128 kbps.
- **Split Uplink/Downlink Audio:** Records caller audio on the left channel and remote participant audio on the right channel for clean stereo separation.
