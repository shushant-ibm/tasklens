# ADR-008: Bounded In-Memory Channel for Non-Blocking Telemetry Ingestion

## Status
Accepted

## Context
Background tasks may execute during high system load or low battery conditions. Telemetry collection must never block application threads, cause jank, or trigger Out-Of-Memory (OOM) errors during rapid event bursts (e.g. rapid network state flickers or large batch migrations).

## Decision
TaskLens uses a Kotlin `Channel<TaskLensEvent>` with:
1. `capacity = 1000` (bounded memory).
2. `onBufferOverflow = BufferOverflow.DROP_OLDEST`.
3. Non-blocking ingestion via `eventChannel.trySend(event)`.
4. Dedicated single-consumer processing coroutine running on `Dispatchers.Default`.

## Consequences
### Positive
- Zero blocking on main or worker threads.
- Under extreme event storms, older diagnostic events are safely dropped without crashing the host process.
- Strict bounded memory footprint (<2MB memory for event queue).

### Negative
- If the consumer loop is starved for extended periods, some historical events may be dropped.
