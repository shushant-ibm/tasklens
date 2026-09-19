package dev.shushant.tasklens.export

import kotlinx.serialization.Serializable

@Serializable
data class ExportPrivacy(
    val payloadsIncluded: Boolean = false,
    val networkBodiesIncluded: Boolean = false
)

@Serializable
data class ExportManifest(
    val schemaVersion: Int = 1,
    val taskLensVersion: String = "0.1.0",
    val platform: String,
    val appVersion: String,
    val createdAt: String,
    val taskId: String,
    val privacy: ExportPrivacy = ExportPrivacy()
)
