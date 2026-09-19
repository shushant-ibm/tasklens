# ADR-009: WorkManager Stop Reason Mapping and Worker Tracing

## Status
Accepted

## Context
In Android 12 (API 31) and Android 14 (API 34), Google introduced stop reasons in `WorkInfo.getStopReason()`. However, these are represented as opaque integers (`STOP_REASON_CONSTRAINT_CONNECTIVITY = 7`, `STOP_REASON_DEVICE_STATE = 4`, `STOP_REASON_TIMEOUT = 3`), which developers frequently fail to inspect or decode properly.

## Decision
TaskLens maps all Android `WorkInfo.STOP_REASON_*` constants into:
1. Standardized enum constants and readable strings.
2. Direct integration in `WorkManagerAdapter` via `WorkQuery` observers.
3. First-class coroutine tracing via `TaskLens.trace(worker)` which catches exceptions, counts run attempts, and records start/stop events.

## Consequences
### Positive
- Developers no longer need to look up Android source code to understand why a worker stopped.
- Clear, immediate feedback displayed in the on-device Compose UI.

### Negative
- As new Android API levels add new stop reason integer constants, TaskLens must update its mapping catalog.
