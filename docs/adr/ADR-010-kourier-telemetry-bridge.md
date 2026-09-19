# ADR-010: Kourier Telemetry Bridge Architecture

## Status
Accepted

## Context
In large enterprise apps, on-device diagnostics are critical for local debugging, but centralized fleet telemetry is required to detect widespread background failures (e.g. backend deployment breaking a background sync endpoint, or new OS update triggering battery kills).

## Decision
TaskLens isolates external telemetry into a dedicated optional bridge module: `:tasklens-kourier`.
1. Core and storage modules maintain zero network dependencies.
2. The bridge observes task completion and diagnosis candidate generation.
3. Diagnostic payloads are sanitized to strip any potential PII or raw payloads, transmitting only high-level classifications, rule IDs, stop reasons, and environment metrics.
4. Transport is decoupled behind `KourierDispatcher`, enabling teams to bind to HTTP endpoints, Kafka producers, or internal telemetry pipelines.

## Consequences
### Positive
- Strict separation of concerns: core remains pure and offline-first.
- Zero network bloat for applications that only want local on-device debugging.
- Anonymized, aggregated fleet diagnostics for enterprise observability.

### Negative
- Teams wanting fleet-wide telemetry must configure and supply the dispatcher implementation.
