# AVault Support Documentation

Welcome to the **AVault** support and user guide.

This guide provides setup, configuration, and troubleshooting information for AVault.

---

## Getting Started

1. **System Permission Integration:**
   - AVault uses Android's telecom APIs combined with system shell capabilities (via Shizuku) to capture high-fidelity call audio without requiring root.
   - Follow the [Configuration Guide](./configuration.md) to grant required telephony and notification permissions.
2. **Built-in Vault Dashboard:**
   - AVault provides an integrated, security-hardened UI to browse, play, search, filter, and preserve your encrypted conversations directly within the app.
3. **Hardware Encryption:**
   - All recorded audio is stored as `.vault` files using AES-256-GCM.
   - You can manage auto-cleanup rules and protect vital conversations by tapping the bookmark icon in the Calls list.

---

## Detailed Guides

- [Configuration & Settings Guide](./configuration.md)
- [Troubleshooting & Diagnostics](./troubleshooting.md)
