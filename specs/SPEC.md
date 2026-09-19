# TaskLens
## Final Cross-Platform Product, Architecture, HLD, LLD, Contracts & Delivery Specification

**Document status:** Final development baseline  
**Platforms:** Android + iOS  
**Initial implementation priority:** Android first, iOS in parallel architecture track  
**Long-term implementation style:** Shared domain/contracts where valuable, platform-native adapters where platform semantics differ  
**Primary positioning:** **Your background task didn't run. TaskLens tells you why.**

---

# 0. Purpose of This Document

This specification is intended to be sufficient to begin end-to-end development of TaskLens.

It defines:

- product vision
- platform scope
- Android behavior
- iOS behavior
- shared architecture
- repository structure
- module structure
- contracts
- public APIs
- HLD
- LLD
- event model
- diagnosis engine
- storage
- export
- privacy
- Kourier integration
- UI/UX
- testing
- CI/CD
- versioning
- release plan
- roadmap
- engineering milestones
- non-goals
- future extensibility

The purpose is to avoid architecture drift while still allowing implementation details to evolve.

---

# 1. Executive Vision

TaskLens is a **cross-platform background execution observability SDK** for Android and iOS.

It helps engineers, QA teams, and mobile platform teams understand why background work:

- did not start
- started late
- retried
- stopped
- expired
- was cancelled
- failed
- was rescheduled
- completed only under specific device conditions

TaskLens is not another scheduler.

It is the **flight recorder and diagnosis layer for background execution**.

On Android, it begins with:

- WorkManager
- JobScheduler
- ForegroundService
- AlarmManager

On iOS, it targets:

- BGTaskScheduler
- BGAppRefreshTask
- BGProcessingTask
- background URLSession
- app/background lifecycle signals

TaskLens turns platform-specific execution data into a common conceptual model:

```text
Scheduled Work
    ↓
Execution Attempt
    ↓
Constraints / Environment
    ↓
State Changes
    ↓
Completion / Retry / Stop / Expiration
    ↓
Diagnosis
```

---

# 2. Product North Star

A developer should be able to answer:

> What happened to this background task, and what evidence explains it?

without needing:

- Android Studio
- Xcode
- ADB
- Console.app
- desktop proxies
- custom log filtering
- remote dashboards
- cloud accounts

The on-device TaskLens UI should be enough to understand most common execution failures.

---

# 3. Product Promise

## Android example

```text
PaymentSyncWorker

10:21:04  Scheduled
10:21:04  Waiting for network
10:23:17  Network available
10:24:02  Started
10:24:08  Stopped
           ↳ Platform stop reason available
10:29:08  Retry scheduled
10:29:11  Started
10:29:13  Completed

Diagnosis
Execution was interrupted while the application was background-restricted.

Evidence
• platform-reported stop reason
• app state = background
• battery optimization = enabled
• network = connected
```

## iOS example

```text
refresh.catalog

09:00:00  Request submitted
09:00:00  Earliest begin = 09:30
09:42:18  System launched task
09:42:18  App entered background execution
09:42:26  Network became unavailable
09:42:29  Task expiration handler fired
09:42:30  Task completed(success = false)

Diagnosis
The task expired before successful completion.

Evidence
• BGTask expiration handler invoked
• task reported failure
• network unavailable during execution

Scheduler note
iOS does not expose the exact reason the task was launched at 09:42.
```

---

# 4. Product Principles

## 4.1 Explain, do not merely log

TaskLens should transform raw platform events into readable execution stories.

Bad:

```text
state=ENQUEUED
attempt=2
stopReason=3
```

Good:

```text
Attempt 2 was scheduled after the worker requested retry.
The task remained blocked for 4m 21s because its network constraint was unsatisfied.
```

---

## 4.2 Evidence before inference

Every diagnosis must separate:

- observed fact
- platform-reported reason
- derived conclusion
- possible contributing factor

Never state a cause when only correlation exists.

---

## 4.3 Platform honesty

Android and iOS expose different levels of scheduler information.

TaskLens must not pretend both platforms provide equivalent diagnostics.

Example:

```text
Android:
"WorkManager reported stop reason X."

iOS:
"The system did not launch this task during the observed window.
The exact scheduler decision is not exposed by iOS."
```

---

## 4.4 On-device first

No mandatory backend.

Cloud should be optional later.

---

## 4.5 Privacy by default

No payload capture by default.

No automatic network body capture.

No auth token capture.

---

## 4.6 Low integration friction

Basic integration should be extremely small.

Android:

```kotlin
TaskLens.install(application)
```

iOS:

```swift
TaskLens.install()
```

Advanced instrumentation remains optional.

---

## 4.7 Zero production tax

Provide no-op production artifacts and APIs.

---

# 5. Target Users

## Primary

- Android developers
- iOS developers
- QA engineers
- mobile platform teams
- Staff/Principal mobile engineers
- backend engineers supporting mobile workflows

## Secondary

- SRE/observability teams
- SDK teams
- release engineering teams

---

# 6. Core Jobs To Be Done

## JTBD-A

When background work does not execute when expected, tell me what prevented or delayed it.

## JTBD-B

When background work stops, show what happened around that moment.

## JTBD-C

When background work retries or is rescheduled, show the full attempt chain.

## JTBD-D

When QA reproduces a failure, let them export evidence without a developer laptop.

## JTBD-E

When the task performs network requests, correlate them with Kourier when available.

## JTBD-F

When the platform does not expose exact causality, tell me what is known and what remains unknowable.

---

# 7. Product Scope by Platform

# Android

## V1

- WorkManager
- CoroutineWorker
- Worker
- ListenableWorker
- constraints
- retries
- cancellation
- process lifecycle
- app lifecycle
- network
- battery
- charging
- battery saver
- battery optimization
- platform stop reasons where available
- export
- Kourier correlation

## V1.x / V2

- JobScheduler
- ForegroundService
- AlarmManager
- periodic work
- expedited work
- chained work
- unique work
- process death analysis
- OEM/background restriction heuristics

---

# iOS

## V1

- BGTaskScheduler
- BGAppRefreshTask
- BGProcessingTask
- registration
- request submission
- earliestBeginDate
- launch handler execution
- expiration handler
- completion state
- app lifecycle
- network path
- Low Power Mode
- Background App Refresh status
- relevant capabilities/Info.plist validation
- background URLSession correlation
- export
- Kourier correlation where Kourier iOS is installed

## V1.x / V2

- richer background URLSession execution tracking
- background transfer lifecycle
- silent push correlation where app hooks it
- location/background modes validation
- task frequency pattern analysis
- app termination / relaunch correlation
- system condition history

---

# 8. Explicit Non-Goals for Initial Release

Do not build initially:

- cloud dashboard
- crash reporting platform
- analytics platform
- feature flag platform
- database browser
- generic developer menu
- remote device control
- Jira automation
- Slack automation
- arbitrary log viewer
- full APM
- desktop app
- AI-generated root cause engine
- app performance profiler

---

# 9. Repository Strategy

Create a new independent repo:

```text
github.com/dev-shushant/tasklens
```

Do not place TaskLens inside Kourier.

Do not use Git submodules.

Recommended ecosystem:

```text
dev-shushant/
├── kourier-kmp
├── kourier
└── tasklens
```

Later:

```text
dev-shushant/
├── mobile-reliability
├── kourier-kmp
├── kourier
├── tasklens
├── turbulence
└── routeprobe
```

---

# 10. Top-Level Repository Structure

```text
tasklens/
│
├── README.md
├── LICENSE
├── CHANGELOG.md
├── CONTRIBUTING.md
├── CODE_OF_CONDUCT.md
├── SECURITY.md
├── gradle.properties
├── settings.gradle.kts
├── build.gradle.kts
├── Package.swift
│
├── docs/
│   ├── SPEC.md
│   ├── VISION.md
│   ├── ARCHITECTURE.md
│   ├── ANDROID.md
│   ├── IOS.md
│   ├── DIAGNOSIS_ENGINE.md
│   ├── PRIVACY.md
│   ├── EXPORT_FORMAT.md
│   ├── KOURIER_INTEGRATION.md
│   ├── API_STABILITY.md
│   └── ROADMAP.md
│
├── tasklens-core/
├── tasklens-diagnosis/
├── tasklens-export/
├── tasklens-storage/
├── tasklens-ui/
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
├── benchmark/
│
└── .github/
    └── workflows/
```

---

# 11. Shared Architecture

```text
                    ┌──────────────────────┐
                    │ Platform Schedulers  │
                    │ Android / iOS        │
                    └──────────┬───────────┘
                               │
                               ↓
                    ┌──────────────────────┐
                    │ Platform Adapters    │
                    └──────────┬───────────┘
                               │
               ┌───────────────┼────────────────┐
               ↓               ↓                ↓
        Task Events      Environment Events   App Events
               │               │                │
               └───────────────┼────────────────┘
                               ↓
                    ┌──────────────────────┐
                    │ Event Normalization  │
                    └──────────┬───────────┘
                               ↓
                    ┌──────────────────────┐
                    │ Correlation Engine   │
                    └──────────┬───────────┘
                               ↓
                    ┌──────────────────────┐
                    │ Timeline Builder     │
                    └──────────┬───────────┘
                               ↓
                    ┌──────────────────────┐
                    │ Diagnosis Engine     │
                    └──────────┬───────────┘
                               ↓
             ┌─────────────────┼──────────────────┐
             ↓                 ↓                  ↓
           UI                Export             Storage
```

---

# 12. Architectural Style

Use a layered architecture.

```text
Presentation
    ↓
Application
    ↓
Domain
    ↓
Platform Adapters / Infrastructure
```

## Domain must know nothing about:

- WorkManager
- BGTaskScheduler
- Compose
- SwiftUI
- Room
- UserDefaults
- Kourier concrete classes

---

# 13. Shared Domain Model

## 13.1 ScheduledWork

```kotlin
data class ScheduledWork(
    val id: TaskId,
    val platformId: String?,
    val name: String?,
    val type: TaskType,
    val scheduler: SchedulerType,
    val submittedAt: Instant?,
    val earliestBeginAt: Instant?,
    val periodic: Boolean,
    val metadata: Map<String, String>
)
```

Swift equivalent should mirror semantics.

---

## 13.2 ExecutionAttempt

```kotlin
data class ExecutionAttempt(
    val attemptId: AttemptId,
    val taskId: TaskId,
    val attemptNumber: Int,
    val startedAt: Instant?,
    val endedAt: Instant?,
    val outcome: AttemptOutcome?,
    val platformReason: PlatformReason?,
    val evidenceIds: List<EvidenceId>
)
```

---

## 13.3 ConstraintSnapshot

```kotlin
data class ConstraintSnapshot(
    val network: NetworkRequirement?,
    val chargingRequired: Boolean?,
    val batteryNotLowRequired: Boolean?,
    val storageNotLowRequired: Boolean?,
    val externalPowerRequired: Boolean?,
    val requiresIdle: Boolean?,
    val custom: Map<String, String>
)
```

Shared model should tolerate platform-specific gaps.

---

## 13.4 EnvironmentSnapshot

```kotlin
data class EnvironmentSnapshot(
    val networkState: NetworkState?,
    val charging: Boolean?,
    val batteryLevel: Double?,
    val lowPowerMode: Boolean?,
    val batterySaver: Boolean?,
    val appState: AppState?,
    val backgroundRefreshEnabled: Boolean?,
    val processState: ProcessState?,
    val platformAttributes: Map<String, String>
)
```

---

# 14. Unified Event Model

```kotlin
data class TaskLensEvent(
    val id: EventId,
    val taskId: TaskId?,
    val attemptId: AttemptId?,
    val timestamp: Instant,
    val type: EventType,
    val source: EventSource,
    val severity: EventSeverity,
    val attributes: Map<String, String>,
    val schemaVersion: Int = 1
)
```

Core events:

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
```

---

# 15. Evidence Contract

```kotlin
data class Evidence(
    val id: EvidenceId,
    val type: EvidenceType,
    val source: EvidenceSource,
    val timestamp: Instant?,
    val title: String,
    val description: String,
    val sourceEventIds: List<EventId>,
    val metadata: Map<String, String>
)
```

Evidence types:

```text
PLATFORM_REASON
STATE_TRANSITION
CONSTRAINT_STATE
NETWORK_STATE
POWER_STATE
LIFECYCLE_STATE
EXPIRATION_HANDLER
COMPLETION_RESULT
RETRY_SIGNAL
EXCEPTION
TIME_CORRELATION
CONFIG_VALIDATION
```

---

# 16. Diagnosis Contract

```kotlin
data class Diagnosis(
    val id: DiagnosisId,
    val taskId: TaskId,
    val attemptId: AttemptId?,
    val title: String,
    val summary: String,
    val classification: DiagnosisClassification,
    val confidence: DiagnosisConfidence,
    val evidence: List<Evidence>,
    val possibleFactors: List<PossibleFactor>,
    val limitations: List<String>
)
```

Confidence:

```text
CONFIRMED
STRONG
POSSIBLE
UNKNOWN
```

Classification:

```text
WAITING_ON_CONSTRAINT
PLATFORM_STOPPED
TASK_EXPIRED
RETRY_REQUESTED
NETWORK_INTERRUPTION
POWER_RESTRICTION
PROCESS_TERMINATED
APPLICATION_CANCELLED
APPLICATION_FAILURE
SCHEDULER_DELAY
CONFIGURATION_PROBLEM
UNKNOWN
```

---

# 17. Diagnosis Engine Design

Use deterministic rules first.

No LLM in V1.

Architecture:

```text
Event history
   ↓
Fact extractor
   ↓
Evidence builder
   ↓
Rule evaluator
   ↓
Diagnosis candidates
   ↓
Confidence resolver
   ↓
Final diagnosis
```

Contract:

```kotlin
interface DiagnosisRule {
    val id: String
    fun evaluate(context: DiagnosisContext): DiagnosisCandidate?
}
```

```kotlin
data class DiagnosisContext(
    val task: ScheduledWork,
    val attempts: List<ExecutionAttempt>,
    val events: List<TaskLensEvent>,
    val environment: List<EnvironmentSnapshot>
)
```

---

# 18. Android HLD

```text
Application
    ↓
TaskLens.install()
    ↓
AndroidRuntimeCollector
    ├── ProcessLifecycle
    ├── NetworkMonitor
    ├── BatteryMonitor
    ├── PowerManagerMonitor
    └── AppStateMonitor

WorkManager
    ↓
WorkManagerAdapter
    ├── WorkInfoObserver
    ├── WorkQueryReader
    ├── ConstraintMapper
    ├── AttemptMapper
    └── StopReasonMapper

Events
    ↓
Core EventBus
    ↓
Storage
    ↓
Timeline / Diagnosis / UI / Export
```

---

# 19. Android LLD

## 19.1 TaskLens Android entry point

```kotlin
object TaskLens {

    fun install(
        application: Application,
        config: TaskLensConfig = TaskLensConfig()
    )

    fun show()

    fun hide()

    fun clear()

    suspend fun export(taskId: String): TaskLensExportResult
}
```

---

## 19.2 Configuration

```kotlin
data class TaskLensConfig(
    val retentionPolicy: RetentionPolicy = RetentionPolicy.LastDays(7),
    val captureWorkerPayloads: Boolean = false,
    val captureEnvironment: Boolean = true,
    val enableKourierBridge: Boolean = true,
    val autoObserveWorkManager: Boolean = true,
    val diagnosticsEnabled: Boolean = true,
    val redactor: TaskLensRedactor = DefaultTaskLensRedactor,
    val logger: TaskLensLogger = NoOpTaskLensLogger
)
```

---

## 19.3 WorkManager adapter

```kotlin
interface WorkManagerAdapter {
    fun start()
    fun stop()
    suspend fun snapshot(): List<WorkSnapshot>
}
```

Implementation responsibilities:

- observe known work
- map WorkInfo states
- record run attempt count
- record tags
- map constraints
- detect terminal states
- fetch stop reason where platform supports it
- create attempt boundaries

---

## 19.4 Optional worker tracing

```kotlin
suspend fun <T> TaskLens.trace(
    worker: ListenableWorker,
    block: suspend () -> T
): T
```

This wrapper may capture:

- start/end
- exceptions
- explicit retry return
- breadcrumbs
- correlation context

Optional only.

---

## 19.5 Android environment collectors

### Network

Use ConnectivityManager.

Capture:

- connected
- validated
- metered
- transport
- roaming where available

### Battery

Capture:

- level
- charging
- low battery state

### Power

Capture:

- power save mode
- ignoring battery optimization
- device idle mode if available

### App lifecycle

Use ProcessLifecycleOwner.

### Process

Capture application process creation timestamp and best-effort termination boundary.

---

# 20. Android WorkManager Diagnosis Rules

Examples:

## Rule A1: blocked by network

IF:

```text
state = ENQUEUED
network constraint required
network unsatisfied
```

THEN:

```text
WAITING_ON_CONSTRAINT
confidence = CONFIRMED
```

---

## Rule A2: retry requested

IF:

```text
attempt count increases
or instrumented worker returns Result.retry()
```

THEN:

```text
RETRY_REQUESTED
confidence = CONFIRMED/STRONG
```

---

## Rule A3: platform stop reason

IF:

```text
stop reason exposed by platform
```

THEN:

```text
PLATFORM_STOPPED
confidence = CONFIRMED
```

---

## Rule A4: network interruption during execution

IF:

```text
network becomes unavailable during attempt
```

THEN:

```text
possible factor = connectivity loss
```

Do not claim causality unless application instrumentation proves it.

---

# 21. Android JobScheduler Future Contract

```kotlin
interface JobSchedulerAdapter {
    fun start()
    fun stop()
    suspend fun inspect(jobId: Int): JobExecutionSnapshot?
}
```

Events should normalize into the same TaskLens event model.

---

# 22. Android Foreground Service Future Contract

Track:

- service start request
- service created
- foreground promotion
- notification state
- stop
- system restriction
- timeout where applicable

---

# 23. Android AlarmManager Future Contract

Track:

- alarm scheduled
- exact/inexact
- permission status
- expected trigger
- actual receive time
- delay delta

---

# 24. iOS HLD

```text
Application
    ↓
TaskLens.install()
    ↓
IOSRuntimeCollector
    ├── AppLifecycleMonitor
    ├── NetworkMonitor
    ├── PowerMonitor
    ├── BackgroundRefreshMonitor
    └── CapabilityValidator

BGTaskScheduler
    ↓
BGTaskAdapter
    ├── Registration wrapper
    ├── Submission wrapper
    ├── Launch wrapper
    ├── Expiration handler wrapper
    └── Completion wrapper

Background URLSession
    ↓
URLSessionAdapter

Events
    ↓
Core EventBus
    ↓
Storage
    ↓
Timeline / Diagnosis / SwiftUI / Export
```

---

# 25. iOS LLD

## 25.1 Swift public entry point

```swift
public enum TaskLens {

    public static func install(
        config: TaskLensConfig = .default
    )

    public static func show()

    public static func hide()

    public static func clear()

    public static func export(
        taskId: String
    ) async throws -> URL
}
```

---

# 26. iOS Configuration

```swift
public struct TaskLensConfig {
    public var retentionPolicy: RetentionPolicy
    public var captureEnvironment: Bool
    public var enableKourierBridge: Bool
    public var diagnosticsEnabled: Bool
    public var redactor: TaskLensRedactor
}
```

---

# 27. BGTask Registration Contract

Preferred TaskLens wrapper:

```swift
TaskLensBG.register(
    identifier: "com.example.refresh"
) { task in
    await CatalogRefreshRunner.run(task)
}
```

Under the hood:

```text
BGTaskScheduler.register
    ↓
TaskLens records TASK_REGISTERED
    ↓
system invokes launch handler
    ↓
TaskLens records TASK_LAUNCHED
    ↓
TaskLens installs expiration wrapper
    ↓
developer block runs
    ↓
completion captured
```

TaskLens should not hide BGTaskScheduler semantics.

It wraps them.

---

# 28. BGTask Submission Contract

```swift
try TaskLensBG.submit(
    BGAppRefreshTaskRequest(identifier: "com.example.refresh")
)
```

TaskLens records:

- submission time
- identifier
- earliestBeginDate
- request type

For BGProcessingTaskRequest also capture:

- requiresNetworkConnectivity
- requiresExternalPower

---

# 29. BGTask Launch Contract

When the OS launches the task:

```text
TASK_LAUNCHED
TASK_STARTED
```

Capture:

- identifier
- task type
- launch time
- app state
- low power mode
- background refresh status
- network state

---

# 30. Expiration Handling

TaskLens must wrap expiration handler safely.

Concept:

```swift
let original = task.expirationHandler

task.expirationHandler = {
    TaskLens.recordExpiration(task)
    original?()
}
```

If direct wrapping constraints require developer cooperation, expose:

```swift
TaskLensBG.handleExpiration(task) {
    // app cleanup
}
```

TaskLens must never swallow the application expiration handler.

---

# 31. Task Completion Contract

Preferred helper:

```swift
TaskLensBG.complete(
    task,
    success: true
)
```

Internally:

```text
TASK_COMPLETION_REPORTED
TASK_SUCCEEDED / TASK_FAILED
```

TaskLens should document that it cannot automatically observe every direct call to:

```swift
task.setTaskCompleted(success:)
```

unless using the wrapper.

This is a key platform limitation.

---

# 32. iOS Background App Refresh Monitor

Capture:

```swift
UIApplication.shared.backgroundRefreshStatus
```

Normalized:

```text
AVAILABLE
DENIED
RESTRICTED
UNKNOWN
```

Use as environmental evidence only.

Do not imply this alone determines BGTask scheduling.

---

# 33. iOS Low Power Mode

Use ProcessInfo.

Capture changes:

```text
POWER_MODE_CHANGED
lowPowerMode = true/false
```

This is important context for background scheduling and execution.

---

# 34. iOS Network Monitor

Use NWPathMonitor.

Capture:

- satisfied
- unsatisfied
- requiresConnection
- interface type
- expensive
- constrained

Normalize to shared NetworkState.

---

# 35. iOS App Lifecycle

Capture:

- active
- inactive
- background

Use UIApplication notifications / SwiftUI scene phase integration as appropriate.

---

# 36. iOS Capability Validator

On startup or debug open, validate:

- BGTaskScheduler permitted identifiers
- background modes
- registered identifiers
- relevant Info.plist declarations
- mismatch between submitted identifier and permitted identifiers

Example diagnosis:

```text
Configuration problem

Task identifier:
com.example.refresh

was submitted but is not declared in
BGTaskSchedulerPermittedIdentifiers.
```

This should be a major iOS differentiator.

---

# 37. iOS BGTask Diagnosis Rules

## Rule I1: expiration

IF:

```text
expiration handler fired
```

THEN:

```text
TASK_EXPIRED
confidence = CONFIRMED
```

---

## Rule I2: failed completion

IF:

```text
setTaskCompleted(success: false)
via wrapper
```

THEN:

```text
APPLICATION_FAILURE
confidence = CONFIRMED
```

---

## Rule I3: request submitted but not launched

IF:

```text
request submitted
no launch observed during captured window
```

THEN:

```text
SCHEDULER_DELAY
confidence = UNKNOWN/POSSIBLE
```

Limitation must say:

```text
iOS does not expose the exact reason the scheduler did not launch this task.
```

---

## Rule I4: network unavailable during execution

IF:

```text
NWPath unsatisfied during attempt
```

THEN:

```text
possible factor = connectivity loss
```

---

## Rule I5: capability mismatch

IF:

```text
identifier not in permitted identifiers
```

THEN:

```text
CONFIGURATION_PROBLEM
confidence = CONFIRMED
```

---

# 38. Background URLSession HLD

```text
URLSession background configuration
       ↓
TaskLensURLSessionDelegateProxy
       ↓
Transfer events
       ↓
Correlation to logical background task
```

Track:

- transfer created
- started
- progress milestones optionally
- completed
- failed
- app relaunched for background events
- completion handler called

Do not collect bodies by default.

---

# 39. Background URLSession Public Integration

Preferred:

```swift
let session = TaskLensURLSession.makeBackgroundSession(
    identifier: "com.example.uploads",
    delegate: delegate
)
```

Alternative adapter for existing sessions should be provided later if feasible.

---

# 40. Shared Correlation Engine

Purpose:

Connect events into one logical task story.

Priority:

1. explicit correlation ID
2. platform task ID
3. worker/task identifier
4. execution context
5. time-window correlation as fallback

Contract:

```kotlin
interface CorrelationEngine {
    fun correlate(event: TaskLensEvent): CorrelationResult
}
```

Never silently treat time-window correlation as deterministic.

---

# 41. Correlation Context

Allow app code:

```kotlin
TaskLens.withCorrelation("payment-sync") {
    repository.sync()
}
```

Swift:

```swift
await TaskLens.withCorrelation("payment-sync") {
    try await repository.sync()
}
```

Kourier integration can use this context if both SDKs support it.

---

# 42. Kourier Integration Architecture

```text
TaskLens Core
      ↑
tasklens-kourier
      ↓
Kourier public telemetry API
```

No circular dependency.

The bridge should translate Kourier transactions into:

```text
HTTP_REQUEST_STARTED
HTTP_REQUEST_COMPLETED
HTTP_REQUEST_FAILED
```

with correlation metadata.

---

# 43. Kourier Integration Contract

```kotlin
interface NetworkTelemetryBridge {
    fun start()
    fun stop()
}
```

The TaskLens core depends only on the interface.

The `tasklens-kourier` artifact provides implementation.

---

# 44. Storage HLD

Storage must support:

- append event
- query by task
- query by attempt
- latest tasks
- retention cleanup
- export snapshot
- atomic write where practical

Shared interface:

```kotlin
interface TaskLensStore {
    suspend fun append(event: TaskLensEvent)
    suspend fun saveTask(task: ScheduledWork)
    suspend fun saveAttempt(attempt: ExecutionAttempt)
    suspend fun tasks(query: TaskQuery): List<ScheduledWork>
    suspend fun events(taskId: TaskId): List<TaskLensEvent>
    suspend fun delete(taskId: TaskId)
    suspend fun clear()
    suspend fun applyRetention(policy: RetentionPolicy)
}
```

---

# 45. Android Storage

Initial recommendation:

Room.

Tables:

```text
tasks
attempts
events
diagnoses
metadata
```

Suggested indices:

```text
events(task_id, timestamp)
events(attempt_id, timestamp)
attempts(task_id, attempt_number)
tasks(submitted_at)
```

---

# 46. iOS Storage

Initial recommendation:

SQLite-backed lightweight store or GRDB-style abstraction if dependency policy allows.

If avoiding external dependencies:

- SQLite C API wrapper
- actor-isolated persistence layer

Do not use UserDefaults for trace history.

---

# 47. Storage Schema

## tasks

```text
id
platform_id
name
type
scheduler
submitted_at
earliest_begin_at
periodic
metadata_json
```

## attempts

```text
attempt_id
task_id
attempt_number
started_at
ended_at
outcome
platform_reason_json
```

## events

```text
event_id
task_id
attempt_id
timestamp
type
source
severity
attributes_json
schema_version
```

## diagnoses

```text
diagnosis_id
task_id
attempt_id
classification
confidence
title
summary
payload_json
```

---

# 48. Event Ordering

All events must have:

- wall-clock timestamp
- monotonic ordering token if available
- insertion sequence

Use insertion sequence to break ties.

Never rely solely on wall clock.

---

# 49. Clock Skew Handling

If system clock changes:

- preserve original wall-clock timestamp
- preserve monotonic sequence where possible
- mark clock-shift event if detectable

Timeline should still remain ordered.

---

# 50. Retention

Default:

```text
7 days OR 500 task executions
```

whichever comes first.

Configurable.

Storage cleanup runs:

- app startup best-effort
- debugger open
- after N new events threshold

Never perform expensive cleanup on main thread.

---

# 51. Export Format

Extension:

```text
.tasklens
```

Internally:

```text
ZIP
```

Contents:

```text
manifest.json
task.json
attempts.json
timeline.json
diagnosis.json
environment.json
device.json
events.json
network/
  kourier.json
README.html
```

---

# 52. Export Manifest

```json
{
  "schemaVersion": 1,
  "taskLensVersion": "0.1.0",
  "platform": "android",
  "appVersion": "2.8.1",
  "createdAt": "2026-09-19T12:00:00Z",
  "taskId": "abc",
  "privacy": {
    "payloadsIncluded": false,
    "networkBodiesIncluded": false
  }
}
```

---

# 53. Export Compatibility

Rules:

- schema version mandatory
- readers must ignore unknown fields
- backward-compatible additions within schema version
- breaking changes require schema version increment
- keep old readers documented

---

# 54. Export README

Generate human-readable `README.html`.

It should contain:

- summary
- timeline
- diagnosis
- device info
- platform limitations
- evidence list

This allows backend or QA teams to inspect the trace without installing TaskLens.

---

# 55. Privacy Model

Default capture excludes:

- worker input payload values
- worker output payload values
- HTTP bodies
- cookies
- auth headers
- arbitrary app state

Allowed default metadata:

- worker class
- task identifier
- timing
- constraints
- OS/device info
- network status
- battery status
- lifecycle
- result state

---

# 56. Redaction Contract

```kotlin
interface TaskLensRedactor {
    fun redact(
        key: String,
        value: String
    ): String
}
```

Swift equivalent:

```swift
public protocol TaskLensRedactor {
    func redact(key: String, value: String) -> String
}
```

---

# 57. Export Review Screen

Before export:

```text
Export contains

✓ task timeline
✓ environment events
✓ task identifiers
✓ device metadata
✓ diagnosis
✗ worker payloads
✗ HTTP bodies
✗ auth headers
```

Allow user to cancel.

---

# 58. UI HLD

Common conceptual screens:

```text
Task Feed
Task Detail
Timeline
Diagnosis
Environment
Raw Events
Export
Settings
```

Android implementation:

Compose

iOS implementation:

SwiftUI

Do not force Compose Multiplatform initially.

---

# 59. Task Feed UX

Example:

```text
TaskLens

PaymentSyncWorker
RETRYING
2 attempts
3m ago

CatalogRefresh
EXPIRED
1 attempt
11m ago

ImageCleanupWorker
WAITING
Charging required
17m ago
```

Filters:

- waiting
- running
- retrying
- failed
- expired
- completed
- cancelled

---

# 60. Task Detail UX

Sections:

```text
Overview
Timeline
Diagnosis
Attempts
Constraints
Environment
Network
Raw events
Export
```

Default tab:

Timeline.

---

# 61. Timeline Visual Language

Suggested semantics:

```text
● submitted
◌ waiting
▶ started
◆ environment change
↻ retry
⚠ warning
✕ failed
⌛ expired
✓ success
```

Avoid color-only meaning.

Accessibility must be preserved.

---

# 62. Diagnosis UI

Example:

```text
What happened?

The task expired before it reported successful completion.

Confidence
Confirmed

Evidence

09:42:29
BGTask expiration handler invoked

09:42:30
Task completed(success = false)

Possible contributing factor

Network became unavailable 3 seconds before expiration.

Platform limitation

iOS does not expose the exact scheduler decision that
determined the task's launch time.
```

---

# 63. Raw Event UI

Useful for advanced engineers.

Must not be the default.

Show:

- timestamp
- event type
- source
- attributes
- correlation id

---

# 64. Settings

Initial settings:

- retention
- payload capture opt-in
- export redaction
- Kourier bridge enablement
- show diagnostic confidence
- clear all traces

---

# 65. Performance Requirements

## Android

- no blocking WorkManager queries on main thread
- collectors use coroutines
- UI lazy-loaded
- bounded storage
- event batching allowed

## iOS

- actor-isolated storage
- no synchronous heavy SQLite work on main actor
- NWPathMonitor on background queue
- event batching allowed
- SwiftUI reads from immutable view state

---

# 66. Performance Budgets

Targets:

```text
Idle CPU: near zero
Main-thread blocking: none for persistence/export
Storage growth: bounded
Startup overhead: negligible
Memory overhead when UI closed: minimal
Network overhead: zero
```

TaskLens itself performs no network calls in V1.

---

# 67. Failure Isolation

Rule:

```text
TaskLens failure must never become application failure.
```

Examples:

- persistence failure → disable persistence safely
- malformed event → drop + internal diagnostic
- Kourier bridge failure → bridge disabled
- UI failure → no impact on task execution
- export failure → return error to debugger only

---

# 68. Internal Logging

Provide internal logger abstraction.

Levels:

```text
ERROR
WARN
INFO
DEBUG
TRACE
```

Default:

No-op.

Do not spam host Logcat / Console.

---

# 69. Public Android API Surface

Keep small.

```kotlin
TaskLens.install(...)
TaskLens.show()
TaskLens.hide()
TaskLens.clear()
TaskLens.export(...)
TaskLens.breadcrumb(...)
TaskLens.withCorrelation(...)
```

Advanced:

```kotlin
TaskLens.trace(...)
```

Avoid exposing internal event store types publicly.

---

# 70. Public iOS API Surface

```swift
TaskLens.install(...)
TaskLens.show()
TaskLens.hide()
TaskLens.clear()
TaskLens.export(...)
TaskLens.breadcrumb(...)
TaskLens.withCorrelation(...)
```

BGTask helpers:

```swift
TaskLensBG.register(...)
TaskLensBG.submit(...)
TaskLensBG.complete(...)
TaskLensBG.handleExpiration(...)
```

Background session:

```swift
TaskLensURLSession.makeBackgroundSession(...)
```

---

# 71. API Design Principle

A small public API is a feature.

Internal implementation classes remain internal.

Avoid public types unless consumers need them.

---

# 72. No-Op Artifacts

Android:

```gradle
debugImplementation("dev.shushant.tasklens:tasklens:<version>")
releaseImplementation("dev.shushant.tasklens:tasklens-noop:<version>")
```

iOS:

Options:

- debug SPM product
- no-op SPM product
- compiler flag-based no-op wrapper

Preferred:

separate no-op product if packaging remains clean.

---

# 73. Android Packaging

Suggested group:

```text
dev.shushant.tasklens
```

Initial public artifact:

```text
tasklens
tasklens-noop
```

Internal modules may remain separate while the consumer-facing artifact aggregates them.

---

# 74. iOS Packaging

Swift Package Manager.

Products:

```text
TaskLens
TaskLensNoop
TaskLensKourierBridge
```

Later XCFramework distribution can be added.

---

# 75. Build Strategy

Android:

- Gradle Kotlin DSL
- version catalog
- convention plugins
- Detekt/Ktlint if desired
- unit + instrumentation tests

iOS:

- Swift Package Manager
- XCTest
- SwiftLint optional
- sample Xcode project

Shared schema:

- JSON schema files committed in repo
- golden export fixtures

---

# 76. Testing Pyramid

## Unit tests

- event mapping
- diagnosis rules
- evidence building
- retention
- correlation
- export schema
- redaction
- ordering

## Platform tests

Android:
- WorkManager state transitions
- retries
- constraints
- cancellation

iOS:
- registration wrapper
- request submission
- expiration wrapper
- completion wrapper
- capability validator

## Integration tests

- sample app scenario → expected timeline
- Kourier bridge
- export/import

## Golden tests

Input:

```text
events.json
```

Expected:

```text
timeline.json
diagnosis.json
```

---

# 77. Android Sample App

Screen:

```text
TaskLens Lab

[ Successful worker ]
[ Retry twice ]
[ Wait for network ]
[ Wait for charging ]
[ Cancel worker ]
[ API failure ]
[ Long-running worker ]
[ Open TaskLens ]
```

---

# 78. iOS Sample App

Screen:

```text
TaskLens Lab

[ Register refresh task ]
[ Submit refresh ]
[ Register processing task ]
[ Submit processing ]
[ Background URLSession upload ]
[ Simulate completion failure ]
[ Open TaskLens ]
```

Include a diagnostics screen showing:

- background refresh status
- low power mode
- permitted identifiers
- registered identifiers

---

# 79. Synthetic Test Harness

Create deterministic fake event sources.

Android fake:

```kotlin
FakeWorkManagerEventSource
```

iOS fake:

```swift
FakeBGTaskEventSource
```

This allows UI and diagnosis development without waiting for real scheduler timing.

Critical for iOS because real BGTask scheduling is nondeterministic.

---

# 80. CI/CD

GitHub Actions workflows:

```text
android-ci.yml
ios-ci.yml
schema-tests.yml
sample-build.yml
release.yml
```

Android CI:

- unit tests
- lint
- assemble sample
- publish local test artifact

iOS CI:

- swift test
- build sample
- package validation

---

# 81. Release CI

On tag:

```text
v0.1.0
```

Actions:

- verify changelog
- run all tests
- build Android artifacts
- build Swift package
- generate checksums
- create GitHub release
- publish package artifacts

---

# 82. Versioning

Use SemVer.

```text
0.x
```

may evolve faster.

After 1.0:

- preserve public API
- preserve export compatibility where documented
- deprecate before breaking

---

# 83. Issue/Milestone Structure

Milestones:

```text
M0 Skeleton
M1 Shared core
M2 Android WorkManager
M3 Android UI + diagnosis
M4 iOS BGTask
M5 iOS UI + diagnosis
M6 Export + Kourier
M7 Public 0.1
```

Labels:

```text
area:core
area:android
area:ios
area:workmanager
area:bgtask
area:ui
area:diagnosis
area:storage
area:export
area:kourier
type:bug
type:feature
type:docs
priority:p0
priority:p1
priority:p2
```

---

# 84. Delivery Strategy

Do not attempt full Android + iOS production parity on day one.

Recommended order:

```text
Phase 0
Shared contracts + event schema

Phase 1
Android WorkManager end-to-end

Phase 2
Android polished release

Phase 3
iOS BGTaskScheduler end-to-end

Phase 4
iOS polished release

Phase 5
Kourier integration

Phase 6
cross-platform export parity

Phase 7
additional schedulers
```

---

# 85. Phase 0 Tasks

- create repo
- configure Gradle
- configure SPM
- add docs
- define event schema
- define export schema
- define domain contracts
- define diagnosis contracts
- build fake event sources

---

# 86. Phase 1 Android

Build:

- TaskLens.install
- Room store
- runtime collectors
- WorkManager observer
- event normalization
- timeline builder
- Android sample

Exit:

A worker's lifecycle is visible end-to-end.

---

# 87. Phase 2 Android Polish

Build:

- diagnosis engine
- Compose UI
- export
- no-op artifact
- privacy
- performance benchmark
- launch demo

Exit:

Developer can diagnose a realistic WorkManager failure faster than with Logcat.

---

# 88. Phase 3 iOS

Build:

- TaskLens.install
- iOS store
- BGTask wrappers
- NWPathMonitor
- power monitor
- background refresh monitor
- capability validator
- sample app

Exit:

A BGTask request can be traced from submission to launch/completion/expiration.

---

# 89. Phase 4 iOS Polish

Build:

- SwiftUI
- diagnosis rules
- export
- no-op
- docs
- sample scenarios

Exit:

A developer can understand iOS background task behavior from one on-device timeline.

---

# 90. Phase 5 Kourier Bridge

Android + iOS.

Build:

- common network telemetry bridge
- correlation ids
- task detail network section
- export integration

---

# 91. HLD Component Contracts

## Event source

```kotlin
interface EventSource {
    fun start()
    fun stop()
}
```

## Event sink

```kotlin
interface EventSink {
    suspend fun emit(event: TaskLensEvent)
}
```

## Timeline builder

```kotlin
interface TimelineBuilder {
    fun build(
        task: ScheduledWork,
        events: List<TaskLensEvent>
    ): TaskTimeline
}
```

## Diagnosis engine

```kotlin
interface DiagnosisEngine {
    fun diagnose(
        context: DiagnosisContext
    ): List<Diagnosis>
}
```

## Exporter

```kotlin
interface TaskLensExporter {
    suspend fun export(taskId: TaskId): ExportArtifact
}
```

---

# 92. LLD Event Pipeline

```text
Platform callback
   ↓
Adapter maps callback
   ↓
TaskLensEvent created
   ↓
redaction
   ↓
EventSink.emit
   ↓
in-memory channel
   ↓
persistent store
   ↓
correlation engine
   ↓
task/attempt update
   ↓
diagnosis invalidation
   ↓
UI state refresh
```

---

# 93. Concurrency Model

Android:

- CoroutineScope(SupervisorJob + Dispatchers.Default/IO)
- Channel for events
- serialized store writes

iOS:

- actor for event processing
- actor or serial queue for persistence
- MainActor only for UI state updates

---

# 94. Backpressure

If event rate spikes:

- buffer bounded channel
- coalesce high-frequency environment changes
- never drop terminal task events
- low-priority environment events may be deduplicated

Priority:

```text
CRITICAL
TASK_STATE
ENVIRONMENT
DEBUG
```

---

# 95. Deduplication

Environment signals often repeat.

Deduplicate identical consecutive values within a configurable window.

Example:

```text
NETWORK_CONNECTED
NETWORK_CONNECTED
NETWORK_CONNECTED
```

store once unless meaningful metadata changes.

---

# 96. Event Immutability

Events are immutable after persistence.

Corrections must be additional events.

This keeps trace history auditable.

---

# 97. Attempt Reconstruction

The correlator builds attempts from:

```text
TASK_STARTED
TASK_STOPPED
TASK_RETRY_REQUESTED
TASK_SUCCEEDED
TASK_FAILED
TASK_EXPIRED
```

If start is missing:

create partial attempt with:

```text
startedAt = null
```

Never invent timestamps.

---

# 98. Platform Limitation Model

Add:

```kotlin
data class PlatformLimitation(
    val code: String,
    val message: String
)
```

Examples:

```text
IOS_SCHEDULER_REASON_NOT_EXPOSED
IOS_DIRECT_COMPLETION_NOT_OBSERVABLE_WITHOUT_WRAPPER
ANDROID_PROCESS_TERMINATION_BEST_EFFORT
NETWORK_CORRELATION_TIME_BASED
```

These should appear in diagnosis/export where relevant.

---

# 99. Capability Matrix

| Capability | Android | iOS |
|---|---:|---:|
| submission observed | Yes | Yes with wrapper |
| task launch observed | Yes | Yes |
| retry observed | Yes | app-defined / resubmission-dependent |
| exact scheduler reason | Partial | No |
| stop reason | Partial/strong on supported APIs | expiration/completion only |
| network state | Yes | Yes |
| battery/power state | Yes | Yes |
| app lifecycle | Yes | Yes |
| process lifecycle | Best effort | Best effort |
| constraints | Strong | Partial |
| expiration | N/A equivalent varies | Yes |
| export | Yes | Yes |
| Kourier bridge | Yes | Yes |

---

# 100. Security

- no remote execution
- no remote command channel
- no secrets persisted by default
- secure temporary export files
- user-approved sharing
- document production/no-op guidance

---

# 101. Threat Model

Risks:

- accidental PII capture
- auth token capture
- oversized traces
- malicious custom breadcrumbs
- exported trace leakage

Mitigations:

- redaction
- metadata-only defaults
- size limits
- export review
- no cloud upload
- retention

---

# 102. Size Limits

Recommended:

```text
single attribute: 2 KB
custom breadcrumb payload: 4 KB
single event attributes JSON: 16 KB
trace export default max: 10 MB
```

Truncate safely with marker.

---

# 103. Crash Safety

Do not throw from:

```text
TaskLens.install
TaskLens.breadcrumb
TaskLens.show
TaskLens event collectors
```

unless API explicitly documents a recoverable export error.

---

# 104. Documentation Requirements Before Launch

README must contain:

- problem
- 30-second integration
- screenshot
- supported platforms
- limitations
- privacy
- sample
- Kourier integration
- roadmap

Separate docs:

```text
ANDROID.md
IOS.md
ARCHITECTURE.md
DIAGNOSIS_ENGINE.md
EXPORT_FORMAT.md
PRIVACY.md
```

---

# 105. README Hero

Recommended:

```markdown
# TaskLens

### Your background task didn't run. TaskLens tells you why.

TaskLens is an on-device background execution flight recorder
for Android and iOS.

It captures scheduler events, constraints, retries, expiration,
power/network conditions and task outcomes, then turns them into
a readable timeline and evidence-backed diagnosis.
```

---

# 106. Launch Positioning

Do not market:

```text
"background telemetry SDK"
```

Market:

```text
"Why didn't my background task run?"
```

Examples:

> Your WorkManager task didn't run. TaskLens shows the complete execution story.

> Your iOS BGTask expired. TaskLens shows when, under what conditions, and what the platform actually exposed.

---

# 107. Quality Bar

Before release ask:

1. Can a developer understand the execution story from one screen?
2. Is every diagnosis evidence-backed?
3. Are platform limitations explicit?
4. Can QA use it without a laptop?
5. Can the trace be exported safely?
6. Does TaskLens stay out of the host app's way?

---

# 108. Definition of Done for Android 0.1

- WorkManager supported
- accurate timeline
- retry chain visible
- network constraints visible
- battery/power context visible
- diagnosis rules implemented
- Room persistence
- Compose debugger
- export
- no-op
- Kourier bridge optional
- sample app
- CI
- docs

---

# 109. Definition of Done for iOS 0.1

- BGTask registration wrapper
- request submission wrapper
- launch tracking
- expiration tracking
- completion wrapper
- NWPathMonitor
- low power mode
- background refresh status
- capability validator
- SwiftUI debugger
- export
- no-op
- sample app
- CI
- docs

---

# 110. Future Android Extensions

## JobScheduler

Normalize:

```text
job scheduled
job started
job stopped
reschedule requested
```

## ForegroundService

Normalize:

```text
start requested
service created
foreground promoted
stopped
timeout/restriction
```

## AlarmManager

Normalize:

```text
scheduled
expected trigger
actual receive
delay
```

---

# 111. Future iOS Extensions

- silent push correlation
- background fetch compatibility where relevant
- location-triggered background execution
- richer URLSession relaunch events
- multi-day task pattern analysis
- app reinstall/version boundary analysis

---

# 112. Future Cloud Opportunity

Optional TaskLens Cloud could provide:

- trace ingestion
- team history
- device clustering
- OEM comparison
- release regression detection
- scheduler failure trends
- fleet-wide worker health

Example:

```text
PaymentSyncWorker failure rate

Samsung Android 16   11.4%
Pixel Android 16      1.8%
OnePlus Android 15    7.1%
```

Open-source local TaskLens remains useful without cloud.

---

# 113. Future AI Opportunity

Later:

```text
Explain this execution
```

AI may summarize grounded evidence.

Constraint:

The AI must not invent cause.

Architecture:

```text
structured timeline
   ↓
deterministic evidence
   ↓
LLM summary
```

Never:

```text
raw telemetry
   ↓
LLM guesses root cause
```

---

# 114. Long-Term Ecosystem

```text
                     Mobile Reliability

                         Observe
                            │
        ┌───────────────────┼──────────────────┐
        ↓                   ↓                  ↓
     Kourier             TaskLens          RouteProbe
     Network             Background        Navigation
        │                   │                  │
        └───────────────────┼──────────────────┘
                            ↓
                         Explain
                            ↓
                        Turbulence
                        Reproduce
                            ↓
                         BugBundle
                          Export
```

---

# 115. Immediate Development Order

```text
1. Create dev-shushant/tasklens
2. Commit this SPEC.md
3. Create Gradle + SPM skeleton
4. Define shared event schema
5. Implement fake event sources
6. Implement Android storage
7. Implement WorkManager adapter
8. Implement Android runtime collectors
9. Build Android timeline
10. Build Android diagnosis
11. Build Android UI
12. Add export
13. Build iOS storage
14. Build BGTask wrappers
15. Build iOS environment collectors
16. Build iOS timeline
17. Build iOS diagnosis
18. Build SwiftUI
19. Add Kourier bridge
20. Add no-op artifacts
21. Benchmark
22. Document
23. Release 0.1.0
```

---

# 116. Suggested First 20 GitHub Issues

1. Bootstrap Gradle project
2. Bootstrap Swift Package
3. Define TaskLensEvent schema
4. Define ScheduledWork model
5. Define ExecutionAttempt model
6. Define Evidence model
7. Define Diagnosis model
8. Implement Android Room schema
9. Implement Android event pipeline
10. Implement WorkManager state observer
11. Implement Android network collector
12. Implement Android power collector
13. Build timeline builder
14. Add retry diagnosis
15. Add constraint diagnosis
16. Build Compose task feed
17. Build Compose task detail
18. Implement `.tasklens` exporter
19. Implement iOS event store
20. Implement BGTask register/submit wrappers

---

# 117. Architecture Decision Records to Create

```text
ADR-001 Separate TaskLens repository
ADR-002 Android-first implementation
ADR-003 Deterministic diagnosis before AI
ADR-004 Platform-native UI
ADR-005 Shared domain, platform-native adapters
ADR-006 Kourier optional bridge
ADR-007 Local-first storage
ADR-008 Versioned export schema
ADR-009 No-op production artifact
ADR-010 Platform limitations are first-class data
```

---

# 118. North Star Acceptance Test

TaskLens succeeds when:

> A developer can open one task and say:  
> **“Now I know what happened, what the platform told me, and what remains uncertain.”**

That is the product.

Everything else is infrastructure supporting that moment.
