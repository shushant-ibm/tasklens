# ADR-006: Zero-Overhead No-Op Production Artifacts

## Status
Accepted

## Context
Many mobile teams require that developer tooling, diagnostic databases, and on-device UI inspectors are completely stripped out of production builds to minimize APK/IPA binary size, attack surface, and performance overhead.

## Decision
TaskLens provides dedicated `-noop` modules:
- Android: `:tasklens-noop` publishes empty implementations of `TaskLens` methods with inline functions or empty return stubs.
- iOS: `TaskLensNoop` provides identical function signatures with empty bodies.

In `build.gradle.kts`, teams configure:
```kotlin
debugImplementation("dev.shushant.tasklens:tasklens-android:0.1.0")
releaseImplementation("dev.shushant.tasklens:tasklens-noop:0.1.0")
```

## Consequences
### Positive
- Production releases contain zero SQLite databases, zero background coroutine channels, zero broadcast receivers, and zero UI activities from TaskLens.
- R8/ProGuard shrinks out remaining inline calls to zero byte overhead.

### Negative
- Applications must ensure they only call the public `TaskLens` facade methods supported by both real and no-op artifacts.
