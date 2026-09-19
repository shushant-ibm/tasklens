import SwiftUI
import TaskLensCore
import TaskLensDiagnosis

@available(iOS 15.0, macOS 12.0, *)
public struct TaskFeedView: View {
    public let tasks: [ScheduledWork]
    public let onSelectTask: (ScheduledWork) -> Void

    public init(tasks: [ScheduledWork], onSelectTask: @escaping (ScheduledWork) -> Void) {
        self.tasks = tasks
        self.onSelectTask = onSelectTask
    }

    public var body: some View {
        NavigationView {
            List(tasks) { task in
                Button(action: { onSelectTask(task) }) {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(task.name ?? task.id)
                            .font(.headline)
                        Text(task.scheduler.rawValue)
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                }
            }
            .navigationTitle("TaskLens Lab")
        }
    }
}
