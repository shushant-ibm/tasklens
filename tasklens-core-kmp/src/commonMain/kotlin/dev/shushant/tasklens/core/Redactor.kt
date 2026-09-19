package dev.shushant.tasklens.core

interface TaskLensRedactor {
    fun redact(key: String, value: String): String
}

object DefaultTaskLensRedactor : TaskLensRedactor {
    private val sensitivePatterns = listOf(
        "authorization",
        "auth",
        "password",
        "secret",
        "token",
        "apikey",
        "api_key",
        "access_token",
        "refresh_token",
        "key",
        "cookie",
        "credential",
        "session",
        "jwt",
        "bearer",
        "credit_card",
        "card_number",
        "cvv"
    )

    private const val MAX_ATTRIBUTE_LENGTH = 2048 // 2 KB
    private const val TRUNCATION_MARKER = " [TRUNCATED]"

    override fun redact(key: String, value: String): String {
        val lowerKey = key.lowercase()
        if (sensitivePatterns.any { lowerKey.contains(it) }) {
            return "[REDACTED]"
        }
        return if (value.length > MAX_ATTRIBUTE_LENGTH) {
            value.take(MAX_ATTRIBUTE_LENGTH - TRUNCATION_MARKER.length) + TRUNCATION_MARKER
        } else {
            value
        }
    }

    fun redactMap(map: Map<String, String>): Map<String, String> {
        return map.mapValues { (k, v) -> redact(k, v) }
    }
}
