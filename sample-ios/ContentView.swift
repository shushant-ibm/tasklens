import SwiftUI
import TaskLens
import TaskLensCore
import TaskLensBGTasks
import TaskLensUI

struct ContentView: View {
    @State private var showInspector = false
    @State private var statusMessage = "Ready to simulate background tasks"

    var body: some View {
        NavigationView {
            ScrollView {
                VStack(spacing: 16) {
                    // Header banner
                    VStack(alignment: .leading, spacing: 6) {
                        Text("Why didn't my background task run?")
                            .font(.headline)
                            .foregroundColor(.white)
                        Text("TaskLens for iOS continuously tracks BGTaskScheduler events, URLSession background tasks, and quota expirations.")
                            .font(.subheadline)
                            .foregroundColor(.white.opacity(0.85))
                    }
                    .padding()
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color.indigo)
                    .cornerRadius(12)

                    // Actions
                    Group {
                        ScenarioRow(
                            title: "Simulate BGAppRefresh",
                            subtitle: "Triggers app refresh task with network check.",
                            systemImage: "arrow.clockwise.circle.fill",
                            color: .green
                        ) {
                            Task {
                                let event = TaskLensEvent(
                                    taskId: "dev.shushant.tasklens.sample.refresh",
                                    type: .taskStarted,
                                    source: .bgTaskScheduler,
                                    attributes: ["type": "BGAppRefreshTask"]
                                )
                                await TaskLens.emit(event: event)
                                statusMessage = "Simulated App Refresh Started"
                            }
                        }

                        ScenarioRow(
                            title: "Simulate BGProcessingTask",
                            subtitle: "Simulates database maintenance background task.",
                            systemImage: "internaldrive.fill",
                            color: .blue
                        ) {
                            Task {
                                let event = TaskLensEvent(
                                    taskId: "dev.shushant.tasklens.sample.database-cleanup",
                                    type: .taskStarted,
                                    source: .bgTaskScheduler,
                                    attributes: ["type": "BGProcessingTask"]
                                )
                                await TaskLens.emit(event: event)
                                statusMessage = "Simulated Database Cleanup Started"
                            }
                        }

                        ScenarioRow(
                            title: "Simulate Task Expiration",
                            subtitle: "Fires expiration handler before task finishes (~30s limit).",
                            systemImage: "hourglass.bottomhalf.filled",
                            color: .orange
                        ) {
                            Task {
                                let event = TaskLensEvent(
                                    taskId: "dev.shushant.tasklens.sample.refresh",
                                    type: .taskExpired,
                                    source: .bgTaskScheduler,
                                    attributes: ["reason": "System quota timeout"]
                                )
                                await TaskLens.emit(event: event)
                                statusMessage = "Simulated Expiration Handler Fired"
                            }
                        }

                        ScenarioRow(
                            title: "Validate Capabilities",
                            subtitle: "Verifies Info.plist permitted identifiers match registered tasks.",
                            systemImage: "checkmark.seal.fill",
                            color: .purple
                        ) {
                            let errors = TaskLensBG.validateCapabilities(
                                registeredIdentifiers: ["dev.shushant.tasklens.sample.unregistered-job"],
                                infoPlistIdentifiers: ["dev.shushant.tasklens.sample.refresh"]
                            )
                            if let err = errors.first {
                                statusMessage = "Config warning: \(err)"
                            } else {
                                statusMessage = "All identifiers valid in Info.plist"
                            }
                        }
                    }

                    Text(statusMessage)
                        .font(.caption)
                        .foregroundColor(.secondary)
                        .padding(.top, 8)
                }
                .padding()
            }
            .navigationTitle("TaskLens Lab")
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(action: { showInspector = true }) {
                        Label("Inspector", systemImage: "magnifyingglass")
                    }
                }
            }
            .sheet(isPresented: $showInspector) {
                NavigationView {
                    TaskFeedView()
                }
            }
        }
    }
}

struct ScenarioRow: View {
    let title: String
    let subtitle: String
    let systemImage: String
    let color: Color
    let action: () -> Void

    var body: some View {
        HStack(spacing: 14) {
            Image(systemName: systemImage)
                .font(.system(size: 28))
                .foregroundColor(color)
                .frame(width: 36)

            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.subheadline)
                    .fontWeight(.bold)
                Text(subtitle)
                    .font(.caption)
                    .foregroundColor(.secondary)
            }

            Spacer()

            Button("Trigger", action: action)
                .buttonStyle(.borderedProminent)
                .controlSize(.small)
        }
        .padding()
        .background(Color(UIColor.secondarySystemBackground))
        .cornerRadius(10)
    }
}
