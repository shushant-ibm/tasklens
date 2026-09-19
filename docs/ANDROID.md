# TaskLens Android Integration Guide

TaskLens follows the same developer-first minimal integration model as [Kourier](https://github.com/dev-shushant/kourier):
- **Single Dependency**: The host application adds **only one dependency** (`tasklens-android` for debug, `tasklens-noop` for release).
- **Encapsulated Internals**: Internal storage, diagnosis rules, export bundlers, and UI activities are completely hidden and never exposed on the host app's compile classpath.
- **Zero Configuration WorkManager Tracing**: Automatically detects and observes WorkManager jobs with zero manual setup.
- **Programmatic Inspector Launch**: Open the debugger with a single call (`TaskLens.showUI()`) without declaring activities in your manifest.

---

## 1. Setup Gradle Dependencies

In your application's `build.gradle.kts`:

```kotlin
dependencies {
    // Debug & Staging builds: Complete diagnostics engine, WorkManager tracing, and UI
    debugImplementation("dev.shushant.tasklens:tasklens-android:0.1.0")

    // Release builds: Completely empty stubs, zero overhead, zero transitive dependencies
    releaseImplementation("dev.shushant.tasklens:tasklens-noop:0.1.0")
}
```

---

## 2. Minimal Initialization in `Application`

Initialize `TaskLens` in your `Application.onCreate()` using the clean builder DSL:

```kotlin
class MainApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Single-line idiomatic initialization
        TaskLens.install(this) {
            retention(days = 7, maxTasks = 500)
            captureEnvironment = true // Monitors Network, Battery, Doze, and Thermal state
            autoObserveWorkManager = true // Automatically tracks all WorkManager jobs
        }
    }
}
```

---

## 3. Worker Tracing with `TaskLens.trace`

Wrap worker executions using the `trace` extension on `TaskLens`. TaskLens automatically logs worker start, captures execution duration, records run attempt counts, and intercepts failures:

```kotlin
import dev.shushant.tasklens.android.TaskLens
import dev.shushant.tasklens.android.trace

class SyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return TaskLens.trace(this) {
            fetchRemoteChanges()
            saveToDatabase()
            Result.success()
        }
    }
}
```

In release builds with `tasklens-noop`, `TaskLens.trace` is an `inline` function that directly executes the code block with **zero allocations and zero runtime overhead**.

---

## 4. Launching the Debugger UI

Open the on-device inspector from anywhere (debug drawer, shake gesture, or QA menu):

```kotlin
// Opens the inspector activity automatically
TaskLens.showUI(context)

// Or programmatically close it
TaskLens.hideUI()
```

You do **not** need to declare `TaskLensActivity` in your `AndroidManifest.xml` — it is merged automatically from the SDK.

---

## 5. Breadcrumbs and Custom Context

Add custom diagnostic breadcrumbs to annotate background task execution:

```kotlin
TaskLens.breadcrumb(
    title = "Sync Step",
    message = "Cache invalidated, fetching batch 1 of 5",
    taskId = worker.id.toString(),
    attributes = mapOf("batch_size" to "50")
)
```

---

## 6. Understanding Android Stop Reasons

Android 12 (API 31) and Android 14 introduced granular stop reasons in `WorkInfo.getStopReason()`. TaskLens automatically translates every stop reason code into human-readable explanations:

| Stop Reason Constant | Name | Explanation |
|:---------------------|:-----|:------------|
| `STOP_REASON_CONSTRAINT_CHARGING` | Charging Lost | Worker required charging, but the user unplugged the device |
| `STOP_REASON_CONSTRAINT_CONNECTIVITY` | Network Lost | Required network connection dropped during execution |
| `STOP_REASON_DEVICE_STATE` | Battery Saver / Thermal | OS triggered Battery Saver or high thermal state |
| `STOP_REASON_TIMEOUT` | Execution Timeout | Worker exceeded the 10-minute WorkManager background execution limit |
| `STOP_REASON_CANCELLED_BY_APP` | Cancelled by App | Application called `cancelWorkById` or `cancelAllWork` |
| `STOP_REASON_SYSTEM_PREEMPT` | System Preemption | Higher priority task or system service preempted worker |
