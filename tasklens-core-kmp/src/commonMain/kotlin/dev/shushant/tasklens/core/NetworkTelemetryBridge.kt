package dev.shushant.tasklens.core

interface NetworkTelemetryBridge {
    fun start()
    fun stop()
    fun isConnected(): Boolean
}

object NoOpNetworkTelemetryBridge : NetworkTelemetryBridge {
    override fun start() = Unit
    override fun stop() = Unit
    override fun isConnected(): Boolean = false
}
