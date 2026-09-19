# TaskLens Enterprise-Grade Completion & KMP Migration Specification

**Status:** Final execution baseline  
**Target:** Enterprise-grade cross-platform background execution observability SDK  
**Platforms:** Android + iOS  
**Architecture:** KMP shared core + native platform adapters + native host-facing UI  
**Primary principle:** If TaskLens cannot explain behavior reliably, with evidence, without destabilizing the host app, it is not done.

# 1. Product Vision

TaskLens is a cross-platform **background execution observability and diagnosis SDK** for Android and iOS.

It answers one operational question exceptionally well:

> **What happened to my background task, why did it behave that way, what evidence supports the explanation, and what remains unknown because of platform limitations?**

TaskLens is not:
- a scheduler
- a generic logger
- an APM replacement
- a crash reporter
- a developer drawer
- a cloud-first monitoring product

TaskLens is a **flight recorder, correlation engine, and evidence-backed diagnosis layer** for mobile background execution.

The enterprise promise is:

```text
Observe → Correlate → Explain → Export → Reproduce
```

The SDK must remain useful with:
- no cloud account
- no desktop tooling
- no proxy
- no external service
- no network dependency

# 2. Why We Are Changing the Architecture

The current implementation has:
- strong Android end-to-end functionality
- duplicated Swift models/rules
- incomplete iOS runtime wiring
- iOS events created but not persisted
- iOS in-memory storage only
- placeholder iOS export
- stubbed iOS UI
- Kourier bridge not auto-wired
- destructive Android DB migration
- incomplete noop API parity
- incomplete release/publish hardening

The new architecture removes duplicated business logic and makes cross-platform parity enforceable.

Final architecture:

```text
                         TASKLENS

                 ┌───────────────────┐
                 │   KMP SHARED CORE │
                 │                   │
                 │ Domain Models     │
                 │ Events            │
                 │ Correlation       │
                 │ Timeline          │
                 │ Evidence          │
                 │ Diagnosis         │
                 │ Redaction         │
                 │ Export Contracts  │
                 │ Retention         │
                 │ Limitations       │
                 └─────────┬─────────┘
                           │
             ┌─────────────┴─────────────┐
             ↓                           ↓
       ANDROID NATIVE                iOS NATIVE
       WorkManager                   BGTaskScheduler
       JobScheduler                  BGAppRefreshTask
       ForegroundService             BGProcessingTask
       AlarmManager                  Background URLSession
       Android power/network         NWPathMonitor / power
       Room/SQLite                    SQLite
       Compose                        SwiftUI
```

# 3. Architecture Rules

## 3.1 Shared where semantics are truly shared

Use KMP for:
- domain models
- event schema
- event processing contracts
- correlation logic
- attempt reconstruction
- timeline generation
- evidence model
- diagnosis engine
- common diagnosis rules
- redaction
- archive schema
- export manifest generation
- retention policy logic
- platform limitation model
- schema versioning
- golden fixtures

## 3.2 Keep platform behavior native

Do not force platform scheduler semantics into fake abstractions.

Android stays native for:
- WorkManager
- JobScheduler
- ForegroundService
- AlarmManager
- ConnectivityManager
- PowerManager
- ProcessLifecycleOwner

iOS stays native for:
- BGTaskScheduler
- BGAppRefreshTask
- BGProcessingTask
- NWPathMonitor
- ProcessInfo power state
- UIApplication lifecycle
- Background URLSession

## 3.3 Do not adopt CMP UI in v0.1.x

Host-facing UI stays:
- Compose on Android
- SwiftUI on iOS

Reason:
TaskLens is an embedded SDK. Host apps should not be forced to carry a large cross-platform UI runtime merely for a debug/diagnostic interface.

CMP may be reconsidered later for isolated reusable components such as:
- timeline renderer
- diagnosis card
- raw event viewer

# 4. Repository Layout

```text
tasklens/
│
├── README.md
├── LICENSE
├── CHANGELOG.md
├── CONTRIBUTING.md
├── SECURITY.md
├── CODE_OF_CONDUCT.md
├── NOTICE
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── Package.swift
│
├── specs/
│   ├── SPEC.md
│   ├── ENTERPRISE_COMPLETION_SPEC.md
│   ├── EXPORT_SCHEMA.md
│   ├── EVENT_SCHEMA.md
│   └── API_COMPATIBILITY.md
│
├── docs/
│   ├── ARCHITECTURE.md
│   ├── ANDROID.md
│   ├── IOS.md
│   ├── DIAGNOSIS_ENGINE.md
│   ├── PRIVACY.md
│   ├── SECURITY.md
│   ├── KOURIER_INTEGRATION.md
│   ├── MIGRATIONS.md
│   ├── PERFORMANCE.md
│   ├── RELEASE.md
│   └── ROADMAP.md
│
├── tasklens-core-kmp/
│   ├── src/commonMain/
│   ├── src/commonTest/
│   ├── src/androidMain/
│   ├── src/iosMain/
│   └── src/iosTest/
│
├── tasklens-android/
├── tasklens-workmanager/
├── tasklens-jobscheduler/
├── tasklens-foreground/
├── tasklens-alarm/
│
├── tasklens-ios/
├── tasklens-bgtasks/
├── tasklens-background-urlsession/
│
├── tasklens-kourier/
├── tasklens-noop/
│
├── sample-android/
├── sample-ios/
├── integration-tests/
├── compatibility-tests/
├── benchmark/
├── test-fixtures/
│
└── .github/workflows/
```

# 5. Shared Domain Model

All common models:
- immutable
- serializable
- backward-compatible by default
- use `kotlin.time.Instant`
- preserve unknown/extra metadata through extension maps where practical
- never expose mutable collections

Core models:

```kotlin
ScheduledWork
ExecutionAttempt
ConstraintSnapshot
EnvironmentSnapshot
TaskLensEvent
Evidence
Diagnosis
PossibleFactor
PlatformReason
PlatformLimitation
TaskTimeline
TimelineEntry
TaskQuery
RetentionPolicy
ExportManifest
TaskLensExportSnapshot
```

# 6. Canonical Event Model

```kotlin
@Serializable
data class TaskLensEvent(
    val id: EventId,
    val taskId: TaskId?,
    val attemptId: AttemptId?,
    val timestamp: Instant,
    val sequence: Long,
    val type: EventType,
    val source: EventSource,
    val severity: EventSeverity,
    val attributes: Map<String, String> = emptyMap(),
    val schemaVersion: Int = 1
)
```

Required event types:

```text
TASK_REGISTERED
TASK_SUBMITTED
TASK_ENQUEUED
TASK_WAITING
TASK_ELIGIBLE
TASK_LAUNCHED
TASK_STARTED
TASK_STOPPED
TASK_EXPIRED
TASK_RETRY_REQUESTED
TASK_RESCHEDULED
TASK_SUCCEEDED
TASK_FAILED
TASK_CANCELLED
TASK_COMPLETION_REPORTED

CONSTRAINT_CHANGED
NETWORK_CHANGED
BATTERY_CHANGED
POWER_MODE_CHANGED
APP_STATE_CHANGED
PROCESS_STATE_CHANGED
BACKGROUND_REFRESH_CHANGED

URLSESSION_TRANSFER_STARTED
URLSESSION_TRANSFER_COMPLETED
URLSESSION_TRANSFER_FAILED

HTTP_REQUEST_STARTED
HTTP_REQUEST_COMPLETED
HTTP_REQUEST_FAILED

CUSTOM_BREADCRUMB
CLOCK_SHIFT_DETECTED
CONFIGURATION_WARNING
INTERNAL_DIAGNOSTIC
```

# 7. Event Ordering and Determinism

Every event MUST have:
- wall-clock timestamp
- monotonic sequence number
- stable unique ID

Ordering rule:
1. monotonic sequence
2. timestamp
3. insertion order

If wall clock shifts:
- preserve original timestamp
- emit `CLOCK_SHIFT_DETECTED`
- do not reorder prior persisted events

# 8. Event Pipeline

```text
Platform callback
    ↓
Platform adapter
    ↓
Normalized TaskLensEvent
    ↓
Redaction
    ↓
Bounded event queue
    ↓
Persistent append
    ↓
Correlation
    ↓
Attempt reconstruction
    ↓
Task projection update
    ↓
Diagnosis trigger
    ↓
Read-model invalidation
    ↓
UI / export
```

The pipeline MUST:
- be async
- avoid main-thread blocking
- preserve terminal events
- tolerate duplicate callbacks
- survive partial persistence failure
- not crash host application

# 9. Backpressure Policy

Events are prioritized:

```text
P0  terminal task state
P1  scheduler / attempt state
P2  environment changes
P3  debug breadcrumbs
```

Rules:
- P0 never dropped
- P1 never dropped unless process is terminating
- P2 may be coalesced
- P3 may be dropped under pressure

Queue telemetry MUST expose:
- current depth
- dropped P3 count
- coalesced event count

# 10. Shared Contracts

```kotlin
interface EventSink {
    suspend fun emit(event: TaskLensEvent)
}

interface TaskLensStore {
    suspend fun append(event: TaskLensEvent)
    suspend fun saveTask(task: ScheduledWork)
    suspend fun saveAttempt(attempt: ExecutionAttempt)
    suspend fun saveDiagnosis(diagnosis: Diagnosis)

    suspend fun getTask(taskId: TaskId): ScheduledWork?
    suspend fun getAttempts(taskId: TaskId): List<ExecutionAttempt>
    suspend fun getEvents(taskId: TaskId): List<TaskLensEvent>
    suspend fun getDiagnoses(taskId: TaskId): List<Diagnosis>
    suspend fun queryTasks(query: TaskQuery): List<ScheduledWork>

    suspend fun delete(taskId: TaskId)
    suspend fun clear()
    suspend fun applyRetention(policy: RetentionPolicy)
}

interface CorrelationEngine {
    fun correlate(
        event: TaskLensEvent,
        context: CorrelationContext
    ): CorrelationResult
}

interface TimelineBuilder {
    fun build(
        task: ScheduledWork,
        attempts: List<ExecutionAttempt>,
        events: List<TaskLensEvent>
    ): TaskTimeline
}

interface DiagnosisEngine {
    fun diagnose(context: DiagnosisContext): List<Diagnosis>
}

interface DiagnosisRule {
    val id: String
    val priority: Int
    fun evaluate(context: DiagnosisContext): DiagnosisCandidate?
}
```

# 11. Diagnosis Engine

The engine is deterministic in v0.1.x.

No LLM decides root cause.

Confidence:

```text
CONFIRMED
LIKELY
POSSIBLE
UNKNOWN
```

Each diagnosis MUST include:
- classification
- summary
- confidence
- evidence IDs
- contributing factors
- platform limitations
- rule ID
- rule version

# 12. Common Diagnosis Rules

Shared rules:
- waiting on unsatisfied constraint
- retry chain detected
- explicit application failure
- cancellation
- network interruption during attempt
- completion success
- attempt timeout if deterministically inferred
- repeated retry pattern
- scheduler delay as an observation, not cause

Platform-specific rules are injected separately.

# 13. Android-Specific Rules

Support:
- WorkManager stop reasons
- Doze/background restriction
- battery optimization
- device idle
- app standby where exposed
- expedited work limitations
- JobScheduler stop semantics later
- foreground service timeout/restrictions later

Never overstate a platform signal.

# 14. iOS-Specific Rules

Support:
- BGTask expiration
- completion(success: false)
- background refresh denied/restricted
- BGTask identifier misconfiguration
- required network/external power mismatch
- task submitted but not launched within observed period
- URLSession background transfer failure

For scheduler delay, always include:

```text
iOS does not expose the exact scheduler decision responsible for launch timing.
```

# 15. Platform Limitations

Shared codes:

```text
IOS_SCHEDULER_REASON_NOT_EXPOSED
IOS_DIRECT_COMPLETION_NOT_OBSERVABLE_WITHOUT_WRAPPER
IOS_TASK_LAUNCH_TIMING_NOT_DETERMINISTIC
ANDROID_STOP_REASON_API_LEVEL_DEPENDENT
ANDROID_PROCESS_TERMINATION_BEST_EFFORT
NETWORK_CORRELATION_TIME_BASED
KOURIER_CORRELATION_BEST_EFFORT
PROCESS_DEATH_INFERENCE_BEST_EFFORT
```

These are first-class exported data, not documentation-only caveats.

# 16. Android Completion Tasks

## Must preserve
- current WorkManager E2E flow
- Compose debugger
- storage
- diagnosis
- export

## Must fix

### 16.1 Non-destructive migrations
Replace table-drop upgrades.

Requirements:
- explicit migration objects
- upgrade/downgrade policy
- migration fixtures
- CI migration tests
- no data loss on supported upgrade path

### 16.2 Observer lifecycle correctness
Cache exact LiveData/observer reference.
Stop must remove from the same observable.

### 16.3 Unified clock abstraction

```kotlin
interface TaskLensClock {
    fun now(): Instant
}
```

No direct `System.currentTimeMillis()` in diagnosis logic.

### 16.4 Diagnosis trigger policy
Run final diagnosis on:
- retry
- stop
- expiration
- terminal state
- explicit user refresh

Do not recompute every rule on every low-value environment event.

# 17. iOS Completion Tasks

## 17.1 Real event sink
Every BGTask event must be emitted to the runtime sink.

No created-and-discarded events.

## 17.2 Persistent store
Replace in-memory-only store.

Requirements:
- SQLite
- actor-isolated
- versioned schema
- migrations
- retention
- corruption recovery
- query parity with Android

## 17.3 Attempt reconstruction
Persist:
- launch
- expiration
- completion
- failure
- cancellation
- resubmission relationship

## 17.4 Shared diagnosis
Use KMP engine.
Delete duplicated Swift business rules once parity tests pass.

## 17.5 Real export
Generate `.tasklens` archive with same schema as Android.

## 17.6 Real UI
Implement SwiftUI:
- TaskFeedView
- TaskDetailView
- TimelineView
- DiagnosisView
- EnvironmentView
- RawEventsView
- SettingsView
- ExportReviewView

`TaskLens.show()` / `hide()` must work for:
- UIKit hosts
- SwiftUI hosts

## 17.7 BGTask wrappers
Implement:
- register
- submit
- launch
- expiration
- complete

Wrapper must preserve host behavior exactly.

# 18. iOS BGTask Contracts

```swift
TaskLensBG.register(identifier: "com.example.refresh") { task in
    ...
}
```

Records:
- registration
- launch
- environment snapshot

```swift
try TaskLensBG.submit(request)
```

Records:
- identifier
- request type
- earliestBeginDate
- network requirement
- external power requirement

```swift
TaskLensBG.complete(task, success: true)
```

Records:
- completion signal
- success/failure
- closes attempt
- triggers diagnosis

Expiration wrapper:
- preserves original handler
- emits expiration
- closes attempt
- triggers diagnosis

# 19. Background URLSession

Provide helper:

```swift
TaskLensURLSession.makeBackgroundSession(...)
```

Capture:
- session identifier
- transfer start
- transfer completion
- error
- relaunch handoff
- completion-handler lifecycle

No body capture by default.

# 20. Kourier Integration

Bridge is optional.

TaskLens core MUST not depend directly on Kourier.

```text
tasklens-kourier
    ↓
NetworkTelemetryBridge
```

Bridge maps Kourier traffic to normalized events:
- HTTP_REQUEST_STARTED
- HTTP_REQUEST_COMPLETED
- HTTP_REQUEST_FAILED

Auto-enable when:
- TaskLens config enables bridge
- Kourier is present
- compatible API is available

If unavailable:
- no crash
- no reflection failure leakage
- record one internal diagnostic
- continue without network correlation

# 21. Correlation Strategy

Priority:
1. explicit correlation ID
2. platform task/worker ID
3. execution context
4. known request ownership
5. time-window correlation fallback

Every best-effort correlation MUST be labelled.

No false certainty.

# 22. Storage Design

Logical tables:

```text
tasks
attempts
events
diagnoses
metadata
```

Required indices:

```text
events(task_id, sequence)
events(attempt_id, sequence)
attempts(task_id, attempt_number)
tasks(submitted_at)
diagnoses(task_id)
```

Storage requirements:
- bounded growth
- transactional writes
- WAL where suitable
- corruption handling
- migration testing
- no plaintext secrets by default
- export snapshot consistency

# 23. Persistence Recovery

If DB is corrupt:
1. stop writes
2. record internal diagnostic if possible
3. attempt safe reopen
4. if unrecoverable, rotate corrupted DB
5. create new DB
6. never crash host app

Provide a debug-visible message:
“TaskLens trace store was reset after corruption.”

# 24. Export Format

Extension:

```text
.tasklens
```

Container:
ZIP

Contents:

```text
manifest.json
task.json
attempts.json
events.json
timeline.json
diagnosis.json
environment.json
device.json
network/kourier.json
README.html
```

Requirements:
- deterministic file names
- versioned schema
- no secrets by default
- checksums in manifest
- consistent Android/iOS field names
- unknown fields tolerated by readers

# 25. Export Manifest

Include:

```text
schemaVersion
taskLensVersion
platform
platformVersion
appVersion
buildNumber
createdAt
taskId
archiveId
files[]
privacySummary
checksums
capabilities
limitations
```

# 26. Enterprise Privacy

Default OFF:
- worker payloads
- HTTP bodies
- cookies
- auth headers
- arbitrary app state
- user-entered text

Default ON:
- timing
- state transitions
- scheduler metadata
- network availability
- power state
- OS/device model
- app version
- task identifiers

# 27. Redaction

Provide:
- key-based redaction
- regex redaction
- payload truncation
- allowlist mode
- custom redactor

All export paths must pass through redaction again.

Defense in depth:
capture redaction + export redaction.

# 28. Security Requirements

Enterprise baseline:
- no remote execution
- no hidden network calls
- no analytics by default
- no telemetry upload
- no third-party trackers
- secure temp-file handling
- exported files excluded from backups where practical
- documented data retention
- threat model in repo
- security policy
- CVE reporting channel
- dependency scanning

# 29. Supply Chain Security

CI MUST include:
- dependency vulnerability scan
- secret scan
- license scan
- SBOM generation
- provenance/attestation where supported
- signed release artifacts/tags where practical
- reproducible build checks where practical

# 30. API Compatibility

Public API policy:
- SemVer
- deprecate before remove
- source-compatible no-op artifact
- Android binary compatibility checks
- Swift API compatibility review
- export schema compatibility tests

Add automated API snapshots.

# 31. No-Op Parity

No-op artifact MUST exactly mirror public API surface.

Specifically verify:
- config fields
- logger field
- correlation APIs
- export API
- trace wrappers
- show/hide
- all public enums/types used by host app

No-op compatibility is a CI gate.

# 32. Performance Budgets

Idle:
- near-zero CPU
- zero network
- minimal wakeups

Startup:
- no blocking DB open on main thread
- no full history load at install

Runtime:
- event append p95 < 5 ms off-main
- UI closed memory overhead target documented
- bounded channel
- deduplicated environment events

Export:
- streaming archive construction
- avoid loading huge histories into memory

Add benchmark suite.

# 33. Power Efficiency

Because TaskLens observes background work, it must not materially worsen battery usage.

Rules:
- passive callbacks preferred
- no polling if callback APIs exist
- coalesce frequent environmental signals
- no periodic wakeups solely for TaskLens
- no background worker created just to monitor other workers

# 34. Threading / Concurrency

Android:
- SupervisorJob
- Dispatchers.Default/IO
- serialized store writes
- immutable UI state
- cancellation-safe collectors

iOS:
- actor-based runtime
- actor/serial DB access
- MainActor only for UI
- no blocking SQLite on main actor

# 35. Failure Isolation

Every public non-export API should be non-throwing unless explicitly documented.

TaskLens failure must never:
- cancel a host worker
- alter BGTask completion semantics
- block scheduler callbacks
- crash the host
- swallow host exceptions
- modify network requests

# 36. Internal Health Diagnostics

Provide internal health state:

```text
READY
DEGRADED_STORAGE
DEGRADED_KOURIER
DEGRADED_EXPORT
DISABLED
```

Expose only in debugger UI, not host business logic.

# 37. UI Requirements

Android: Compose  
iOS: SwiftUI

Screens:
- Task Feed
- Task Detail
- Timeline
- Diagnosis
- Attempts
- Constraints
- Environment
- Network
- Raw Events
- Export Review
- Settings
- Internal Health

Accessibility:
- dynamic type
- screen readers
- no color-only semantics
- keyboard focus where relevant
- minimum touch targets

# 38. Task Detail UX

Default tab:
Timeline

Sections:
```text
Overview
Timeline
Diagnosis
Attempts
Constraints
Environment
Network
Raw Events
Export
```

The UI must answer the question in under 10 seconds.

# 39. Testing Strategy

## Shared/KMP
- domain serialization
- correlation
- attempt reconstruction
- timeline
- diagnosis
- redaction
- export manifest
- retention
- platform limitations

## Android
- WorkManager state transitions
- retry
- cancellation
- constraints
- stop reason
- DB migration
- process recreation
- Compose UI smoke tests

## iOS
- BGTask registration wrapper
- submission wrapper
- launch
- expiration
- completion
- SQLite migration
- SwiftUI smoke tests
- background URLSession adapter

# 40. Golden Fixtures

Maintain shared fixtures:

```text
success.json
retry.json
network-loss.json
constraint-wait.json
workmanager-stop.json
ios-expiration.json
ios-not-launched.json
corrupt-clock.json
```

For each fixture assert:
- normalized events
- attempt reconstruction
- timeline
- diagnosis
- export

Android and iOS must produce equivalent shared outputs for equivalent semantics.

# 41. Fault Injection Tests

Test:
- DB full
- DB locked
- DB corruption
- exporter I/O failure
- queue pressure
- malformed event attributes
- missing task ID
- clock shift
- Kourier unavailable
- duplicate callbacks
- process restart mid-attempt

# 42. CI Matrix

Required workflows:

```text
kmp-core.yml
android-ci.yml
android-instrumentation.yml
ios-ci.yml
schema-compat.yml
migration-tests.yml
api-compat.yml
security.yml
benchmark.yml
sample-builds.yml
release.yml
```

# 43. Publishing

Android:
- Maven Central or GitHub Packages initially
- signed artifacts
- sources/javadoc artifacts

iOS:
- Swift Package Manager
- tagged GitHub releases
- optional XCFramework later

Release MUST fail if:
- tests fail
- API diff unexpected
- schema compatibility fails
- no-op parity fails
- migration tests fail
- security scan finds blocking issue

# 44. Versioning

SDK:
SemVer

Archive:
independent `schemaVersion`

Diagnosis rules:
rule IDs + rule versions

Example:

```text
sdkVersion = 0.1.0
schemaVersion = 1
rule = android.workmanager.stop_reason@1
```

# 45. Observability of TaskLens Itself

TaskLens must self-monitor locally:
- dropped low-priority events
- queue depth high-water mark
- DB failures
- exporter failures
- bridge failures
- migration status

Never upload this automatically.

# 46. Migration Plan

## Step 1
Freeze current Android behavior with golden tests.

## Step 2
Create `tasklens-core-kmp`.

## Step 3
Move models into commonMain.

## Step 4
Move serialization and event schema.

## Step 5
Move correlation/attempt reconstruction.

## Step 6
Move diagnosis contracts + common rules.

## Step 7
Move timeline builder.

## Step 8
Adapt Android code to consume KMP core.

## Step 9
Run Android parity tests.

## Step 10
Replace Swift mirrors with KMP-facing models.

## Step 11
Wire iOS event sink.

## Step 12
Implement persistent iOS store.

## Step 13
Wire iOS diagnosis.

## Step 14
Implement iOS export.

## Step 15
Implement SwiftUI debugger.

## Step 16
Wire Kourier bridge on both platforms.

## Step 17
Harden migrations/security/performance.

## Step 18
Publish cross-platform beta.

# 47. Do Not Break Android While Migrating

Mandatory gate:
For each migrated component, compare old vs new outputs using the same fixtures.

Android v0.1 behavior is the reference.

No migration PR merges if:
- timeline changes unexpectedly
- diagnosis changes without spec update
- export changes without schema update

# 48. Definition of Done: KMP Core

Done when:
- common models are canonical
- Swift duplicate business models are removed or bridge-only
- shared diagnosis runs on both platforms
- timeline builder shared
- correlation shared
- golden fixtures shared
- Android parity proven
- iOS consumes same shared engine

# 49. Definition of Done: Android

Done when:
- current features preserved
- migration non-destructive
- observer lifecycle fixed
- unified clock
- diagnosis trigger optimized
- no-op parity complete
- Kourier auto-bridge works
- instrumentation CI added
- performance budget met
- publishing works

# 50. Definition of Done: iOS

Done when:
- all BGTask events persist
- app restart retains traces
- attempts reconstructed
- KMP diagnosis runs
- export parity achieved
- SwiftUI debugger works
- show/hide works
- background URLSession trace works
- no-op package works
- sample app demonstrates real flows
- CI is green

# 51. Definition of Done: Enterprise Grade

TaskLens is enterprise-grade only when ALL are true:

- zero host crashes caused by TaskLens in stress testing
- deterministic diagnoses are evidence-backed
- platform limitations explicitly surfaced
- schema migrations preserve data
- public API compatibility is checked
- Android/iOS archive parity is enforced
- privacy defaults are safe
- no hidden network calls
- security scans run in CI
- SBOM generated
- signed release artifacts
- performance budgets measured
- queue/backpressure tested
- fault injection suite passes
- no-op parity tested
- sample apps work independently
- external integration docs complete
- upgrade path tested
- Kourier integration optional and isolated

# 52. First 30 Implementation Issues

1. Freeze Android golden fixtures
2. Create `tasklens-core-kmp`
3. Move IDs and enums to commonMain
4. Move ScheduledWork
5. Move ExecutionAttempt
6. Move TaskLensEvent
7. Move Evidence/Diagnosis
8. Move PlatformLimitation
9. Move correlation engine
10. Move attempt reconstruction
11. Move timeline builder
12. Move common diagnosis rules
13. Add Android parity test suite
14. Replace destructive DB migration
15. Fix WorkManager observer lifecycle
16. Add shared clock
17. Fix diagnosis trigger strategy
18. Build iOS runtime EventSink
19. Implement iOS SQLite store
20. Add iOS schema migrations
21. Wire BGTask register/submit/launch
22. Wire expiration/completion
23. Wire iOS KMP diagnosis
24. Implement iOS `.tasklens` export
25. Implement SwiftUI task feed/detail/timeline
26. Implement iOS show/hide coordinator
27. Auto-wire Kourier bridge
28. Fix no-op API parity
29. Add API/schema/migration CI gates
30. Add performance/security/release pipeline

# 53. ADRs Required

```text
ADR-001 Separate TaskLens repository
ADR-002 KMP shared core
ADR-003 Native platform scheduler adapters
ADR-004 Native Compose/SwiftUI UI
ADR-005 Deterministic diagnosis
ADR-006 Evidence before inference
ADR-007 Optional Kourier bridge
ADR-008 Local-first persistence
ADR-009 Versioned export schema
ADR-010 Non-destructive migrations
ADR-011 No-op production artifact
ADR-012 Platform limitations as data
ADR-013 No hidden telemetry
ADR-014 Backpressure and terminal-event priority
ADR-015 Security and SBOM release gate
```

# 54. Acceptance Scenarios

## Android scenario A
Worker waits for network, starts, network drops, retries, succeeds.

Expected:
- all states captured
- two attempts reconstructed
- network interruption shown as contributing factor
- no false causal claim
- Kourier request correlated if present

## Android scenario B
Worker stopped by platform.

Expected:
- stop reason preserved
- platform/API limitation noted if needed
- diagnosis confirmed where reason explicit

## iOS scenario A
BGProcessingTask submitted, launched, expires.

Expected:
- submission persisted
- launch persisted
- attempt created
- expiration persisted
- diagnosis = TASK_EXPIRED / CONFIRMED

## iOS scenario B
Task submitted but not launched.

Expected:
- scheduler delay observation
- confidence UNKNOWN/POSSIBLE
- explicit limitation that iOS does not expose exact scheduler decision

## iOS scenario C
Background URLSession transfer fails.

Expected:
- transfer lifecycle captured
- correlation to logical task where possible
- export contains events

# 55. Product Quality Bar

A developer should be able to:
1. integrate TaskLens
2. reproduce a background issue
3. open TaskLens
4. understand what happened
5. see evidence
6. understand uncertainty
7. export a trace
8. hand it to another engineer

in under 10 minutes.

# 56. North Star

TaskLens is complete when the user can say:

> **“I know what happened, I know why TaskLens believes that, and I know what the platform did not reveal.”**

That is the standard.

Anything less is instrumentation.
TaskLens must be diagnosis infrastructure.
