# ADR-004: Offline Portable Export Format (`.tasklens`)

## Status
Accepted

## Context
When field testers, QA engineers, or users encounter background sync issues, extracting diagnostic state from a mobile device without an attached USB debugger or specialized ADB scripts is difficult.

## Decision
TaskLens standardizes on a portable, self-contained ZIP archive format (`.tasklens`) containing:
1. `manifest.json`: Device specifications, OS version, app version, SDK version.
2. `tasks.json`, `attempts.json`, `events.jsonl`, `diagnoses.json`, `environment.json`.
3. `README.html`: A self-contained, responsive, CSS-styled HTML report.

The `.tasklens` archive is generated on-device via `DefaultTaskLensExporter` and can be shared via the standard Android Sharesheet (Slack, email, bug trackers).

## Consequences
### Positive
- Any recipient can open `README.html` in their web browser without installing any developer tools or CLI.
- Raw JSON files enable automated machine parsing, ingestion into CI bug trackers, or diffing.

### Negative
- ZIP compression takes slight compute time during export (typically 50-150ms).
