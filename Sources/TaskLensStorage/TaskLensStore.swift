import Foundation
import TaskLensCore

public enum RetentionPolicy: Codable {
    case lastDays(Int)
    case maxTasks(Int)
    case forever
}

public struct TaskQuery {
    public let limit: Int
    public let offset: Int
    public let scheduler: SchedulerType?
    public let searchQuery: String?

    public init(
        limit: Int = 100,
        offset: Int = 0,
        scheduler: SchedulerType? = nil,
        searchQuery: String? = nil
    ) {
        self.limit = limit
        self.offset = offset
        self.scheduler = scheduler
        self.searchQuery = searchQuery
    }
}

public protocol TaskLensStore: Sendable {
    func append(event: TaskLensEvent) async
    func saveTask(_ task: ScheduledWork) async
    func saveAttempt(_ attempt: ExecutionAttempt) async
    func saveDiagnosis(_ diagnosis: Diagnosis) async
    func tasks(query: TaskQuery) async -> [ScheduledWork]
    func task(id: String) async -> ScheduledWork?
    func attempts(taskId: String) async -> [ExecutionAttempt]
    func events(taskId: String) async -> [TaskLensEvent]
    func diagnoses(taskId: String) async -> [Diagnosis]
    func environmentEvents(limit: Int) async -> [TaskLensEvent]
    func clear() async
    func applyRetention(_ policy: RetentionPolicy) async
}

public actor InMemoryTaskLensStore: TaskLensStore {
    private var tasksMap: [String: ScheduledWork] = [:]
    private var attemptsMap: [String: [ExecutionAttempt]] = [:]
    private var eventsList: [TaskLensEvent] = []
    private var diagnosesMap: [String: [Diagnosis]] = [:]

    public init() {}

    public func append(event: TaskLensEvent) {
        eventsList.append(event)
    }

    public func saveTask(_ task: ScheduledWork) {
        tasksMap[task.id] = task
    }

    public func saveAttempt(_ attempt: ExecutionAttempt) {
        var list = attemptsMap[attempt.taskId] ?? []
        if let idx = list.firstIndex(where: { $0.attemptId == attempt.attemptId }) {
            list[idx] = attempt
        } else {
            list.append(attempt)
        }
        attemptsMap[attempt.taskId] = list
    }

    public func saveDiagnosis(_ diagnosis: Diagnosis) {
        var list = diagnosesMap[diagnosis.taskId] ?? []
        list.append(diagnosis)
        diagnosesMap[diagnosis.taskId] = list
    }

    public func tasks(query: TaskQuery) -> [ScheduledWork] {
        var items = Array(tasksMap.values)
        if let s = query.scheduler {
            items = items.filter { $0.scheduler == s }
        }
        if let q = query.searchQuery?.lowercased(), !q.isEmpty {
            items = items.filter { $0.id.lowercased().contains(q) || ($0.name?.lowercased().contains(q) == true) }
        }
        return items
    }

    public func task(id: String) -> ScheduledWork? {
        tasksMap[id]
    }

    public func attempts(taskId: String) -> [ExecutionAttempt] {
        attemptsMap[taskId] ?? []
    }

    public func events(taskId: String) -> [TaskLensEvent] {
        eventsList.filter { $0.taskId == taskId }
            .sorted { $0.timestamp < $1.timestamp }
    }

    public func diagnoses(taskId: String) -> [Diagnosis] {
        diagnosesMap[taskId] ?? []
    }

    public func environmentEvents(limit: Int) -> [TaskLensEvent] {
        eventsList.filter {
            $0.type == .networkChanged ||
                $0.type == .batteryChanged ||
                $0.type == .powerModeChanged ||
                $0.type == .appStateChanged
        }.suffix(limit)
    }

    public func clear() {
        tasksMap.removeAll()
        attemptsMap.removeAll()
        eventsList.removeAll()
        diagnosesMap.removeAll()
    }

    public func applyRetention(_ policy: RetentionPolicy) {
        // Enforce retention window
        switch policy {
        case .forever:
            break
        case .maxTasks(let count):
            if tasksMap.count > count {
                let toRemove = tasksMap.keys.prefix(tasksMap.count - count)
                for id in toRemove {
                    tasksMap.removeValue(forKey: id)
                    attemptsMap.removeValue(forKey: id)
                    eventsList.removeAll { $0.taskId == id }
                    diagnosesMap.removeValue(forKey: id)
                }
            }
        case .lastDays(let days):
            let cutoff = Date().addingTimeInterval(-Double(days * 86400))
            let expiredIds = tasksMap.values.filter { ($0.submittedAt ?? Date.distantPast) < cutoff }.map { $0.id }
            for id in expiredIds {
                tasksMap.removeValue(forKey: id)
                attemptsMap.removeValue(forKey: id)
                eventsList.removeAll { $0.taskId == id }
                diagnosesMap.removeValue(forKey: id)
            }
        }
    }
}
