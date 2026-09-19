import SwiftUI
import TaskLens
import TaskLensBGTasks

@main
struct TaskLensLabApp: App {

    init() {
        // Initialize TaskLens on iOS
        TaskLens.install(config: TaskLensConfig())

        // Register background handlers
        TaskLensBG.register(identifier: "dev.shushant.tasklens.sample.refresh") { task in
            handleAppRefresh(task: task)
        }

        TaskLensBG.register(identifier: "dev.shushant.tasklens.sample.database-cleanup") { task in
            handleDatabaseCleanup(task: task)
        }
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}

func handleAppRefresh(task: Any) {
    Task {
        // Simulate network fetch
        try? await Task.sleep(nanoseconds: 1_500_000_000)
        TaskLensBG.complete(task: task, success: true)
    }
}

func handleDatabaseCleanup(task: Any) {
    Task {
        // Simulate database vacuum
        try? await Task.sleep(nanoseconds: 2_000_000_000)
        TaskLensBG.complete(task: task, success: true)
    }
}
