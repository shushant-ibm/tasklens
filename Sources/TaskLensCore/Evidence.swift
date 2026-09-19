import Foundation

public enum EvidenceType: String, Codable {
    case platformReason = "PLATFORM_REASON"
    case stateTransition = "STATE_TRANSITION"
    case constraintState = "CONSTRAINT_STATE"
    case networkState = "NETWORK_STATE"
    case powerState = "POWER_STATE"
    case lifecycleState = "LIFECYCLE_STATE"
    case expirationHandler = "EXPIRATION_HANDLER"
    case completionResult = "COMPLETION_RESULT"
    case retrySignal = "RETRY_SIGNAL"
    case exception = "EXCEPTION"
    case timeCorrelation = "TIME_CORRELATION"
    case configValidation = "CONFIG_VALIDATION"
}

public enum EvidenceSource: String, Codable {
    case platform = "PLATFORM"
    case environment = "ENVIRONMENT"
    case application = "APPLICATION"
    case derived = "DERIVED"
    case correlation = "CORRELATION"
}

public struct Evidence: Codable, Identifiable {
    public let id: String
    public let type: EvidenceType
    public let source: EvidenceSource
    public let timestamp: Date?
    public let title: String
    public let description: String
    public let sourceEventIds: [String]
    public let metadata: [String: String]

    public init(
        id: String = UUID().uuidString,
        type: EvidenceType,
        source: EvidenceSource,
        timestamp: Date? = nil,
        title: String,
        description: String,
        sourceEventIds: [String] = [],
        metadata: [String: String] = [:]
    ) {
        self.id = id
        self.type = type
        self.source = source
        self.timestamp = timestamp
        self.title = title
        self.description = description
        self.sourceEventIds = sourceEventIds
        self.metadata = metadata
    }
}
