# Project Documentation Context (Non-Obvious Only)

- `tasklens-noop` is NOT a stub for tests — it is the production release artifact that ships in release builds to give zero overhead. It lives in `tasklens-noop/src/main/kotlin/dev/shushant/tasklens/android/TaskLens.kt` under the **same package** as the real SDK.
- `tasklens-android` exposes `tasklens-core` and `tasklens-workmanager` via `api()` but hides `tasklens-storage`, `tasklens-diagnosis`, `tasklens-export`, and `tasklens-ui` via `implementation()` — these internal modules are invisible to host apps at compile time.
- The iOS Swift SDK (under `Sources/`) is a structurally parallel but completely independent implementation — same model names, same API surface, zero shared code.
- `docs/adr/` contains the decision log for non-obvious design choices (channel overflow strategy, noop artifact design, multiplatform parity, etc.).
- `specs/SPEC.md` is the canonical cross-platform event taxonomy — both Android and iOS must conform to it.
- `tasklens-kourier` is an optional bridge to the Kourier HTTP interceptor SDK. It is enabled via `TaskLensConfig.enableKourierBridge`.
- The `.tasklens` export format is a ZIP archive with `manifest.json` + JSON data + offline HTML viewer. See `docs/EXPORT_FORMAT.md`.
- `EventSource.KOURIER` is a dedicated source enum value for network telemetry bridged from the Kourier SDK — not a generic custom source.
