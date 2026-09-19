# ADR-005: Privacy-First Ingestion and Sensitive Attribute Redaction Boundary

## Status
Accepted

## Context
Mobile background tasks frequently transmit bearer tokens, credentials, API keys, cookies, and user IDs. If diagnostic telemetry blindly stores or exports these values, it creates severe compliance and data-leak vulnerabilities (GDPR, HIPAA, CCPA).

## Decision
TaskLens enforces a strict privacy boundary:
1. **Zero Payload Ingestion by Default**: Raw HTTP bodies and task payloads are ignored unless explicitly opted into via configuration.
2. **Synchronous Redaction**: The `TaskLensRedactor` runs synchronously in the event processing pipeline *before* any event is written to SQLite or memory.
3. **Key and Value Matching**: Both dictionary keys (e.g. `authorization`, `password`, `token`) and string values (e.g. JWT strings, Bearer tokens) are identified and redacted with `[REDACTED]`.

## Consequences
### Positive
- Diagnostic files and `.tasklens` exports can be safely shared across engineering teams without leaking sensitive authentication tokens or user credentials.
- Compliance posture is secure by default.

### Negative
- Developers must explicitly whitelist non-sensitive metadata keys if they contain substrings like "token" (e.g. `cancellation_token`).
