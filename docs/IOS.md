# TaskLens iOS Integration Guide

This guide details integrating TaskLens into iOS applications using Swift Package Manager, monitoring `BGTaskScheduler` tasks, instrumenting `URLSessionBackground`, and displaying the SwiftUI debugger.

---

## 1. Add Swift Package Dependency

Add the TaskLens Swift package in Xcode or your `Package.swift`:

```swift
dependencies: [
    .package(url: "https://github.com/shushanttiwari/tasklens.git", from: "0.1.0")
]
```

Link the appropriate modules to your target:
- `TaskLens`: Main facade and unified event pipeline.
- `TaskLensBGTasks`: Integration helpers for `BGTaskScheduler`.
- `TaskLensURLSession`: Telemetry tracker for background URLSession downloads and uploads.
- `TaskLensUI`: SwiftUI debug inspector view.
- `TaskLensNoop`: (Optional) Zero-overhead stubs for production targets.

---

## 2. Info.plist Configuration & Verification

iOS strictly enforces background task registration. Any task identifier submitted via `BGTaskScheduler.shared.submit` must exist in `BGTaskSchedulerPermittedIdentifiers` in `Info.plist`.

```xml
<key>BGTaskSchedulerPermittedIdentifiers</key>
<array>
    <string>dev.shushant.tasklens.refresh</string>
    <string>dev.shushant.tasklens.processing</string>
</array>
<key>UIBackgroundModes</key>
<array>
    <string>fetch</string>
    <string>processing</string>
</array>
```

TaskLens includes a capability validator:

```swift
let errors = TaskLensBG.validateCapabilities(
    registeredIdentifiers: ["dev.shushant.tasklens.refresh"],
    infoPlistIdentifiers: Bundle.main.object(forInfoDictionaryKey: "BGTaskSchedulerPermittedIdentifiers") as? [String] ?? []
)
// Flags missing identifiers before submission!
```

---

## 3. Registering & Submitting BGTasks

Register handlers during application startup:

```swift
import TaskLens
import TaskLensBGTasks

@main
struct MyApp: App {
    init() {
        TaskLens.install()

        TaskLensBG.register(identifier: "dev.shushant.tasklens.refresh") { task in
            handleAppRefresh(task: task)
        }
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}

func handleAppRefresh(task: Any) {
    // Schedule next refresh
    try? TaskLensBG.submit(
        identifier: "dev.shushant.tasklens.refresh",
        earliestBeginDate: Date(timeIntervalSinceNow: 15 * 60)
    )

    // Execute refresh work
    Task {
        let success = await performDataSync()
        TaskLensBG.complete(task: task, success: success)
    }
}
```

---

## 4. Background URLSession Instrumentation

Track background network transfers using `TaskLensURLSession`:

```swift
import TaskLensURLSession

let tracker = TaskLensURLSessionTracker.shared
let sessionTask = session.downloadTask(with: url)
tracker.track(task: sessionTask, taskId: "download-sync-job")
sessionTask.resume()
```

When transfers complete or fail, TaskLens correlates network metrics with background task lifecycle events.

---

## 5. SwiftUI Debug Inspector

Embed the developer UI directly in your debug sheet or developer menu:

```swift
import SwiftUI
import TaskLensUI

struct DebugMenu: View {
    @State private var showInspector = false

    var body: some View {
        Button("Open TaskLens Inspector") {
            showInspector = true
        }
        .sheet(isPresented: $showInspector) {
            TaskLensView()
        }
    }
}
```
