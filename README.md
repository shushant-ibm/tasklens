# TaskLens

> **"Your background task didn't run. TaskLens tells you why."**

[![Kotlin](https://img.shields.io/badge/Kotlin-2.4-purple.svg?style=flat-square&logo=kotlin)](https://kotlinlang.org)
[![Android SDK](https://img.shields.io/badge/Android%20SDK-26%2B%20(Target%2035)-green.svg?style=flat-square&logo=android)](https://developer.android.com)
[![Swift](https://img.shields.io/badge/Swift-5.9%2B-orange.svg?style=flat-square&logo=swift)](https://swift.org)
[![iOS](https://img.shields.io/badge/iOS-14.0%2B-lightgrey.svg?style=flat-square&logo=apple)](https://developer.apple.com/ios/)
[![License: Apache 2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg?style=flat-square)](LICENSE)

**TaskLens** is an on-device diagnostics and explainability engine for mobile background execution across Android (`WorkManager`, `JobScheduler`, `ForegroundService`, `AlarmManager`) and iOS (`BGTaskScheduler`, `URLSessionBackground`).

Unlike generic APM platforms or raw loggers that merely record timestamped strings, TaskLens applies a deterministic **Diagnosis Engine** to correlate platform environment conditions (Doze, Battery Saver, Network changes, Thermal throttling, App Standby buckets) with scheduler constraints, producing human-readable explanations, deterministic classifications, and auditable evidence chains.

---

## Key Capabilities

- 🔍 **Explain, Do Not Merely Log**: Automatically correlates OS environment events with scheduler failures and yields exact root causes (e.g., `STOP_REASON_DEVICE_STATE` due to Battery Saver entering during execution).
- ⛓️ **Evidence Before Inference**: Every diagnosis candidate is backed by concrete evidence items referencing exact events and OS state transitions.
- 📱 **Embedded Developer UI**: Built with Jetpack Compose (Android) and SwiftUI (iOS) for rapid on-device debugging, timeline scrubbing, and state inspection.
- 📦 **Offline Export Bundles (`.tasklens`)**: Generates self-contained ZIP packages containing structured `manifest.json`, `events.jsonl`, `diagnosis.json`, environment snapshots, and an offline interactive `README.html` report.
- 🔒 **Privacy-First By Design**: Built-in automatic redaction for auth headers, tokens, credentials, and PII. No payload capture by default.
- ⚡ **Zero-Overhead No-Op Artifacts**: Dedicated `-noop` modules for release builds compile down to zero-allocation no-op stubs with zero runtime dependencies.
- 🌉 **Kourier Telemetry Bridge**: First-class optional bridge to export diagnostic summaries to backend telemetry systems (such as Kourier) without adding cloud dependencies to core modules.

---

## Architecture Overview

```
+-------------------------------------------------------------------------+
|                              Application Layer                          |
|   (SyncWorker, DownloadService, BGAppRefreshTask, URLSession Tasks)     |
+-------------------------------------------------------------------------+
                                     │
                                     ▼
+-------------------------------------------------------------------------+
|                           Framework Adapters                            |
|  tasklens-workmanager  │ tasklens-jobscheduler │ tasklens-bgtasks (iOS) |
+-------------------------------------------------------------------------+
                                     │
                                     ▼
+-------------------------------------------------------------------------+
|                             TaskLens Core                               |
|   • Unified Event Model (TaskLensEvent, ScheduledWork, ExecutionAttempt)|
|   • Async Non-Blocking Bounded Pipeline (Drop-Oldest Safety Channel)    |
|   • Sensitive Key & Value Redactor                                      |
+-------------------------------------------------------------------------+
            │                                             │
            ▼                                             ▼
+───────────────────────────+                 +───────────────────────────+
|     Persistence Layer     |                 |     Diagnosis Engine      |
|  • Android SQLite / Room  |                 |  • Deterministic Rules    |
|  • In-Memory / File Store |                 |  • Confidence Scoring     |
|  • Time & Size Retention  |                 |  • Evidence Chain Builder |
+───────────────────────────+                 +───────────────────────────+
            │                                             │
            ▼                                             ▼
+─────────────────────────────────────────────────────────────────────────+
|                           Presentation & Export                         |
|   • Material 3 Jetpack Compose Inspector (`TaskLensActivity`)           |
|   • SwiftUI Inspector (`TaskLensView`)                                  |
|   • `.tasklens` Self-Contained ZIP & Interactive HTML Exporter          |
|   • `tasklens-kourier` Telemetry Bridge                                 |
+─────────────────────────────────────────────────────────────────────────+
```

---

## 30-Second Quickstart (Android)

### 1. Add Dependencies

Following the same minimal integration model as [Kourier](https://github.com/dev-shushant/kourier), the host application adds **only one dependency**:

```kotlin
dependencies {
    // Debug & Staging builds: Complete diagnostics engine, WorkManager tracing, and UI
    debugImplementation("dev.shushant.tasklens:tasklens-android:0.1.0")

    // Release builds: Completely empty stubs, zero overhead, zero transitive dependencies
    releaseImplementation("dev.shushant.tasklens:tasklens-noop:0.1.0")
}
```

Internal modules (`tasklens-storage`, `tasklens-diagnosis`, `tasklens-export`, `tasklens-ui`) are strictly encapsulated and never leaked to the host application's compile classpath.

### 2. Initialize in Application

```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // 1-line idiomatic initialization with builder DSL:
        TaskLens.install(this) {
            retention(days = 7, maxTasks = 500)
            captureEnvironment = true     // Monitors Network, Battery, Doze, and Thermal state
            autoObserveWorkManager = true // Automatically tracks all WorkManager jobs
        }
    }
}
```

### 3. Trace Workers

```kotlin
import dev.shushant.tasklens.android.TaskLens
import dev.shushant.tasklens.android.trace

class SyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        return TaskLens.trace(this) {
            // Your business logic here...
            Result.success()
        }
    }
}
```

### 4. Launch On-Device Inspector

```kotlin
// Open anywhere without declaring activities in AndroidManifest.xml:
TaskLens.showUI(context)
```

---

## 30-Second Quickstart (iOS)

### 1. Add Swift Package

In your `Package.swift`:

```swift
dependencies: [
    .package(url: "https://github.com/shushanttiwari/tasklens.git", from: "0.1.0")
]
```

### 2. Configure Background Tasks

```swift
import TaskLens
import TaskLensBGTasks

@main
struct MyApp: App {
    init() {
        TaskLens.install(config: TaskLensConfig())

        TaskLensBG.register(identifier: "dev.shushant.tasklens.refresh") { task in
            handleRefresh(task: task)
        }
    }
}
```

---

## Diagnosis Rule Catalog

TaskLens ships with a deterministic suite of rules evaluated in strict priority order:

| Rule ID | Name | Classification | Confidence | Trigger Condition |
|:--------|:-----|:---------------|:-----------|:-------------------|
| `RULE_PLATFORM_STOP_REASON` | OS Stop Reason | `PLATFORM_KILLED` | Confirmed | WorkManager `stopReason` recorded (`BATTERY_SAVER`, `TIMEOUT`, `PRECONDITION`) |
| `RULE_NETWORK_CONSTRAINT` | Network Constraint Violated | `CONSTRAINT_NOT_MET` | Confirmed | Task required network connection but device was disconnected |
| `RULE_NETWORK_INTERRUPTED` | Network Interrupted In-Flight | `NETWORK_LOST_DURING_EXEC` | Likely | Device network disconnected while task was in running state |
| `RULE_RETRY_REQUESTED` | Explicit Retry Scheduled | `RETRY_EXHAUSTED` / `RETRY` | Confirmed | Worker requested retry via `Result.retry()` |
| `RULE_TASK_EXPIRED` | OS Quota Expired | `TASK_EXPIRED` | Confirmed | iOS `expirationHandler` fired before completion |
| `RULE_APPLICATION_FAILURE` | Task Returned Failure | `APPLICATION_FAILURE` | Confirmed | Worker returned `Result.failure()` |
| `RULE_APPLICATION_CANCELLED` | Explicit Cancellation | `APPLICATION_CANCELLED` | Confirmed | Work cancelled by developer via `cancelWorkById` |
| `RULE_CAPABILITY_MISMATCH` | Capability Mismatch | `CONFIGURATION_PROBLEM` | Confirmed | Identifier missing from `Info.plist` or manifest misconfiguration |
| `RULE_SCHEDULER_DELAY` | Scheduler Deferral | `SCHEDULER_DELAY` | Possible | Task submitted but pending OS dispatch heuristics |

---

## Export Bundle Specification (`.tasklens`)

The `.tasklens` archive is an offline, standalone ZIP package structured as:

```
tasklens_export_<timestamp>.tasklens (ZIP archive)
├── manifest.json       # Metadata, SDK version, device specs, platform info
├── tasks.json          # Scheduled tasks and metadata
├── events.jsonl        # High-volume append-only timestamped event log
├── attempts.json       # Execution attempts, start/finish, durations, exit codes
├── diagnoses.json      # Structured root-cause diagnoses, rules, evidence chains
├── environment.json    # Snapshots of battery, network, thermal, OS power mode
└── README.html         # Self-contained, responsive, CSS-styled interactive report
```

Double-clicking `README.html` extracts and opens directly in any modern browser with zero server dependencies.

---

## Module Matrix

| Module | Platform | Description |
|:-------|:---------|:------------|
| `:tasklens-core` | JVM / KMP | Data models, event contracts, redactor, correlation engine |
| `:tasklens-storage` | JVM / KMP | Storage abstractions, in-memory store, retention policies |
| `:tasklens-diagnosis`| JVM / KMP | Deterministic diagnosis rules and evidence evaluators |
| `:tasklens-export` | JVM / KMP | JSON serializers, ZIP bundler, self-contained HTML report generator |
| `:tasklens-android` | Android | SQLite persistent store, Android battery/network/power collectors |
| `:tasklens-workmanager` | Android | WorkManager observer, stop reasons, `TaskLens.trace(worker)` |
| `:tasklens-jobscheduler`| Android | JobScheduler integration contracts |
| `:tasklens-foreground` | Android | Foreground Service tracking contracts |
| `:tasklens-alarm` | Android | Exact Alarm tracking contracts |
| `:tasklens-ui` | Android | Jetpack Compose inspector activity and components |
| `:tasklens-kourier` | Android / JVM| Kourier telemetry bridge adapter |
| `:tasklens-noop` | Android | Zero-overhead empty stubs for release builds |
| `TaskLens (SPM)` | iOS | Swift Package containing Core, Diagnosis, Storage, BGTasks, UI |

---

## Documentation

Explore the detailed architecture guides in [`docs/`](docs/):
- [Architecture & Design Principles](docs/ARCHITECTURE.md)
- [Android Integration Guide](docs/ANDROID.md)
- [iOS Integration Guide](docs/IOS.md)
- [Diagnosis Engine Specification](docs/DIAGNOSIS_ENGINE.md)
- [Privacy & Redaction Boundary](docs/PRIVACY.md)
- [Export Bundle Specification](docs/EXPORT_FORMAT.md)
- [Kourier Telemetry Integration](docs/KOURIER_INTEGRATION.md)
- [API Stability & Deprecation Policy](docs/API_STABILITY.md)
- [Project Roadmap](docs/ROADMAP.md)
- [Architecture Decision Records (ADRs)](docs/adr/)

---

## License

```
Copyright 2026 TaskLens Authors.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0
```
