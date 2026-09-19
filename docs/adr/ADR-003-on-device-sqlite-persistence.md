# ADR-003: On-Device SQLite Persistence and Automated Retention Policies

## Status
Accepted

## Context
Mobile devices have constrained storage, and continuous logging of OS state changes (battery, network, doze) risks unbounded disk consumption or app bloat.

## Decision
TaskLens uses an on-device embedded SQLite database (`AndroidSqliteTaskLensStore`) with:
1. Normalized relational tables: `tasks`, `attempts`, `events`, `diagnoses`.
2. Automatic rolling retention policies (`RetentionPolicy.Combined(days = 7, maxTasks = 500)`).
3. Retention enforcement executed asynchronously during SDK startup or background maintenance windows.
4. Fast indexed queries by `task_id`, `timestamp`, and `type`.

## Consequences
### Positive
- Zero external database dependencies required in the host application.
- Guarantees strict bounds on app data directory storage (<25 MB).
- High-performance atomic transactions and relational querying for the UI inspector.

### Negative
- Direct SQLite driver requires platform-specific implementations on Android and iOS.
