# ADR-002: Deterministic Priority-Based Diagnosis Rule Engine

## Status
Accepted

## Context
When developers need to understand why a task didn't run, black-box AI or LLM models on-device are problematic: they introduce nondeterministic outputs, consume hundreds of megabytes of RAM, introduce battery drain, and may hallucinate explanations without evidence.

## Decision
TaskLens uses a deterministic, rule-based inference engine (`DefaultDiagnosisEngine`). Each rule implements `DiagnosisRule`:
- Has a unique identifier and integer priority score.
- Evaluates against an immutable `DiagnosisContext`.
- If matched, produces a `DiagnosisCandidate` with:
  - Exact classification (`DiagnosisClassification`)
  - Objective confidence (`DiagnosisConfidence`: `CONFIRMED`, `LIKELY`, `POSSIBLE`)
  - A chain of `Evidence` objects referencing concrete event IDs
  - Actionable remediation advice and acknowledged platform limitations.

Rules evaluate in strict priority descending order.

## Consequences
### Positive
- Sub-millisecond evaluation latency (<2ms).
- 100% reproducible across test suites and production devices.
- Auditable evidence trails link every conclusion directly to underlying telemetry.

### Negative
- Emerging OS edge cases require authoring explicit rules rather than relying on automated semantic pattern discovery.
