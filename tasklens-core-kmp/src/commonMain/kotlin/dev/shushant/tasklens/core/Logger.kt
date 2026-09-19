package dev.shushant.tasklens.core

enum class LogLevel {
    ERROR,
    WARN,
    INFO,
    DEBUG,
    TRACE
}

interface TaskLensLogger {
    fun log(level: LogLevel, tag: String, message: String, throwable: Throwable? = null)
}

object NoOpTaskLensLogger : TaskLensLogger {
    override fun log(level: LogLevel, tag: String, message: String, throwable: Throwable?) = Unit
}

class PrintTaskLensLogger(
    private val minLevel: LogLevel = LogLevel.INFO
) : TaskLensLogger {
    override fun log(level: LogLevel, tag: String, message: String, throwable: Throwable?) {
        if (level.ordinal <= minLevel.ordinal) {
            println("[TaskLens][${level.name}][$tag] $message")
            throwable?.printStackTrace()
        }
    }
}
