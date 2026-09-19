import Foundation

public enum EventType: String, Codable {
    case taskRegistered = "TASK_REGISTERED"
    case taskSubmitted = "TASK_SUBMITTED"
    case taskEnqueued = "TASK_ENQUEUED"
    case taskWaiting = "TASK_WAITING"
    case taskEligible = "TASK_ELIGIBLE"
    case taskLaunched = "TASK_LAUNCHED"
    case taskStarted = "TASK_STARTED"
    case taskStopped = "TASK_STOPPED"
    case taskExpired = "TASK_EXPIRED"
    case taskRetryRequested = "TASK_RETRY_REQUESTED"
    case taskRescheduled = "TASK_RESCHEDULED"
    case taskSucceeded = "TASK_SUCCEEDED"
    case taskFailed = "TASK_FAILED"
    case taskCancelled = "TASK_CANCELLED"
    case taskCompletionReported = "TASK_COMPLETION_REPORTED"

    case constraintChanged = "CONSTRAINT_CHANGED"
    case networkChanged = "NETWORK_CHANGED"
    case batteryChanged = "BATTERY_CHANGED"
    case powerModeChanged = "POWER_MODE_CHANGED"
    case appStateChanged = "APP_STATE_CHANGED"
    case processStateChanged = "PROCESS_STATE_CHANGED"
    case backgroundRefreshChanged = "BACKGROUND_REFRESH_CHANGED"

    case urlSessionTransferStarted = "URLSESSION_TRANSFER_STARTED"
    case urlSessionTransferCompleted = "URLSESSION_TRANSFER_COMPLETED"
    case urlSessionTransferFailed = "URLSESSION_TRANSFER_FAILED"

    case httpRequestStarted = "HTTP_REQUEST_STARTED"
    case httpRequestCompleted = "HTTP_REQUEST_COMPLETED"
    case httpRequestFailed = "HTTP_REQUEST_FAILED"

    case customBreadcrumb = "CUSTOM_BREADCRUMB"
}

public enum EventSource: String, Codable {
    case workManager = "WORK_MANAGER"
    case bgTaskScheduler = "BG_TASK_SCHEDULER"
    case jobScheduler = "JOB_SCHEDULER"
    case foregroundService = "FOREGROUND_SERVICE"
    case alarmManager = "ALARM_MANAGER"
    case urlSession = "URL_SESSION"
    case systemBroadcast = "SYSTEM_BROADCAST"
    case application = "APPLICATION"
    case kourier = "KOURIER"
    case custom = "CUSTOM"
}

public enum EventSeverity: String, Codable {
    case info = "INFO"
    case warning = "WARNING"
    case error = "ERROR"
    case critical = "CRITICAL"
}

public struct TaskLensEvent: Codable, Identifiable {
    public let id: String
    public let taskId: String?
    public let attemptId: String?
    public let timestamp: Date
    public let sequenceNumber: Int64
    public let type: EventType
    public let source: EventSource
    public let severity: EventSeverity
    public let attributes: [String: String]
    public let schemaVersion: Int

    private static var sequenceCounter: Int64 = 0
    private static let lock = NSLock()

    public static func nextSequence() -> Int64 {
        lock.lock()
        defer { lock.unlock() }
        sequenceCounter += 1
        return sequenceCounter
    }

    public static func resetSequence(to value: Int64 = 0) {
        lock.lock()
        defer { lock.unlock() }
        sequenceCounter = value
    }

    public init(
        id: String = UUID().uuidString,
        taskId: String? = nil,
        attemptId: String? = nil,
        timestamp: Date = Date(),
        sequenceNumber: Int64? = nil,
        type: EventType,
        source: EventSource,
        severity: EventSeverity = .info,
        attributes: [String: String] = [:],
        schemaVersion: Int = 1
    ) {
        self.id = id
        self.taskId = taskId
        self.attemptId = attemptId
        self.timestamp = timestamp
        if let seq = sequenceNumber {
            self.sequenceNumber = seq
        } else {
            self.sequenceNumber = Self.nextSequence()
        }
        self.type = type
        self.source = source
        self.severity = severity
        self.attributes = attributes
        self.schemaVersion = schemaVersion
    }
}
