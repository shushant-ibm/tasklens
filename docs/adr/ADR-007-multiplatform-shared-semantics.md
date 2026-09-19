# ADR-007: Multiplatform Shared Semantics and Swift Package Architecture

## Status
Accepted

## Context
Enterprise organizations maintain both Android and iOS mobile applications. Having divergent terminology, diagnostic classifications, or file formats makes cross-platform telemetry analysis nearly impossible.

## Decision
TaskLens maintains 1:1 structural and conceptual parity between Kotlin and Swift implementations:
- Exact matching enum strings (`DiagnosisClassification`, `DiagnosisConfidence`, `EventType`, `EventSource`, `EventSeverity`).
- Shared export bundle structure (`.tasklens`).
- Parallel Swift Package structure (`TaskLensCore`, `TaskLensDiagnosis`, `TaskLensStorage`, `TaskLensBGTasks`, `TaskLensURLSession`, `TaskLensUI`, `TaskLensNoop`, `TaskLensKourierBridge`).

## Consequences
### Positive
- Cross-platform teams share the same vocabulary and documentation.
- Telemetry dashboards and backend processors consume identical event formats.

### Negative
- Updates to core data structures or rule semantics must be mirrored across Kotlin and Swift codebases.
