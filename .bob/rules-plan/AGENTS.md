# Project Architecture Rules (Non-Obvious Only)

- **Unidirectional pipeline**: Events → bounded channel (1000, DROP_OLDEST) → coroutine consumer → SQLite → diagnosis engine. Never write to storage directly outside the consumer coroutine.
- **`tasklens-core` is the cross-platform contract**: Any model change here must be mirrored in the Swift `Sources/TaskLensCore/` equivalents. They share no code but must stay structurally identical.
- **`tasklens-noop` is a full API mirror, not a test double**: All public types in `tasklens-android` must be duplicated here (no dependency, same package). Changes to `TaskLensConfig`, `RetentionPolicy`, or `TaskLens` object signatures require updates in both places.
- **Redaction is pre-storage, not post-read**: `DefaultTaskLensRedactor` runs in the channel consumer before `storeInstance.append()`. Sensitive data never touches SQLite or disk.
- **Diagnosis rules are stateless and deterministic**: `DiagnosisRule.evaluate()` takes a `DiagnosisContext` snapshot and returns a `DiagnosisCandidate?`. No coroutines, no side effects, no shared state.
- **WorkManager LiveData observation requires main thread**: `liveData.observeForever(observer)` must run on the main thread; the adapter uses `runBlocking(Dispatchers.Main)` for this. Scheduling it off-main will silently fail.
- **Event schema evolution is append-only**: New fields on `TaskLensEvent` or `ScheduledWork` require default values. Removing or renaming fields breaks deserialization of persisted JSON blobs in SQLite.
- **`sample-android` only depends on `:tasklens-android`**: The sample app must not directly reference any internal module (`:tasklens-storage`, `:tasklens-diagnosis`, etc.). This validates the public API surface.
- **Correlation context is ThreadLocal**: `TaskLens.withCorrelation()` uses `ThreadLocal<String?>` to attach `correlation_id` to subsequent emits on the same thread. Not safe to use across coroutine dispatchers without explicit propagation.
