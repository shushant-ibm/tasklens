import Foundation
import TaskLensCore

public final class TaskLensURLSessionProxy: NSObject, URLSessionDelegate, URLSessionTaskDelegate, URLSessionDownloadDelegate {
    private let actualDelegate: URLSessionDelegate?
    private let sessionIdentifier: String
    private let eventSink: (TaskLensEvent) -> Void

    public init(
        sessionIdentifier: String,
        actualDelegate: URLSessionDelegate?,
        eventSink: @escaping (TaskLensEvent) -> Void
    ) {
        self.sessionIdentifier = sessionIdentifier
        self.actualDelegate = actualDelegate
        self.eventSink = eventSink
    }

    public func urlSession(_ session: URLSession, task: URLSessionTask, didCompleteWithError error: Error?) {
        let taskId = "\(sessionIdentifier)-\(task.taskIdentifier)"
        if let err = error {
            eventSink(
                TaskLensEvent(
                    taskId: taskId,
                    type: .urlSessionTransferFailed,
                    source: .urlSession,
                    severity: .warning,
                    attributes: [
                        "session": sessionIdentifier,
                        "task_id": String(task.taskIdentifier),
                        "error": err.localizedDescription
                    ]
                )
            )
        } else {
            eventSink(
                TaskLensEvent(
                    taskId: taskId,
                    type: .urlSessionTransferCompleted,
                    source: .urlSession,
                    attributes: [
                        "session": sessionIdentifier,
                        "task_id": String(task.taskIdentifier)
                    ]
                )
            )
        }
        (actualDelegate as? URLSessionTaskDelegate)?.urlSession?(session, task: task, didCompleteWithError: error)
    }

    public func urlSession(_ session: URLSession, downloadTask: URLSessionDownloadTask, didFinishDownloadingTo location: URL) {
        (actualDelegate as? URLSessionDownloadDelegate)?.urlSession(session, downloadTask: downloadTask, didFinishDownloadingTo: location)
    }
}

public enum TaskLensURLSession {
    public static func makeBackgroundSession(
        identifier: String,
        delegate: URLSessionDelegate? = nil,
        eventSink: @escaping (TaskLensEvent) -> Void = { _ in }
    ) -> URLSession {
        let config = URLSessionConfiguration.background(withIdentifier: identifier)
        let proxy = TaskLensURLSessionProxy(sessionIdentifier: identifier, actualDelegate: delegate, eventSink: eventSink)
        return URLSession(configuration: config, delegate: proxy, delegateQueue: nil)
    }
}
