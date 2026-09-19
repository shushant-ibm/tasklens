# TaskLens Project Roadmap

This document outlines upcoming capabilities, scheduled enhancements, and future milestones for the TaskLens diagnostics ecosystem.

---

## Milestone 1: Foundation (v0.1.0) - Current Release

- [x] Unified cross-platform event model (`TaskLensEvent`, `ScheduledWork`, `ExecutionAttempt`).
- [x] Deterministic Diagnosis Engine with priority rule chains and confidence ratings.
- [x] Android SQLite persistent store with automated size and time retention policies.
- [x] Android environment monitors (NetworkCallback, BatteryManager, PowerManager Doze, AppState).
- [x] WorkManager integration adapter with automated `WorkInfo` lifecycle mapping and stop reason explanations.
- [x] Jetpack Compose developer UI (`TaskLensActivity`, timeline scrubber, diagnosis card).
- [x] Portable `.tasklens` offline ZIP export with standalone interactive `README.html`.
- [x] iOS Swift Package parity (`TaskLensCore`, `TaskLensDiagnosis`, `TaskLensStorage`, `TaskLensBGTasks`, `TaskLensUI`).
- [x] Zero-overhead release no-op artifacts (`tasklens-noop`, `TaskLensNoop`).
- [x] Enterprise telemetry bridge (`tasklens-kourier`, `TaskLensKourierBridge`).

---

## Milestone 2: Deeper Platform Intelligence (v0.2.0)

- [ ] **Android 14+ Foreground Service Types**: Dedicated diagnostics for `dataSync`, `mediaPlayback`, and `location` restrictions.
- [ ] **App Standby Buckets & Battery Exemptions**: Detailed tracking of standby bucket transitions (`ACTIVE`, `WORKING_SET`, `FREQUENT`, `RARE`, `RESTRICTED`) and power-allowlist changes.
- [ ] **iOS Powerlog / MetricKit Integration**: Ingestion of `MXBackgroundExitData` (e.g. CPU spikes, memory termination in background).
- [ ] **JobScheduler & AlarmManager Deep Adapters**: First-class tracking for legacy Android Enterprise scenarios where WorkManager is not utilized.

---

## Milestone 3: Desktop & Cloud Visualizer (v0.3.0)

- [ ] **TaskLens Web Studio**: Open-source web app (running locally in browser via WebAssembly or React) to drop `.tasklens` files for multi-device comparisons and fleet timeline diffing.
- [ ] **CLI Inspection Tool**: `tasklens inspect <bundle.tasklens>` for headless terminal diagnostics in CI test runs.
- [ ] **KMP Native Harmonization**: Full Kotlin Multiplatform unified core target sharing binary logic across Android, iOS, JVM, and macOS.
