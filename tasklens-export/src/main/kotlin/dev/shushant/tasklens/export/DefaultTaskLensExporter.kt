package dev.shushant.tasklens.export

import dev.shushant.tasklens.core.TaskLensRedactor
import dev.shushant.tasklens.storage.TaskLensStore
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Default implementation of [TaskLensExporter].
 *
 * Writes a `.tasklens` ZIP archive containing eight named entries:
 *   manifest.json, task.json, attempts.json, timeline.json,
 *   diagnosis.json, events.json, device.json, README.html
 *
 * Attribute values in `events.json` are run through [TaskLensRedactor] before writing.
 * The archive is written to a temp file then renamed atomically.
 */
class DefaultTaskLensExporter(
    private val store: TaskLensStore,
    @Suppress("UNUSED_PARAMETER") format: ExportFormat = ExportFormat.Json
) : TaskLensExporter {

    private val json = Json {
        prettyPrint = false
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    override suspend fun export(
        taskId: String,
        outputFile: File,
        includePayloads: Boolean,
        deviceMetadata: Map<String, String>,
        appVersion: String,
        platform: String,
        redactor: TaskLensRedactor
    ): File {
        outputFile.parentFile?.mkdirs()

        val events    = store.events(taskId)
        val attempts  = store.attempts(taskId)
        val diagnoses = store.diagnoses(taskId)
        val task      = store.task(taskId)

        // ── manifest.json ────────────────────────────────────────────────────
        val manifestJson = json.encodeToString(buildJsonObject {
            put("schema_version", 1)
            put("task_id", taskId)
            put("platform", platform)
            put("app_version", appVersion)
            put("exported_at_epoch_ms", System.currentTimeMillis())
        })

        // ── task.json ────────────────────────────────────────────────────────
        val taskJson = json.encodeToString(if (task != null) buildJsonObject {
            put("id", task.id)
            put("name", task.name ?: "")
            put("scheduler_type", task.scheduler.name)
            put("type", task.type.name)
            put("periodic", task.periodic)
        } else buildJsonObject {})

        // ── attempts.json ────────────────────────────────────────────────────
        val attemptsJson = json.encodeToString(buildJsonArray {
            attempts.forEach { attempt ->
                val startedAt = attempt.startedAt
                val endedAt   = attempt.endedAt
                val durationMs: Long = if (startedAt != null && endedAt != null) {
                    endedAt.toEpochMilliseconds() - startedAt.toEpochMilliseconds()
                } else -1L
                val stopReason = attempt.platformReason?.name ?: ""

                add(buildJsonObject {
                    put("id", attempt.attemptId)
                    put("attempt_id", attempt.attemptId)
                    put("attemptId", attempt.attemptId)
                    put("task_id", attempt.taskId)
                    put("attempt_number", attempt.attemptNumber)
                    put("outcome", attempt.outcome?.name ?: "UNKNOWN")
                    put("started_at_epoch_ms", attempt.startedAt?.toEpochMilliseconds() ?: -1L)
                    put("ended_at_epoch_ms", attempt.endedAt?.toEpochMilliseconds() ?: -1L)
                    put("duration_ms", durationMs)
                    put("stop_reason", stopReason)
                })
            }
        })

        // ── timeline.json  (chronological event summaries) ───────────────────
        val sortedEvents = events.sortedBy { it.timestamp }
        val timelineJson = json.encodeToString(buildJsonArray {
            sortedEvents.forEach { event ->
                add(buildJsonObject {
                    put("type", event.type.name)
                    put("timestamp_epoch_ms", event.timestamp.toEpochMilliseconds())
                    putJsonObject("attributes") {
                        event.attributes.forEach { (k, v) -> put(k, v) }
                    }
                })
            }
        })

        // ── diagnosis.json ───────────────────────────────────────────────────
        val diagnosisJson = json.encodeToString(buildJsonArray {
            diagnoses.forEach { diagnosis ->
                add(buildJsonObject {
                    put("id", diagnosis.id)
                    put("task_id", diagnosis.taskId)
                    put("rule_id", diagnosis.ruleId)
                    put("title", diagnosis.title)
                    put("summary", diagnosis.summary)
                    put("confidence", diagnosis.confidence.name)
                    put("classification", diagnosis.classification.name)
                })
            }
        })

        // ── events.json  (with redaction) ─────────────────────────────────────
        val eventsJson = json.encodeToString(buildJsonArray {
            events.forEach { event ->
                add(buildJsonObject {
                    put("id", event.id)
                    put("task_id", event.taskId ?: "")
                    put("attempt_id", event.attemptId ?: "")
                    put("timestamp_epoch_ms", event.timestamp.toEpochMilliseconds())
                    put("sequence_number", event.sequenceNumber)
                    put("type", event.type.name)
                    put("source", event.source.name)
                    put("severity", event.severity.name)
                    put("schema_version", event.schemaVersion)
                    putJsonObject("attributes") {
                        event.attributes.forEach { (k, v) ->
                            put(k, redactor.redact(k, v))
                        }
                    }
                })
            }
        })

        // ── device.json ───────────────────────────────────────────────────────
        val deviceJson = json.encodeToString(buildJsonObject {
            deviceMetadata.forEach { (k, v) -> put(k, v) }
        })

        // ── README.html ───────────────────────────────────────────────────────
        val taskName = task?.name ?: taskId
        val stopReasons = attempts
            .mapNotNull { it.platformReason?.name }
            .distinct()
            .joinToString(", ")
        val readmeHtml = buildString {
            appendLine("<!DOCTYPE html>")
            appendLine("<html><head><meta charset=\"utf-8\">")
            appendLine("<title>TaskLens Export — $taskName</title></head>")
            appendLine("<body>")
            appendLine("<h1>TaskLens Export</h1>")
            appendLine("<h2>Task: $taskName</h2>")
            appendLine("<p>Platform: $platform</p>")
            appendLine("<p>App version: $appVersion</p>")
            appendLine("<p>Events: ${events.size}</p>")
            appendLine("<p>Attempts: ${attempts.size}</p>")
            if (stopReasons.isNotEmpty()) {
                appendLine("<p>Stop reasons: $stopReasons</p>")
            }
            appendLine("</body></html>")
        }

        // ── evidence.json ────────────────────────────────────────────────────
        val allEvidence = diagnoses.flatMap { it.evidence }
        val evidenceJson = json.encodeToString(allEvidence)

        // ── environment.json ─────────────────────────────────────────────────
        val envEvents = store.environmentEvents(100)
        val environmentJson = json.encodeToString(envEvents)

        // ── limitations.json ─────────────────────────────────────────────────
        val allLimitations = diagnoses.flatMap { it.limitations }
        val limitationsJson = json.encodeToString(allLimitations)

        // ── Write ZIP atomically ──────────────────────────────────────────────
        val tmp = File(outputFile.parent, "${outputFile.name}.tmp")
        try {
            ZipOutputStream(tmp.outputStream().buffered()).use { zos ->
                fun addEntry(name: String, content: String) {
                    zos.putNextEntry(ZipEntry(name))
                    zos.write(content.toByteArray(Charsets.UTF_8))
                    zos.closeEntry()
                }
                addEntry("manifest.json",     manifestJson)
                addEntry("task.json",         taskJson)
                addEntry("attempts.json",     attemptsJson)
                addEntry("timeline.json",     timelineJson)
                addEntry("diagnosis.json",    diagnosisJson)
                addEntry("evidence.json",     evidenceJson)
                addEntry("environment.json",  environmentJson)
                addEntry("limitations.json",  limitationsJson)
                addEntry("events.json",        eventsJson)
                addEntry("device.json",        deviceJson)
                addEntry("README.html",        readmeHtml)
            }

            if (!tmp.renameTo(outputFile)) {
                outputFile.outputStream().use { out ->
                    tmp.inputStream().use { it.copyTo(out) }
                }
                tmp.delete()
            }
        } catch (t: Throwable) {
            tmp.delete()
            throw t
        }

        return outputFile
    }
}
