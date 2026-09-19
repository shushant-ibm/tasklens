# AGENTS.md

This file provides guidance to agents when working with code in this repository.

## Stack

- **Dual-platform SDK**: Kotlin/Android (Gradle, AGP 9.4) + Swift/iOS (Swift Package Manager, swift-tools-version 5.9)
- **Kotlin**: 2.4.10, JVM toolchain 21, `kotlin.code.style=official`
- **Gradle**: 9.7.1, version catalog at `gradle/libs.versions.toml`
- **Modules**: `tasklens-core` (pure JVM, no Android deps), `tasklens-storage`, `tasklens-diagnosis`, `tasklens-android` (main Android entry point), `tasklens-workmanager`, `tasklens-jobscheduler`, `tasklens-foreground`, `tasklens-alarm`, `tasklens-ui`, `tasklens-kourier`, `tasklens-noop`

## Build & Test Commands

```bash
# Build all modules (skips sample app)
./gradlew build -PskipSamples

# Run JVM unit tests for a specific module
./gradlew :tasklens-core:test
./gradlew :tasklens-storage:test
./gradlew :tasklens-diagnosis:test

# Run a single test class
./gradlew :tasklens-core:test --tests "dev.shushant.tasklens.core.CoreTests"

# Run a single test method
./gradlew :tasklens-core:test --tests "dev.shushant.tasklens.core.CoreTests.testDefaultRedactor"

# iOS tests (Swift Package Manager)
swift test
swift test --filter TaskLensTests
```

> `sample-android` is excluded by default with `-PskipSamples`. It is included normally without that flag.

## Module Dependency Rules

- `tasklens-core` is **pure JVM** — never add Android or iOS platform imports here.
- `tasklens-android` uses `api()` for `tasklens-core` and `tasklens-workmanager` (public surface), and `implementation()` for `tasklens-storage`, `tasklens-diagnosis`, `tasklens-export`, `tasklens-ui` (hidden from host app).
- Host apps should only depend on `:tasklens-android` — internal modules are encapsulated.
- `tasklens-noop` is a zero-overhead release replacement: it mirrors the exact same `dev.shushant.tasklens.android` package and `TaskLens` object API but all methods are no-ops.

## Code Style

- `kotlin.code.style=official` — enforced in `gradle.properties`.
- All domain models in `tasklens-core` are `@Serializable data class` or `@Serializable enum class` (kotlinx.serialization).
- New fields on serialized models **must** have default values to preserve backwards compatibility with stored event streams (schema versioning policy, see `docs/API_STABILITY.md`).
- `TaskLensEvent.schemaVersion` starts at 1; increment only on breaking schema changes.
- Use `kotlin.time.Instant` (not `java.time.Instant`) for timestamps — the codebase uses `kotlinx-datetime`.
- Event attribute maps are always `Map<String, String>` — no typed values, no nested objects.
- Sensitive attribute keys are auto-redacted by `DefaultTaskLensRedactor` before storage; attribute values are truncated at 2 KB with `" [TRUNCATED]"` suffix.

## Naming & Patterns

- Interfaces are named with the concept, default implementations are prefixed `Default` (e.g., `DiagnosisEngine` / `DefaultDiagnosisEngine`, `WorkManagerAdapter` / `DefaultWorkManagerAdapter`).
- No-op variants are suffixed `NoOp` (e.g., `NoOpTaskLensLogger`).
- Public Kotlin API on `TaskLensConfig` provides both a plain constructor and a `Builder` DSL; both must stay in sync.
- Worker tracing extension is `suspend fun <T> TaskLens.trace(worker, block)` — in `tasklens-android`, typed version; in `tasklens-noop`, inlined zero-allocation version.
- `TaskLens.emit()` silently drops events if SDK is not yet installed (guard: `AtomicBoolean.get()`).
- Event channel capacity is fixed at 1000 with `BufferOverflow.DROP_OLDEST` — do not change without an ADR.

## Diagnosis Engine

- Rules implement `DiagnosisRule` (in `tasklens-diagnosis`) with `id`, `name`, and `evaluate(DiagnosisContext): DiagnosisCandidate?`.
- Add new rules to `tasklens-diagnosis/src/main/kotlin/.../diagnosis/rules/Rules.kt`.
- `DiagnosisContext` contains the task, attempts, events, and environment snapshots — rules must be stateless and deterministic.

## iOS / Swift

- Swift sources mirror Kotlin model names exactly (see `docs/ARCHITECTURE.md` parity table).
- iOS modules live under `Sources/TaskLens*/`; tests under `Tests/TaskLensTests/`.
- `TaskLensNoop` target has no dependencies — keep it that way.

## Version & Release

- Version is managed in `version.properties` (`VERSION_NAME`, `GROUP`), not in `build.gradle.kts` or `gradle.properties`.
- The root `build.gradle.kts` reads `version.properties` and propagates to all subprojects via `allprojects { version = ... }`.
