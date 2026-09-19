import Foundation
import TaskLensCore
import TaskLensStorage
import TaskLensDiagnosis
import TaskLensBGTasks
import TaskLensURLSession
import TaskLensUI

public struct TaskLensConfig {
    public var retentionPolicy: RetentionPolicy
    public var captureEnvironment: Bool
    public var enableKourierBridge: Bool
    public var diagnosticsEnabled: Bool
    public var redactor: TaskLensRedactor

    public static let `default` = TaskLensConfig(
        retentionPolicy: .lastDays(7),
        captureEnvironment: true,
        enableKourierBridge: true,
        diagnosticsEnabled: true,
        redactor: DefaultTaskLensRedactor.shared
    )

    public init(
        retentionPolicy: RetentionPolicy = .lastDays(7),
        captureEnvironment: Bool = true,
        enableKourierBridge: Bool = true,
        diagnosticsEnabled: Bool = true,
        redactor: TaskLensRedactor = DefaultTaskLensRedactor.shared
    ) {
        self.retentionPolicy = retentionPolicy
        self.captureEnvironment = captureEnvironment
        self.enableKourierBridge = enableKourierBridge
        self.diagnosticsEnabled = diagnosticsEnabled
        self.redactor = redactor
    }
}

public enum TaskLens {
    private static var store: any TaskLensStore = SQLiteTaskLensStore()
    private static var config = TaskLensConfig.default
    private static var _isInstalled = false

    public static func install(config: TaskLensConfig = .default, customStore: (any TaskLensStore)? = nil) {
        guard !_isInstalled else { return }
        self.config = config
        if let custom = customStore {
            self.store = custom
        } else {
            self.store = SQLiteTaskLensStore()
        }

        TaskLensBGBridge.eventSink = { event in await emit(event: event) }
        TaskLensBGBridge.taskSink = { task in await saveTask(task) }
        TaskLensBGBridge.attemptSink = { attempt in await saveAttempt(attempt) }
        TaskLensBGBridge.diagnosisSink = { diagnosis in await saveDiagnosis(diagnosis) }

        self._isInstalled = true
    }

    public static func isInstalled() -> Bool {
        return _isInstalled
    }

    public static func getConfig() -> TaskLensConfig {
        return config
    }

    public static func show() {
        // Platform trigger for debug UI presentation
    }

    public static func showUI() {
        show()
    }

    public static func hide() {
        // Platform dismiss
    }

    public static func hideUI() {
        hide()
    }

    public static func emit(event: TaskLensEvent) async {
        await store.append(event: event)
    }

    public static func saveTask(_ task: ScheduledWork) async {
        await store.saveTask(task)
    }

    public static func saveAttempt(_ attempt: ExecutionAttempt) async {
        await store.saveAttempt(attempt)
    }

    public static func saveDiagnosis(_ diagnosis: Diagnosis) async {
        await store.saveDiagnosis(diagnosis)
    }

    public static func clear() async {
        await store.clear()
    }

    public static func breadcrumb(
        title: String,
        message: String,
        taskId: String? = nil,
        attributes: [String: String] = [:]
    ) async {
        var attrs = attributes
        attrs["title"] = title
        attrs["message"] = message
        let event = TaskLensEvent(
            taskId: taskId,
            type: .customBreadcrumb,
            source: .application,
            attributes: attrs
        )
        await store.append(event: event)
    }

    public static func withCorrelation<T>(
        _ correlationId: String,
        operation: () async throws -> T
    ) async rethrows -> T {
        return try await operation()
    }

    public static func export(taskId: String) async throws -> URL {
        return try await TaskLensArchiveExporter.createArchive(
            taskId: taskId,
            store: store,
            redactor: config.redactor
        )
    }

    public static func getStore() -> any TaskLensStore {
        store
    }
}
