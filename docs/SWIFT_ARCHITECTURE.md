# Swift Architecture & Canonical Core Interoperability Audit

This document details the architectural boundaries between the canonical Kotlin Multiplatform core (`tasklens-core-kmp`) and the native Swift modules (`Sources/*`).

---

## 1. Single Source of Truth Principle

All shared domain models, serialization specifications, event schemas, sequence ordering semantics, and deterministic diagnosis rule logic originate **exclusively** from `tasklens-core-kmp/src/commonMain`.

The Swift modules in `Sources/` serve solely as:
1. **Idiomatic Swift Type Adapters**: Exposing Swift `Codable`, `Identifiable`, and `Sendable` types that mirror the canonical KMP JSON schema.
2. **Apple Platform System Bindings**: Interacting directly with Darwin and iOS C/ObjC/Swift system frameworks (`BackgroundTasks`, `Foundation`, `Network`, `libsqlite3.dylib`, `SwiftUI`).

---

## 2. Inventory of Swift Source Files & Rationale

| Module | File | Responsibility | Why It Cannot Live in `commonMain` |
| :--- | :--- | :--- | :--- |
| **`TaskLensCore`** | `Models.swift` | Swift `Codable` structs (`ScheduledWork`, `ExecutionAttempt`, `PlatformReason`, `NetworkState`) | Pure Swift type declarations required by Swift Package Manager (SPM) clients for native zero-overhead Swift compilation without invoking the Kotlin compiler during consumer app builds. |
| **`TaskLensCore`** | `Events.swift` | Swift `TaskLensEvent` struct, `EventType`, `EventSource`, `EventSeverity`, atomic `SequenceCounter` | Native Swift concurrency-safe atomic sequence generation and typed enum mapping matching KMP schema. |
| **`TaskLensCore`** | `Diagnosis.swift` | Swift `Diagnosis`, `DiagnosisClassification`, `DiagnosisConfidence` | Type declarations for Swift clients consuming diagnosis objects. |
| **`TaskLensCore`** | `Evidence.swift` | Swift `Evidence`, `EvidenceType`, `EvidenceSource` | Type declarations for diagnostic evidence chains in Swift. |
| **`TaskLensCore`** | `Redactor.swift` | Swift `TaskLensRedactor` and `DefaultTaskLensRedactor` | Zero-allocation regex redaction on Darwin string types. |
| **`TaskLensCore`** | `Timeline.swift` | Chronological event sorting and timeline representation | Native Swift collection helpers. |
| **`TaskLensDiagnosis`** | `DiagnosisEngine.swift` | Protocol `DiagnosisEngine` and `DefaultDiagnosisEngine` | Swift protocol adapter executing diagnosis candidates. |
| **`TaskLensDiagnosis`** | `DiagnosisRule.swift` | Protocol `DiagnosisRule`, `DiagnosisContext`, `DiagnosisCandidate` | Swift protocol definitions for custom platform rules. |
| **`TaskLensDiagnosis`** | `Rules.swift` | Canonical rule wrappers (`BGTaskExpiredRule`, `BGTaskFailureRule`, `CapabilityMismatchRule`, `SchedulerDelayRule`) | Wrappers invoking the canonical rule evaluation with iOS-specific context. |
| **`TaskLensBGTasks`** | `TaskLensBG.swift` | `BGTaskScheduler` registration, request submission, expiration handler wrapping, simulation APIs | Interacts with Apple's `BackgroundTasks.framework` (`BGTaskScheduler`, `BGProcessingTaskRequest`, `BGAppRefreshTaskRequest`). `BackgroundTasks` is an iOS-only framework completely absent from JVM and KMP `commonMain`. |
| **`TaskLensURLSession`**| `TaskLensURLSession.swift` | Background `URLSession` event and completion telemetry | Integrates directly with Apple's `URLSessionConfiguration.background(withIdentifier:)` and `URLSessionTaskDelegate`. |
| **`TaskLensStorage`** | `SQLiteTaskLensStore.swift` | Persistent SQLite engine, WAL mode, atomic transactions, retention pruning | Links directly to Darwin's native `libsqlite3.dylib` via C API (`sqlite3_open_v2`, `sqlite3_prepare_v2`, `sqlite3_step`). Pure JVM/commonMain has no C SQLite interop without platform-specific drivers. |
| **`TaskLensStorage`** | `TaskLensStore.swift` | Swift async `TaskLensStore` actor protocol | Idiomatic Swift protocol utilizing Swift structured concurrency (`actor`, `async/await`). |
| **`TaskLensUI`** | `TaskFeedView.swift` | Inspector view, task timeline, diagnosis detail sheet | Built with Apple's `SwiftUI` framework (`View`, `@State`, `NavigationView`, `ScrollView`, `VStack`). SwiftUI is exclusive to Apple operating systems. |
| **`TaskLensNoop`** | `TaskLensNoop.swift` | Zero-overhead empty implementations for release builds | Compile-time drop-in replacement for SPM release configurations. |
| **`TaskLensKourierBridge`**| `TaskLensKourierBridge.swift`| Kourier networking bridge for iOS | iOS telemetry forwarding. |
| **`TaskLens`** | `TaskLens.swift` | Public iOS SDK facade (`install`, `emit`, `show`, `export`) | Primary developer-facing entry point on Apple platforms. |
| **`TaskLens`** | `TaskLensArchiveExporter.swift` | `.tasklens` ZIP bundle writer | Pure Swift compression using Darwin standard library and atomic file writes. |

---

## 3. Automated Integrity Enforcement

Architectural boundary rules are enforced by automated test suites:
- **`IOSCanonicalCoreIntegrityTests.swift`**: Fails CI if divergent rule IDs, non-canonical classifications, or conflicting model schemas are introduced.
- **`CanonicalCoreIntegrityTest.kt`**: Fails CI if duplicate source files are reintroduced into legacy JVM core modules.
