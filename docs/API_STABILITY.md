# TaskLens API Stability & Evolution Policy

TaskLens is designed to be an enterprise foundation embedded into production mobile applications. This document outlines our API stability tiers, binary compatibility guarantees, and schema versioning strategies.

---

## 1. Stability Tiers

Every public package and symbol in TaskLens falls under one of three stability tiers:

### 🟢 Stable (`@StableAPI`)
- **Modules**: `tasklens-core`, `tasklens-storage`, `tasklens-diagnosis`, `TaskLensCore`, `TaskLensDiagnosis`
- **Guarantees**: Semantic Versioning (SemVer 2.0). No breaking binary or source changes within the same major version.
- **Deprecation**: Deprecated APIs will remain for at least one minor release cycle with `@Deprecated(replaceWith = ...)` before removal.

### 🟡 Platform Interop (`@ExperimentalTaskLensApi`)
- **Modules**: `tasklens-workmanager`, `tasklens-jobscheduler`, `tasklens-bgtasks`
- **Guarantees**: Dependent on underlying Android Jetpack and Apple SDK stability. Backwards-compatible across all supported API levels (Android API 26-35, iOS 14-18).

### 🔵 Internal (`@InternalTaskLensApi`)
- **Modules**: Storage implementation internals (`AndroidSqliteTaskLensStore`), internal channel pipelines, and low-level reflection helpers.
- **Guarantees**: Not intended for direct public consumption. May change without notice between patch releases.

---

## 2. Event & Storage Schema Evolution

1. **Schema Versioning**: Every event emitted into TaskLens carries `schemaVersion: Int = 1`.
2. **Backwards Compatibility**: New fields added to `TaskLensEvent` or `ScheduledWork` must supply default values to allow decoding older event streams.
3. **SQLite Migrations**: The Android SQLite database automatically executes progressive migration scripts across schema version increments without dropping existing task history.
4. **Export Archive Stability**: The `.tasklens` archive format is versioned in `manifest.json`. Any newer viewer or web inspector is guaranteed to read earlier archive versions.
