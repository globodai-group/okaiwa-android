# Contributing to Okaiwa Android

Thank you for your interest in contributing to Okaiwa. This document provides guidelines for contributing to the Android application.

## Development Setup

### Prerequisites

- Android Studio Hedgehog (2023.1.1) or later
- JDK 17
- Android SDK 35
- Kotlin 2.0

### Building

```bash
git clone https://github.com/okaiwa/okaiwa-android.git
cd okaiwa-android
./gradlew assembleDebug
```

## Code Style

### Kotlin

- Follow the [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use `official` Kotlin code style (configured in `gradle.properties`)
- Maximum line length: 120 characters
- Use explicit return types for public functions
- Prefer `val` over `var`
- Use sealed classes/interfaces for state modeling
- Use data classes for entities

### Compose

- Composable function names use PascalCase
- State hoisting: keep state in ViewModel, pass down to composables
- Use `remember` and `derivedStateOf` appropriately
- Preview annotations for all screen composables

### Architecture

- **Clean Architecture**: domain -> data -> presentation
- **Domain layer**: entities, repository interfaces, use cases
- **Data layer**: repository implementations, data sources, DTOs
- **Presentation layer**: ViewModels (Hilt), Compose screens
- Use cases have a single `operator fun invoke()`
- ViewModels expose `StateFlow<UiState>`

## Git Workflow

### Branch Naming

```
feature/OKA-123-short-description
fix/OKA-456-bug-description
refactor/OKA-789-what-changed
```

### Commit Messages

Follow [Conventional Commits](https://www.conventionalcommits.org/):

```
feat(chat): add disappearing messages support
fix(wallet): correct balance display for ERC-20 tokens
refactor(auth): extract key generation to SignalCore JNI
test(chat): add unit tests for message encryption
docs: update API integration guide
```

### Pull Request Process

1. Create a feature branch from `dev`
2. Write code with tests
3. Ensure `./gradlew check` passes (build + lint + test)
4. Push and create a PR against `dev`
5. Fill out the PR template completely
6. Request review from at least one maintainer
7. Address review feedback
8. Squash-merge after approval

## Security Checklist

Before submitting a PR, verify:

- [ ] No secrets, keys, or tokens in code
- [ ] No plaintext logging of sensitive data
- [ ] Encryption used for all stored user data
- [ ] FLAG_SECURE applied to sensitive screens
- [ ] Certificate pinning not bypassed
- [ ] Input validation on all user inputs
- [ ] No data leakage via intents, clipboard, or screenshots

## Testing

- Unit tests for all use cases and ViewModels
- Use fakes/mocks for repository interfaces
- Integration tests for Room DAOs
- UI tests for critical user flows (Compose testing)

```bash
# Run all tests
./gradlew test

# Run specific module tests
./gradlew :app:testDebugUnitTest

# Run lint
./gradlew lint
```

## License

By contributing to Okaiwa, you agree that your contributions will be licensed under the AGPLv3 license.
