# Security Policy

Security and user privacy are fundamental to **AVault**. This document outlines our security architecture and responsible disclosure process.

---

## Supported Versions

| Version | Supported |
|:---|:---|
| 2.x (AVault) | ✅ Active |
| < 2.0 | ❌ End of life |

---

## Security Architecture

1. **Hardware-Backed AES-256-GCM Encryption:**
   - All audio files are encrypted with the master key (`avault_master_key`) stored inside the device's hardware KeyStore / StrongBox.
   - Files on disk carry the `.vault` extension and cannot be read without device authentication.
2. **Encrypted Metadata Index:**
   - Call logs, contact names, durations, and bookmark flags are stored with encrypted byte buffers starting with the `AVAULT01` signature.
3. **Ephemeral Decryption & Zero Persistence:**
   - During playback, temporary files are decrypted with restricted mode `0600` in private internal cache.
   - All ephemeral files are wiped upon playback termination, pause, or application backgrounding.
4. **Application Lock:**
   - Multi-factor protection via custom SHA-256 salted PIN or Android BiometricPrompt (Fingerprint/Face/Device Credential).
   - Exponential rate-limiting lockout guards against brute-force attacks.
5. **Screen Privacy:**
   - `FLAG_SECURE` blocks screen capture and protects app content in the Android Recent Apps overview.

---

## Reporting a Vulnerability

If you discover a security vulnerability within AVault, please report it responsibly:
- **Do not open a public issue.**
- Submit your vulnerability details with a clear description, reproduction steps, and potential impact.
- We will review and provide an initial response promptly, working with you to patch any confirmed issue before public release.
