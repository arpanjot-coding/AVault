# Contributing to AVault

Thank you for your interest in contributing to **AVault**!

AVault is an open-source, privacy-first call management and encrypted vault application. We welcome contributions that improve security, stability, code quality, and user experience.

---

## Guidelines for Contributing

1. **Clear Scope:** Keep pull requests focused, isolated, and well-tested. Large refactors should be proposed in an issue first.
2. **Code Style & Quality:**
   - Adhere to idiomatic Kotlin and Android architectural standards.
   - Use Jetpack Compose Material 3 patterns and proper state management.
   - Maintain documentation integrity across all components.
3. **Security Standards:**
   - Never weaken encryption, access controls, biometric verification, or screen security flags.
   - All sensitive audio must remain encrypted at rest using AES-256-GCM via the Android KeyStore.
   - Any transient decrypted data must be cleared immediately when no longer in use.
4. **Testing:**
   - Verify all changes on physical devices or Android emulators (API 31+).
   - Ensure `./gradlew assembleDebug` builds without errors or warnings.

---

## Reporting Issues

- When reporting bugs, please provide device model, Android version, steps to reproduce, and any relevant logcat output from AVault's diagnostic export.
- For security-sensitive vulnerability reports, please refer to our [Security Policy](SECURITY.md).

---

## License

By contributing to AVault, you agree that your contributions will be licensed under the project's [MIT License](LICENSE).
