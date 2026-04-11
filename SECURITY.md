# Security Policy

## Reporting a Vulnerability

Okaiwa takes security extremely seriously. If you believe you have found a security vulnerability in our Android application, please report it responsibly.

**Do NOT open a public GitHub issue for security vulnerabilities.**

### How to Report

Email: **security@okaiwa.io**

Include the following in your report:

- Description of the vulnerability
- Steps to reproduce
- Affected versions
- Potential impact
- Suggested fix (if any)

### PGP Key

Our PGP key for encrypted communications is available at:
https://okaiwa.io/.well-known/security.txt

### Response Timeline

| Stage | Timeline |
|-------|----------|
| Acknowledgment | Within 24 hours |
| Initial assessment | Within 72 hours |
| Status update | Within 7 days |
| Fix & disclosure | Within 90 days |

### Scope

The following are in scope:

- End-to-end encryption implementation
- Key management and storage
- Authentication and session handling
- Wallet and transaction security
- Data leakage (logs, screenshots, clipboard)
- Network security (certificate pinning, TLS)
- Local data encryption (SQLCipher, Keystore)

### Out of Scope

- Social engineering attacks
- Physical device attacks requiring unlocked bootloader
- Denial of service
- Issues in third-party dependencies (report upstream)

### Recognition

We maintain a security hall of fame for responsible disclosures. Reporters will be credited (with permission) in our security advisories.

## Supported Versions

| Version | Supported |
|---------|-----------|
| Latest  | Yes       |
| < Latest - 1 | Security fixes only |
| < Latest - 2 | No |

## Security Design

- Signal Protocol (Double Ratchet + X3DH) for all messages
- Android Keystore with StrongBox for key material
- SQLCipher for local database encryption
- Certificate pinning for all API connections
- FLAG_SECURE on all sensitive screens
- No plaintext logging in release builds
- Biometric authentication with CryptoObject binding
