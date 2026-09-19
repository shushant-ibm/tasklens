# TaskLens Diagnosis Engine Specification

The **Diagnosis Engine** is the intelligence layer of TaskLens. Its sole responsibility is answering:
> *"Why did this background execution fail, stop prematurely, get deferred, or behave abnormally?"*

---

## 1. Design Philosophy: Determinism Over Black-Box Models

While Large Language Models (LLMs) excel at offline code generation and synthesis, running generative AI on-device for live root-cause diagnosis introduces unacceptable variance, latency, memory consumption, and hallucination risks.

TaskLens enforces **Deterministic Rule Evaluation**:
1. **Reproducibility**: Identical event sequences and platform conditions always produce identical diagnosis outputs across devices.
2. **Evidence Precedence**: Every claim must point to explicit timestamped evidence (`sourceEventIds`).
3. **Sub-millisecond Execution**: The entire rule chain evaluates in under 2 milliseconds on typical mobile chipsets.
4. **Hierarchical Prioritization**: Higher-specificity rules (e.g. OS hardware stop reasons) override lower-specificity fallback rules (e.g. generic application failure).

---

## 2. Evaluation Pipeline

```
              ┌─────────────────────────────────────────┐
              │            DiagnosisContext             │
              │  • ScheduledWork                        │
              │  • List<ExecutionAttempt>               │
              │  • List<TaskLensEvent> (Task + Env)     │
              └─────────────────────────────────────────┘
                                   │
                                   ▼
              ┌─────────────────────────────────────────┐
              │           DiagnosisRule Chain           │
              │  Sorted by Priority (Descending)        │
              └─────────────────────────────────────────┘
                                   │
              ┌────────────────────┴────────────────────┐
              ▼                                         ▼
   [Rule Evaluates Match]                    [Rule Evaluates No Match]
              │                                         │
              ▼                                         ▼
   Produce DiagnosisCandidate                        Continue
   (Classification, Confidence, Evidence)
                                   │
                                   ▼
              ┌─────────────────────────────────────────┐
              │           Confidence Threshold          │
              │   Confirmed  >  Likely  >  Possible     │
              └─────────────────────────────────────────┘
                                   │
                                   ▼
              ┌─────────────────────────────────────────┐
              │             Final Diagnosis             │
              │  Primary Diagnosis + Supporting Evidence│
              └─────────────────────────────────────────┘
```

---

## 3. Diagnosis Classifications

TaskLens standardizes background execution root causes into clear, actionable classifications:

| Classification | Meaning | Actionable Guidance |
|:---------------|:--------|:--------------------|
| `PLATFORM_KILLED` | OS forcibly terminated the task process or worker | Check stop reason; defer heavy work to charging or unmetered network |
| `CONSTRAINT_NOT_MET` | Scheduler refused to run because a declared constraint was unsatisfied | Verify network, battery, storage, or charging state |
| `NETWORK_LOST_DURING_EXEC` | Task started on network, but connection dropped mid-flight | Implement idempotent chunking and retry with backoff |
| `RETRY_EXHAUSTED` | Task retried up to maximum attempts and ceased | Check upstream API health and increase backoff intervals |
| `TASK_EXPIRED` | Task ran past OS time budget (~10 min on Android, ~30s on iOS) | Break long tasks into sub-tasks or use foreground service |
| `APPLICATION_FAILURE` | Worker returned failure (`Result.failure()`) | Check application error logs and payload exceptions |
| `APPLICATION_CANCELLED`| Developer or user explicitly cancelled work | Verify cancellation triggers in application lifecycle |
| `CONFIGURATION_PROBLEM`| Manifest, Info.plist, or capability mismatch | Add missing identifier or permissions |
| `SCHEDULER_DELAY` | Task queued but OS hasn't dispatched it yet | Device is in Doze/Standby; wait for maintenance window |

---

## 4. Confidence Levels

Each candidate is tagged with an objective confidence rating:

- **`CONFIRMED`**: Backed by unambiguous platform evidence (e.g., `WorkInfo.getStopReason()` explicitly set to `STOP_REASON_DEVICE_STATE`, or iOS `expirationHandler` invoked).
- **`LIKELY`**: Strong temporal correlation (e.g., device transitioned to `DISCONNECTED` 300ms before a network exception was raised in the worker).
- **`POSSIBLE`**: Inferred from circumstantial environment states (e.g., task has been submitted for 4 hours while device was in deep Doze mode without an unconstrained battery exemption).

---

## 5. Authoring Custom Rules

Applications and internal SDKs can register proprietary domain rules:

```kotlin
class CustomAuthExpiredRule : DiagnosisRule {
    override val id = "RULE_AUTH_EXPIRED"
    override val name = "OAuth Token Expired"
    override val priority = 85

    override fun evaluate(context: DiagnosisContext): DiagnosisCandidate? {
        val authErrorEvent = context.events.find { 
            it.attributes["http_status"] == "401" || it.attributes["error"] == "token_expired" 
        } ?: return null

        return DiagnosisCandidate(
            ruleId = id,
            title = "Authentication Token Expired",
            summary = "The background sync was aborted because the user's refresh token has expired.",
            classification = DiagnosisClassification.APPLICATION_FAILURE,
            confidence = DiagnosisConfidence.CONFIRMED,
            evidence = listOf(
                Evidence(
                    type = EvidenceType.HTTP_STATUS,
                    source = EventSource.APPLICATION,
                    title = "HTTP 401 Unauthorized",
                    description = "OAuth token refresh failed with 401 Unauthorized",
                    sourceEventIds = listOf(authErrorEvent.id)
                )
            ),
            remediations = listOf(
                "Prompt the user to re-authenticate when the app next enters foreground.",
                "Ensure refresh tokens are persisted in EncryptedSharedPreferences."
            )
        )
    }
}
```
