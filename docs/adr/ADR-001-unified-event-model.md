# ADR-001: Unified Cross-Platform Event Model

## Status
Accepted

## Context
Background tasks across mobile operating systems (Android WorkManager, JobScheduler, AlarmManager, Foreground Services; iOS BGTaskScheduler, URLSession background transfers) have disparate APIs, terminology, and lifecycle callbacks. A unified diagnostics platform must map these divergent abstractions into a single, cohesive domain model without losing platform-specific fidelity.

## Decision
We define a canonical set of domain entities in `tasklens-core` and `TaskLensCore`:
1. `ScheduledWork`: Represents a declared unit of background work (identifier, name, tags, declared constraints, scheduler type).
2. `ExecutionAttempt`: Represents a specific runtime execution run of a task, capturing start/stop timestamps, duration, exit status, and stop reason.
3. `TaskLensEvent`: Represents an immutable, sequenced point-in-time occurrence (lifecycle change or environment transition).

## Consequences
### Positive
- The diagnosis engine evaluates identical rules across Android and iOS events.
- Storage schema and serialization models are completely consistent across platforms.
- Developer UI can visualize timelines using the exact same components and mental model.

### Negative
- Platform adapters must translate native concepts (e.g. `WorkInfo.State` or `BGTask` callbacks) into canonical enum values.
