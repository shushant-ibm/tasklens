package dev.shushant.tasklens.workmanager

import dev.shushant.tasklens.core.PlatformReason

object WorkManagerStopReasons {

    // WorkManager / JobParameters stop reason codes
    const val STOP_REASON_NOT_STOPPED = 0
    const val STOP_REASON_UNKNOWN = -512
    const val STOP_REASON_CANCELLED_BY_APP = 1
    const val STOP_REASON_PREEMPT = 2
    const val STOP_REASON_CONSTRAINT_BATTERY_NOT_LOW = 4
    const val STOP_REASON_CONSTRAINT_CHARGING = 5
    const val STOP_REASON_CONSTRAINT_CONNECTIVITY = 7
    const val STOP_REASON_CONSTRAINT_DEVICE_IDLE = 8
    const val STOP_REASON_CONSTRAINT_STORAGE_NOT_LOW = 9
    const val STOP_REASON_TIMEOUT = 3
    const val STOP_REASON_QUOTA = 10
    const val STOP_REASON_BACKGROUND_RESTRICTION = 11

    fun toPlatformReason(code: Int): PlatformReason {
        val (name, desc) = when (code) {
            STOP_REASON_CANCELLED_BY_APP -> "STOP_REASON_CANCELLED_BY_APP" to "The application requested this worker be cancelled."
            STOP_REASON_PREEMPT -> "STOP_REASON_PREEMPT" to "The job was preempted by higher priority work."
            STOP_REASON_TIMEOUT -> "STOP_REASON_TIMEOUT" to "Execution time exceeded the platform execution limit."
            STOP_REASON_CONSTRAINT_BATTERY_NOT_LOW -> "STOP_REASON_CONSTRAINT_BATTERY_NOT_LOW" to "Battery level dropped below required threshold."
            STOP_REASON_CONSTRAINT_CHARGING -> "STOP_REASON_CONSTRAINT_CHARGING" to "Device stopped charging while charging constraint was active."
            STOP_REASON_CONSTRAINT_CONNECTIVITY -> "STOP_REASON_CONSTRAINT_CONNECTIVITY" to "Network connectivity lost or no longer satisfies constraint."
            STOP_REASON_CONSTRAINT_DEVICE_IDLE -> "STOP_REASON_CONSTRAINT_DEVICE_IDLE" to "Device is no longer idle."
            STOP_REASON_CONSTRAINT_STORAGE_NOT_LOW -> "STOP_REASON_CONSTRAINT_STORAGE_NOT_LOW" to "Device storage is critically low."
            STOP_REASON_QUOTA -> "STOP_REASON_QUOTA" to "App background execution quota exhausted."
            STOP_REASON_BACKGROUND_RESTRICTION -> "STOP_REASON_BACKGROUND_RESTRICTION" to "System background restrictions prevented execution."
            else -> "STOP_REASON_$code" to "Platform stopped worker with reason code $code"
        }
        return PlatformReason(code = code, name = name, description = desc)
    }
}
