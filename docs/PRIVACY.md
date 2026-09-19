# TaskLens Privacy & Security Specification

Because background tasks frequently handle sensitive user information (authentication tokens, sync payloads, financial transactions, chat messages, location coords), TaskLens implements a strict **Privacy-First Architecture**.

---

## 1. Zero Payload Ingestion by Default

- By default, TaskLens **never captures raw request or response payloads**.
- Configuration flag `captureWorkerPayloads` defaults to `false`.
- Only metadata (task names, run attempts, timestamps, stop codes, system state) is collected.

---

## 2. Synchronous Attribute Redactor

Before any event is appended to the persistence store or memory buffer, every attribute key and value passes through the `TaskLensRedactor`.

### Default Redacted Keys
Any attribute whose key matches (case-insensitive substring):
- `token`, `auth`, `authorization`, `bearer`
- `password`, `secret`, `api_key`, `apikey`
- `credential`, `session`, `private`
- `email`, `ssn`, `cookie`

Is automatically replaced with `[REDACTED]`.

### Sensitive Value Pattern Redaction
The redactor also scans attribute string values for common secret formats:
- Bearer tokens (`Bearer ey...`)
- JSON Web Tokens (`eyJ...`)
- Base64 encoded private keys
- Basic authentication credentials

### Custom Redactor Injection
Organizations can provide custom redactors conforming to the `TaskLensRedactor` interface:

```kotlin
val customRedactor = object : TaskLensRedactor {
    override fun redact(key: String, value: String): String {
        return if (key.startsWith("user_pii_")) "[PII_REDACTED]" else value
    }
}

TaskLens.install(
    application = this,
    config = TaskLensConfig(redactor = customRedactor)
)
```

---

## 3. Storage Security

- **Android**: Events are stored in app-private SQLite databases (`Context.getDatabasePath(...)`) with mode `MODE_PRIVATE`, inaccessible to other applications on non-rooted devices.
- **iOS**: Data resides in the application's private sandboxed `Application Support` directory.
- **No Remote Telemetry**: Core TaskLens modules make zero outbound network requests. Diagnostic data remains on-device until the user or developer explicitly triggers an export or an optional enterprise bridge is configured.
