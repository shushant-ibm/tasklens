import Foundation
import TaskLensCore

public final class TaskLensKourierBridge: @unchecked Sendable {
    private let eventSink: @Sendable (TaskLensEvent) -> Void
    private var isStarted = false
    private let lock = NSLock()

    public init(eventSink: @escaping @Sendable (TaskLensEvent) -> Void) {
        self.eventSink = eventSink
    }

    public func start() {
        lock.lock()
        isStarted = true
        lock.unlock()
    }

    public func stop() {
        lock.lock()
        isStarted = false
        lock.unlock()
    }

    public func onTransaction(
        id: String,
        url: String,
        method: String,
        status: Int,
        durationMs: Int64,
        taskId: String? = nil
    ) {
        lock.lock()
        guard isStarted else {
            lock.unlock()
            return
        }
        lock.unlock()

        let event = TaskLensEvent(
            taskId: taskId,
            type: .httpRequestCompleted,
            source: .kourier,
            attributes: [
                "request_id": id,
                "url": url,
                "method": method,
                "status_code": String(status),
                "duration_ms": String(durationMs)
            ]
        )
        eventSink(event)
    }
}
