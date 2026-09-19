package dev.shushant.tasklens.foreground

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import dev.shushant.tasklens.android.TaskLens
import dev.shushant.tasklens.core.EventSeverity
import dev.shushant.tasklens.core.EventSource
import dev.shushant.tasklens.core.EventType
import dev.shushant.tasklens.core.TaskLensEvent

/**
 * A [Service] subclass that integrates TaskLens instrumentation into a foreground service.
 *
 * ## Usage
 *
 * 1. Declare a concrete subclass in your app manifest with the desired `foregroundServiceType`.
 * 2. Override [onStartForeground] to perform your long-running work.
 * 3. Call [startForeground] from within [onStartCommand] using [buildNotification].
 *
 * TaskLens events are automatically emitted for start, stop, and error lifecycle transitions.
 */
abstract class TaskLensForegroundService : Service() {

    // -----------------------------------------------------------------------
    // Subclass contract
    // -----------------------------------------------------------------------

    /** Must return a non-null [Notification] that will be used to start the service in the foreground. */
    abstract fun buildNotification(): Notification

    /** Called once the service has been started in the foreground. Perform your work here. */
    open fun onStartForeground(intent: Intent?, flags: Int, startId: Int) = Unit

    /** Notification channel configuration used by [ForegroundNotificationBuilder]. */
    open val notificationChannelId: String get() = "tasklens_foreground"
    open val notificationId: Int get() = 0x544c /* "TL" */

    // -----------------------------------------------------------------------
    // Lifecycle
    // -----------------------------------------------------------------------

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ForegroundNotificationBuilder.ensureChannel(this, notificationChannelId)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ""
        val taskId = intent?.getStringExtra(EXTRA_TASK_ID)

        startForeground(notificationId, buildNotification())

        TaskLens.emit(
            TaskLensEvent(
                taskId = taskId,
                type = EventType.TASK_STARTED,
                source = EventSource.FOREGROUND_SERVICE,
                severity = EventSeverity.INFO,
                attributes = mapOf(
                    "service_class" to javaClass.simpleName,
                    "action" to action,
                    "start_id" to startId.toString()
                )
            )
        )

        try {
            onStartForeground(intent, flags, startId)
        } catch (t: Throwable) {
            TaskLens.emit(
                TaskLensEvent(
                    taskId = taskId,
                    type = EventType.TASK_FAILED,
                    source = EventSource.FOREGROUND_SERVICE,
                    severity = EventSeverity.ERROR,
                    attributes = mapOf(
                        "service_class" to javaClass.simpleName,
                        "error_type" to (t::class.simpleName ?: "Unknown"),
                        "error_message" to (t.message?.take(500) ?: "")
                    )
                )
            )
        }

        return START_STICKY
    }

    override fun onDestroy() {
        TaskLens.emit(
            TaskLensEvent(
                type = EventType.TASK_STOPPED,
                source = EventSource.FOREGROUND_SERVICE,
                severity = EventSeverity.INFO,
                attributes = mapOf("service_class" to javaClass.simpleName)
            )
        )
        super.onDestroy()
    }

    companion object {
        const val EXTRA_TASK_ID = "tasklens_task_id"
    }
}

// ---------------------------------------------------------------------------
// ForegroundNotificationBuilder
// ---------------------------------------------------------------------------

/**
 * Utility for building a minimal foreground-service notification and ensuring
 * the notification channel exists.
 */
object ForegroundNotificationBuilder {

    /**
     * Ensures a [NotificationChannel] with [channelId] exists.  Safe to call multiple times.
     */
    fun ensureChannel(
        context: Context,
        channelId: String,
        channelName: String = "Background Tasks",
        importance: Int = NotificationManager.IMPORTANCE_LOW
    ) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(channelId) == null) {
            nm.createNotificationChannel(
                NotificationChannel(channelId, channelName, importance)
            )
        }
    }

    /**
     * Builds a simple ongoing notification for foreground-service use.
     *
     * @param context       Application context.
     * @param channelId     Notification channel id (must already exist).
     * @param title         Notification title text.
     * @param contentText   Notification body text.
     * @param tapIntent     Optional [PendingIntent] fired when the notification is tapped.
     * @param iconResId     Small icon resource.  Defaults to `android.R.drawable.ic_dialog_info`.
     */
    fun build(
        context: Context,
        channelId: String,
        title: String,
        contentText: String = "",
        tapIntent: PendingIntent? = null,
        iconResId: Int = android.R.drawable.ic_dialog_info
    ): Notification {
        val builder = Notification.Builder(context, channelId)
            .setSmallIcon(iconResId)
            .setContentTitle(title)
            .setContentText(contentText)
            .setOngoing(true)

        if (tapIntent != null) {
            builder.setContentIntent(tapIntent)
        }

        return builder.build()
    }
}

// ---------------------------------------------------------------------------
// ForegroundTaskTracker
// ---------------------------------------------------------------------------

/**
 * Lightweight helper that emits TaskLens events when a long-running operation
 * managed by a foreground service starts and stops, without requiring the caller
 * to subclass [TaskLensForegroundService].
 */
object ForegroundTaskTracker {

    fun trackStart(taskId: String, serviceClass: String, attributes: Map<String, String> = emptyMap()) {
        TaskLens.emit(
            TaskLensEvent(
                taskId = taskId,
                type = EventType.TASK_STARTED,
                source = EventSource.FOREGROUND_SERVICE,
                severity = EventSeverity.INFO,
                attributes = attributes + mapOf("service_class" to serviceClass)
            )
        )
    }

    fun trackStop(
        taskId: String,
        serviceClass: String,
        succeeded: Boolean,
        attributes: Map<String, String> = emptyMap()
    ) {
        val type = if (succeeded) EventType.TASK_SUCCEEDED else EventType.TASK_FAILED
        TaskLens.emit(
            TaskLensEvent(
                taskId = taskId,
                type = type,
                source = EventSource.FOREGROUND_SERVICE,
                severity = if (succeeded) EventSeverity.INFO else EventSeverity.ERROR,
                attributes = attributes + mapOf("service_class" to serviceClass)
            )
        )
    }
}
