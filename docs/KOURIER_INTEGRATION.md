# TaskLens Kourier Telemetry Integration

For enterprise distributed architectures, background failures that occur in production on user devices need to be aggregated centrally for fleet-wide reliability tracking without violating user privacy.

TaskLens provides a dedicated, lightweight module: `:tasklens-kourier` (and `TaskLensKourierBridge` for iOS) to stream aggregated diagnostic records into Kourier.

---

## 1. Architecture

```
                                  [TaskLens Core]
                                         │
                         (Task Completion & Diagnosis)
                                         │
                                         ▼
                            [KourierTelemetryBridge]
                                         │
                ┌────────────────────────┴────────────────────────┐
                ▼                                                 ▼
        [Privacy Sanitizer]                             [Diagnostic Summary]
     (Strip all raw payloads)                        (Rule ID, Code, Confidence)
                │                                                 │
                └────────────────────────┬────────────────────────┘
                                         ▼
                            [Kourier Event Pipeline]
                                         │
                                         ▼
                        [Central Reliability Dashboard]
```

---

## 2. Android Configuration

Add `:tasklens-kourier` to your dependencies:

```kotlin
dependencies {
    implementation(project(":tasklens-kourier"))
}
```

Implement a `KourierDispatcher` or wire it to your telemetry client:

```kotlin
val dispatcher = object : KourierDispatcher {
    override fun dispatch(topic: String, payload: Map<String, Any?>) {
        // Forward to your internal telemetry SDK (e.g., KourierClient.send(...))
        MyKourierClient.recordDiagnostic(topic, payload)
    }
}

val bridge = KourierTelemetryBridge(dispatcher = dispatcher)
bridge.start(taskLens = TaskLens)
```

---

## 3. Dispatched Telemetry Schema

The bridge emits lightweight, anonymized diagnostic payloads:

```json
{
  "event_type": "tasklens_diagnostic_summary",
  "task_name": "DailySyncWorker",
  "scheduler": "WORK_MANAGER",
  "classification": "PLATFORM_KILLED",
  "confidence": "CONFIRMED",
  "primary_rule": "RULE_PLATFORM_STOP_REASON",
  "stop_reason": "STOP_REASON_DEVICE_STATE",
  "device_state": {
    "battery_saver": true,
    "battery_level": 14,
    "thermal_status": "THROTTLED"
  },
  "attempt_count": 2,
  "execution_duration_ms": 1420
}
```

Notice that **no user identifiers, payload data, headers, or query parameters** are transmitted. Only platform telemetry, scheduler constraints, and deterministic classifications are shared.
