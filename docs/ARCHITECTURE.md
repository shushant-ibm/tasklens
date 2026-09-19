# TaskLens Architecture & Design Principles

## 1. Core Philosophy

Modern mobile applications rely heavily on background execution for synchronization, telemetry, cache invalidation, media processing, and offline data sync. However, modern operating systems (Android 6.0+ Doze, Android 12+ Phantom Process Killer, Android 14+ foreground service types, iOS BGTaskScheduler heuristics) treat background execution as an adversarial resource risk.

When a background job fails or does not execute, engineers are left with cryptic symptoms:
- "The task never ran."
- "The worker was cancelled after 2 seconds."
- "The task succeeded on Wi-Fi in the lab, but fails in production."

TaskLens was engineered around three inviolable tenets:

1. **Explain, Do Not Merely Log**: Logging tells you that an event happened at time $T$. Diagnosis tells you *why* the task stopped, what the OS constraints were at time $T$, and provides concrete remediation advice.
2. **Evidence Before Inference**: Every diagnostic statement must be backed by an auditable evidence chain consisting of recorded lifecycle events and platform state transitions.
3. **On-Device First & Zero Overhead**: The SDK must function 100% offline without mandatory external cloud infrastructure or backend servers. When compiled in release configurations using `tasklens-noop`, the overhead is absolute zero.

---

## 2. System Architecture

TaskLens follows a strictly decoupled, unidirectional reactive architecture:

```
[System Events: Doze, Network, Battery] ───┐
                                          ├──► [Bounded Channel] ──► [Persistence] ──► [Diagnosis Engine] ──► [UI / Export]
[Scheduler Events: WorkManager, BGTasks] ──┘         (1000 items)     (SQLite / Room)   (Deterministic Rules)   (Compose / SwiftUI / .tasklens)
```

### Decoupled Subsystems

- **Core Abstractions (`tasklens-core`, `TaskLensCore`)**:
  Defines the domain models (`ScheduledWork`, `TaskLensEvent`, `ExecutionAttempt`, `DiagnosisCandidate`, `Evidence`). Completely pure with zero Android or iOS platform dependencies.

- **Persistence Subsystem (`tasklens-storage`, `TaskLensStorage`)**:
  Manages task and event persistence. Implements automated rolling retention policies bounded by both temporal limits (e.g. 7 days) and storage size limits (e.g. 25 MB).

- **Environment & Lifecycle Collectors (`tasklens-android`, `TaskLensBGTasks`)**:
  Listens to OS broadcasts, `ConnectivityManager.NetworkCallback`, `BatteryManager`, and `PowerManager` (Doze status). Emits structured `TaskLensEvent` objects into a bounded channel.

- **Deterministic Diagnosis Engine (`tasklens-diagnosis`, `TaskLensDiagnosis`)**:
  Evaluates a prioritized rule chain over an execution context containing the task configuration, attempts, and surrounding environment timeline. Computes a confidence score (`CONFIRMED`, `LIKELY`, `POSSIBLE`) and compiles an evidence trail.

- **Presentation & Export (`tasklens-ui`, `tasklens-export`, `TaskLensUI`)**:
  Provides zero-setup developer UI to scrub timelines and view diagnoses on-device. Packages full diagnostic bundles into `.tasklens` ZIP archives containing structured JSON and offline interactive HTML.

---

## 3. Non-Blocking Ingestion Pipeline

To ensure that TaskLens never degrades application responsiveness or starves application threads, all events are ingested through a bounded non-blocking channel:

- **Buffer Size**: 1,000 events.
- **Overflow Strategy**: `BufferOverflow.DROP_OLDEST`.
- **Worker Coroutine**: Runs on `Dispatchers.Default` under a `SupervisorJob`.
- **Sensitive Key Redaction**: Evaluated synchronously prior to storage writes so unredacted secrets never touch SQLite or disk caches.

---

## 4. Multiplatform Parity & Shared Semantics

TaskLens is designed from day one with structural parity across Android and iOS:

| Concept | Android (`tasklens-*`) | iOS (`TaskLens*`) |
|:--------|:-----------------------|:-------------------|
| Task Representation | `ScheduledWork` | `ScheduledWork` |
| Event Structure | `TaskLensEvent` | `TaskLensEvent` |
| Execution Attempt | `ExecutionAttempt` | `ExecutionAttempt` |
| Diagnosis Model | `DiagnosisCandidate` | `DiagnosisCandidate` |
| Evidence Model | `Evidence` | `Evidence` |
| Storage Interface | `TaskLensStore` | `TaskLensStore` |
| Diagnosis Engine | `DiagnosisEngine` | `DiagnosisEngine` |
| Ingestion Entry | `TaskLens.emit(event)` | `TaskLens.emit(event)` |
| Redaction Policy | `TaskLensRedactor` | `TaskLensRedactor` |
| Release Replacement | `tasklens-noop` | `TaskLensNoop` |

Both implementations adhere to the unified event taxonomy defined in `specs/SPEC.md`.
