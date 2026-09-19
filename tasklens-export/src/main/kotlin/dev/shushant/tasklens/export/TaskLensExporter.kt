package dev.shushant.tasklens.export

import dev.shushant.tasklens.core.TaskLensRedactor
import kotlin.time.Clock
import kotlinx.serialization.Serializable
import java.io.File
import kotlinx.serialization.Transient

// ---------------------------------------------------------------------------
// Export model types
// ---------------------------------------------------------------------------

/** Supported export formats for a TaskLens trace bundle. */
@Serializable
sealed class ExportFormat {
    /** Compact JSON — one JSON object per line (JSON-ND). */
    @Serializable data object Json : ExportFormat()
    /** RFC-4180 CSV, header row included. */
    @Serializable data object Csv : ExportFormat()
    /** Raw SQLite database file (copy of the on-device store). */
    @Serializable data object Sqlite : ExportFormat()
}

/** Criteria used to narrow down which events are included in an export. */
@Serializable
data class ExportFilter(
    /** Only export events whose `taskId` is in this set. Null = all tasks. */
    val taskIds: Set<String>? = null,
    /** Only include events whose `type` name matches one of these strings. */
    val eventTypes: Set<String>? = null,
    /** Earliest timestamp (epoch-millis) to include. */
    val fromEpochMillis: Long? = null,
    /** Latest timestamp (epoch-millis) to include. */
    val toEpochMillis: Long? = null
)

/** Outcome of a completed export operation. */
@Serializable
data class ExportResult(
    val filePath: String,
    val format: ExportFormat = ExportFormat.Json,
    val taskId: String,
    val eventCount: Int,
    val exportedAt: Long = Clock.System.now().toEpochMilliseconds(),
    val warnings: List<String> = emptyList()
) {
    @Transient
    val file: File get() = File(filePath)
}

// ---------------------------------------------------------------------------
// Exporter interface
// ---------------------------------------------------------------------------

/**
 * Writes a TaskLens trace bundle (ZIP archive) for a given task to a [File].
 *
 * Implementations must be safe to call from a coroutine (they may perform I/O).
 */
interface TaskLensExporter {
    /**
     * Exports the full trace for [taskId] to [outputFile] as a `.tasklens` ZIP archive.
     *
     * The archive contains: manifest.json, task.json, attempts.json, timeline.json,
     * diagnosis.json, events.json, device.json, README.html.
     *
     * @param taskId         The task whose trace should be exported.
     * @param outputFile     Destination file. Parent directory must exist.
     * @param includePayloads Whether to embed raw worker input/output payloads.
     * @param deviceMetadata Arbitrary string key/value metadata embedded in device.json.
     * @param appVersion     Host application version string.
     * @param platform       Platform identifier ("android", "ios", …).
     * @param redactor       Applied to every attribute value before writing.
     * @return The [outputFile] after a successful write.
     */
    suspend fun export(
        taskId: String,
        outputFile: File,
        includePayloads: Boolean = false,
        deviceMetadata: Map<String, String> = emptyMap(),
        appVersion: String = "",
        platform: String = "android",
        redactor: TaskLensRedactor
    ): File
}
