import Foundation

public enum TaskType: String, Codable {
    case worker = "WORKER"
    case coroutineWorker = "COROUTINE_WORKER"
    case periodicWork = "PERIODIC_WORK"
    case expeditedWork = "EXPEDITED_WORK"
    case bgAppRefresh = "BG_APP_REFRESH"
    case bgProcessing = "BG_PROCESSING"
    case backgroundURLSession = "BACKGROUND_URLSESSION"
    case jobService = "JOB_SERVICE"
    case foregroundService = "FOREGROUND_SERVICE"
    case alarm = "ALARM"
    case custom = "CUSTOM"
}

public enum SchedulerType: String, Codable {
    case workManager = "WORK_MANAGER"
    case bgTaskScheduler = "BG_TASK_SCHEDULER"
    case jobScheduler = "JOB_SCHEDULER"
    case foregroundService = "FOREGROUND_SERVICE"
    case alarmManager = "ALARM_MANAGER"
    case urlSession = "URL_SESSION"
    case manual = "MANUAL"
}

public enum AttemptOutcome: String, Codable {
    case success = "SUCCESS"
    case retry = "RETRY"
    case stopped = "STOPPED"
    case expired = "EXPIRED"
    case cancelled = "CANCELLED"
    case failed = "FAILED"
    case running = "RUNNING"
    case unknown = "UNKNOWN"
}

public enum NetworkRequirement: String, Codable {
    case notRequired = "NOT_REQUIRED"
    case connected = "CONNECTED"
    case unmetered = "UNMETERED"
    case notRoaming = "NOT_ROAMING"
    case temporarilyUnmetered = "TEMPORARILY_UNMETERED"
}

public enum AppState: String, Codable {
    case foreground = "FOREGROUND"
    case background = "BACKGROUND"
    case terminated = "TERMINATED"
    case unknown = "UNKNOWN"
}

public struct PlatformReason: Codable, Equatable {
    public let code: Int
    public let name: String
    public let description: String?

    public init(code: Int, name: String, description: String? = nil) {
        self.code = code
        self.name = name
        self.description = description
    }
}

public struct PlatformLimitation: Codable, Equatable {
    public let code: String
    public let message: String

    public init(code: String, message: String) {
        self.code = code
        self.message = message
    }
}

public struct ScheduledWork: Codable, Identifiable {
    public let id: String
    public let platformId: String?
    public let name: String?
    public let type: TaskType
    public let scheduler: SchedulerType
    public let submittedAt: Date?
    public let earliestBeginAt: Date?
    public let periodic: Bool
    public let metadata: [String: String]

    public init(
        id: String,
        platformId: String? = nil,
        name: String? = nil,
        type: TaskType = .custom,
        scheduler: SchedulerType = .manual,
        submittedAt: Date? = nil,
        earliestBeginAt: Date? = nil,
        periodic: Bool = false,
        metadata: [String: String] = [:]
    ) {
        self.id = id
        self.platformId = platformId
        self.name = name
        self.type = type
        self.scheduler = scheduler
        self.submittedAt = submittedAt
        self.earliestBeginAt = earliestBeginAt
        self.periodic = periodic
        self.metadata = metadata
    }
}

public struct ExecutionAttempt: Codable, Identifiable {
    public var id: String { attemptId }
    public let attemptId: String
    public let taskId: String
    public let attemptNumber: Int
    public let startedAt: Date?
    public let endedAt: Date?
    public let outcome: AttemptOutcome?
    public let platformReason: PlatformReason?
    public let evidenceIds: [String]

    public init(
        attemptId: String,
        taskId: String,
        attemptNumber: Int,
        startedAt: Date? = nil,
        endedAt: Date? = nil,
        outcome: AttemptOutcome? = nil,
        platformReason: PlatformReason? = nil,
        evidenceIds: [String] = []
    ) {
        self.attemptId = attemptId
        self.taskId = taskId
        self.attemptNumber = attemptNumber
        self.startedAt = startedAt
        self.endedAt = endedAt
        self.outcome = outcome
        self.platformReason = platformReason
        self.evidenceIds = evidenceIds
    }
}
