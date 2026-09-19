# Project Coding Rules (Non-Obvious Only)

- `tasklens-core` must stay pure JVM — never import `android.*` or `androidx.*` here; this is the cross-platform contract.
- `tasklens-noop` re-declares all public types (`TaskLensConfig`, `RetentionPolicy`, `TaskLensRedactor`) from scratch under the same package `dev.shushant.tasklens.android` — it does NOT depend on `tasklens-android`. Keep the two in sync manually.
- `TaskLens.trace()` has two overloads: a `suspend` extension in `tasklens-android` (typed to `ListenableWorker`) and an `inline suspend` in `tasklens-noop` (typed to `Any`) — they must remain API-compatible but cannot share code.
- All event attribute values must be `String`; never pass typed objects. Serialize to string before constructing `TaskLensEvent`.
- New `@Serializable` data class fields require default values or existing event deserialization will break (SQLite stores JSON blobs).
- `TaskLens.emit()` is fire-and-forget via `Channel.trySend()` — it never suspends and never throws. Do not wrap it in `try/catch`.
- The `WorkManagerAdapter` observes `LiveData` and **must** call `observeForever` on the main thread — use `runBlocking(Dispatchers.Main)` as is done now.
- `DefaultTaskLensRedactor.sensitivePatterns` matching is `key.lowercase().contains(pattern)` — substring match, case-insensitive. Adding new sensitive patterns here covers Android SDK automatically.
- Diagnosis rules go in `tasklens-diagnosis/.../diagnosis/rules/Rules.kt` and must be registered in `DefaultDiagnosisEngine`.
- Use `kotlin.time.Instant` (from `kotlinx-datetime`), not `java.time.Instant` — they are incompatible at the serialization boundary.
- Version bump: edit `version.properties` only. The root build script propagates it to all subprojects.
- Sample app (`sample-android`) is gated behind `-PskipSamples`; build CI uses this flag. Don't add production logic there.
