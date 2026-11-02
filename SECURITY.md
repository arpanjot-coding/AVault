# Security notes

These notes describe AVault's implementation and its limits. They are not a claim of an independent security audit.

## Encryption and keys

When recording encryption is enabled, AVault uses AES-256-GCM and a key managed by Android Keystore. The key alias is `AVaultMasterEncryptionKey`. Whether that key is hardware-backed depends on the Android device; the app does not require StrongBox or independently attest hardware backing.

The current file envelope is an eight-byte ASCII `AVAULT01` header, a 12-byte IV, followed by ciphertext and the GCM authentication tag. The recording index also uses the encryption manager. Encryption is not equivalent to protection against a compromised OS or a process with access to the app's sandbox.

## App lock

The optional app lock uses a four-digit PIN and/or supported Android biometric/device authentication. PIN hashes use PBKDF2-HMAC-SHA256 with 150,000 iterations and a random salt. Older SHA-256 hashes are upgraded after successful verification. Repeated failed attempts trigger an app-managed timed lockout; this is not a hardware-enforced exponential lockout.

The app PIN gates the interface. It does not derive the audio key, and the Keystore key is not configured to require a biometric authentication for every use. Keep the device itself locked and protected.

Ordinary debug builds do not impose a default PIN or insert sample recordings. The separate `.screenshots` application is explicitly for demonstrations and uses only fictional sample data.

## Recording, playback, export and deletion

The recording service encodes an audio file and attempts encryption when finalising it if encryption is enabled. Plaintext can exist during capture/finalisation or when encryption fails. The app does not stream microphone audio directly into an encrypted-only storage pipeline.

Playback creates temporary decrypted files in the app's private `secure_playback` cache. Cleanup is requested when playback stops or completes, the library is disposed, or MainActivity backgrounds. Pausing playback retains the current playback file so playback can resume. A sudden process termination can interrupt cleanup; playback is not exclusively in RAM.

Sharing creates a decrypted copy in the app's `shared_audio` cache and grants the selected receiving application access to that copy. That destination controls its own copies. Do not share recordings with an application you do not trust.

Retention cleanup removes eligible files and index entries. Bookmarked recordings are excluded from automatic cleanup. File deletion on flash storage is not a guarantee of forensic erasure.

## Screen privacy

When app lock and the screen privacy preference are enabled, MainActivity uses Android `FLAG_SECURE` for screenshots and recent-app content. This protection applies to the ordinary debug build as well as release builds. The isolated sample activity intentionally permits screenshots of fictional data.

## Reporting a vulnerability

Report privately through [GitHub's vulnerability reporting form](https://github.com/arpanjot-coding/AVault/security/advisories/new) if enabled. Do not publish real recordings, phone numbers, keys or other sensitive data in a public issue. Include the affected version, Android/device details, reproduction steps and expected impact.
