# Okaiwa for Android

Secure messenger and embedded crypto wallet for Android.

Okaiwa is a privacy-first communication platform that combines end-to-end encrypted messaging with a non-custodial multi-chain crypto wallet. Built with Signal Protocol encryption, every message, call, and transaction is protected by default.

## Features

- **End-to-end encrypted messaging** — Signal Protocol (Double Ratchet + X3DH)
- **Encrypted voice & video calls** — WebRTC with SRTP
- **Non-custodial crypto wallet** — Multi-chain support (Ethereum, Polygon, Arbitrum, Base)
- **In-chat payments** — Send crypto directly in conversations
- **Disappearing messages** — Configurable auto-delete timers
- **Screen security** — FLAG_SECURE by default, screenshot protection
- **Biometric authentication** — Fingerprint & face unlock via Android Keystore
- **Zero-knowledge architecture** — Server never sees plaintext

## Architecture

- **Language:** Kotlin 2.0
- **UI:** Jetpack Compose + Material 3
- **Architecture:** Clean Architecture + MVVM
- **DI:** Hilt
- **Networking:** OkHttp + Retrofit + WebSocket
- **Local storage:** Room + SQLCipher (encrypted database)
- **Crypto:** Signal Protocol via JNI (libsignal-client)
- **Push:** Firebase Cloud Messaging (data-only, silent push)

## Building

```bash
# Clone the repository
git clone https://github.com/okaiwa/okaiwa-android.git
cd okaiwa-android

# Build debug APK
./gradlew assembleDebug

# Run unit tests
./gradlew test

# Run lint
./gradlew lint
```

Requires JDK 17 and Android SDK 35.

## Project Structure

```
app/src/main/kotlin/io/okaiwa/
├── core/              # Config, DI, navigation, theme, errors
├── features/
│   ├── auth/          # Registration, login, key generation
│   ├── chat/          # Conversations, messages, encryption
│   ├── wallet/        # Crypto wallet, transactions
│   ├── contacts/      # Contact discovery
│   ├── calls/         # Voice & video calls
│   ├── settings/      # App settings, security score
│   └── profile/       # User profile
└── shared/            # Utilities, screen security, keystore
```

## Security

Okaiwa takes security seriously. If you discover a vulnerability, please report it responsibly. See [SECURITY.md](SECURITY.md) for details.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for development setup and contribution guidelines.

## License

Copyright 2026 Globodai FZCO

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

See [LICENSE](LICENSE) for the full license text.
