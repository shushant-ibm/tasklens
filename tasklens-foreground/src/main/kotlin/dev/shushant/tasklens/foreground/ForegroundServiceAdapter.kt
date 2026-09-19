package dev.shushant.tasklens.foreground

data class ForegroundServiceSnapshot(
    val serviceClass: String,
    val notificationId: Int,
    val foregroundServiceType: Int,
    val startedAtMs: Long,
    val isPromoted: Boolean
)

interface ForegroundServiceAdapter {
    fun start()
    fun stop()
    suspend fun snapshot(serviceClass: String): ForegroundServiceSnapshot?
}

class DefaultForegroundServiceAdapter : ForegroundServiceAdapter {
    override fun start() = Unit
    override fun stop() = Unit
    override suspend fun snapshot(serviceClass: String): ForegroundServiceSnapshot? = null
}
