import Foundation

public enum TaskLens {
    public static func install(config: Any? = nil) {}
    public static func isInstalled() -> Bool { return false }
    public static func show() {}
    public static func showUI() {}
    public static func hide() {}
    public static func hideUI() {}
    public static func emit(event: Any) async {}
    public static func clear() async {}
    public static func breadcrumb(
        title: String,
        message: String,
        taskId: String? = nil,
        attributes: [String: String] = [:]
    ) async {}
    public static func withCorrelation<T>(_ correlationId: String, operation: () async throws -> T) async rethrows -> T {
        try await operation()
    }
    public static func export(taskId: String) async throws -> URL {
        return FileManager.default.temporaryDirectory.appendingPathComponent("tasklens_noop.tasklens")
    }
}
