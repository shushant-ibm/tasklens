import Foundation

public protocol TaskLensRedactor {
    func redact(key: String, value: String) -> String
}

public struct DefaultTaskLensRedactor: TaskLensRedactor {
    public static let shared = DefaultTaskLensRedactor()

    private let sensitivePatterns = [
        "authorization", "auth", "password", "secret", "token",
        "apikey", "api_key", "key", "cookie", "credential",
        "session", "jwt", "bearer"
    ]

    private let maxAttributeLength = 2048
    private let truncationMarker = " [TRUNCATED]"

    public init() {}

    public func redact(key: String, value: String) -> String {
        let lower = key.lowercased()
        if sensitivePatterns.contains(where: { lower.contains($0) }) {
            return "[REDACTED]"
        }
        if value.count > maxAttributeLength {
            let keepCount = maxAttributeLength - truncationMarker.count
            return String(value.prefix(keepCount)) + truncationMarker
        }
        return value
    }
}
