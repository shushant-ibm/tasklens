import Foundation

public enum TimelineIcon: String, Codable {
    case submitted = "●"
    case waiting = "◌"
    case started = "▶"
    case envChange = "◆"
    case retry = "↻"
    case warning = "⚠"
    case failed = "✕"
    case expired = "⌛"
    case success = "✓"
    case cancelled = "⊘"
    case stopped = "■"
    case breadcrumb = "💬"
}

public struct TimelineItem: Codable, Identifiable {
    public let id: String
    public let timestamp: Date
    public let icon: TimelineIcon
    public let title: String
    public let description: String
    public let durationMs: Int64?
    public let attributes: [String: String]
    public let isWarning: Bool
    public let isFailure: Bool

    public init(
        id: String,
        timestamp: Date,
        icon: TimelineIcon,
        title: String,
        description: String,
        durationMs: Int64? = nil,
        attributes: [String: String] = [:],
        isWarning: Bool = false,
        isFailure: Bool = false
    ) {
        self.id = id
        self.timestamp = timestamp
        self.icon = icon
        self.title = title
        self.description = description
        self.durationMs = durationMs
        self.attributes = attributes
        self.isWarning = isWarning
        self.isFailure = isFailure
    }
}

public struct TaskTimeline: Codable {
    public let taskId: String
    public let taskName: String?
    public let items: [TimelineItem]
    public let totalDurationMs: Int64?
    public let hasFailures: Bool
    public let isOngoing: Bool

    public init(
        taskId: String,
        taskName: String?,
        items: [TimelineItem],
        totalDurationMs: Int64?,
        hasFailures: Bool,
        isOngoing: Bool
    ) {
        self.taskId = taskId
        self.taskName = taskName
        self.items = items
        self.totalDurationMs = totalDurationMs
        self.hasFailures = hasFailures
        self.isOngoing = isOngoing
    }
}
