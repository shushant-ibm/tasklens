package dev.shushant.tasklens.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.shushant.tasklens.android.TaskLens
import dev.shushant.tasklens.core.EventSeverity
import dev.shushant.tasklens.core.EventSource
import dev.shushant.tasklens.core.EventType
import dev.shushant.tasklens.core.TaskLensEvent

// ---------------------------------------------------------------------------
// AlarmTaskLensReceiver
// ---------------------------------------------------------------------------

/**
 * A [BroadcastReceiver] that captures alarm-fired events and forwards them
 * to TaskLens.  Host apps should register their own receiver that delegates
 * to this class, or declare this receiver directly in their manifest.
 *
 * Subclass this receiver and override [onAlarmFired] to add custom handling.
 */
open class AlarmTaskLensReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AlarmManagerAdapter.ACTION_ALARM_FIRED) return

        val taskId = intent.getStringExtra(AlarmManagerAdapter.EXTRA_TASK_ID)
        val alarmTag = intent.getStringExtra(AlarmManagerAdapter.EXTRA_ALARM_TAG) ?: ""

        TaskLens.emit(
            TaskLensEvent(
                taskId = taskId,
                type = EventType.TASK_LAUNCHED,
                source = EventSource.ALARM_MANAGER,
                severity = EventSeverity.INFO,
                attributes = mapOf(
                    "alarm_tag" to alarmTag,
                    "action" to (intent.action ?: "")
                )
            )
        )

        onAlarmFired(context, intent, taskId, alarmTag)
    }

    /** Override to perform work when the alarm fires.  Called after the TaskLens event is emitted. */
    open fun onAlarmFired(context: Context, intent: Intent, taskId: String?, alarmTag: String) = Unit
}

// ---------------------------------------------------------------------------
// AlarmManagerAdapter
// ---------------------------------------------------------------------------

/**
 * Wraps [AlarmManager] to schedule one-shot and repeating alarms with
 * automatic TaskLens event instrumentation.
 */
class AlarmManagerAdapter(private val context: Context) {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    /**
     * Schedules a one-shot exact alarm.
     *
     * @param taskId       TaskLens task identifier (embedded in the broadcast intent).
     * @param triggerAtMs  Absolute elapsed-realtime trigger time in epoch milliseconds.
     * @param alarmTag     Human-readable label for the alarm.
     * @param receiverClass The [BroadcastReceiver] class that should handle the alarm.
     * @param requestCode  PendingIntent request code (must be unique per alarm).
     */
    fun scheduleOneShot(
        taskId: String,
        triggerAtMs: Long,
        alarmTag: String,
        receiverClass: Class<out BroadcastReceiver>,
        requestCode: Int = taskId.hashCode()
    ) {
        val pending = buildPendingIntent(context, taskId, alarmTag, receiverClass, requestCode)
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pending)

        TaskLens.emit(
            TaskLensEvent(
                taskId = taskId,
                type = EventType.TASK_REGISTERED,
                source = EventSource.ALARM_MANAGER,
                severity = EventSeverity.INFO,
                attributes = mapOf(
                    "alarm_tag" to alarmTag,
                    "trigger_at_epoch_ms" to triggerAtMs.toString(),
                    "alarm_type" to "one_shot"
                )
            )
        )
    }

    /**
     * Schedules a repeating inexact alarm.
     *
     * @param taskId       TaskLens task identifier.
     * @param firstTriggerMs  Time for the first alarm (epoch millis).
     * @param intervalMs   Interval between subsequent alarms.
     * @param alarmTag     Human-readable label.
     * @param receiverClass Target receiver.
     * @param requestCode  PendingIntent request code.
     */
    fun scheduleRepeating(
        taskId: String,
        firstTriggerMs: Long,
        intervalMs: Long,
        alarmTag: String,
        receiverClass: Class<out BroadcastReceiver>,
        requestCode: Int = taskId.hashCode()
    ) {
        val pending = buildPendingIntent(context, taskId, alarmTag, receiverClass, requestCode)
        alarmManager.setInexactRepeating(AlarmManager.RTC_WAKEUP, firstTriggerMs, intervalMs, pending)

        TaskLens.emit(
            TaskLensEvent(
                taskId = taskId,
                type = EventType.TASK_REGISTERED,
                source = EventSource.ALARM_MANAGER,
                severity = EventSeverity.INFO,
                attributes = mapOf(
                    "alarm_tag" to alarmTag,
                    "first_trigger_epoch_ms" to firstTriggerMs.toString(),
                    "interval_ms" to intervalMs.toString(),
                    "alarm_type" to "repeating"
                )
            )
        )
    }

    /**
     * Cancels a previously scheduled alarm.
     *
     * @param taskId       The task ID used when scheduling.
     * @param alarmTag     The tag used when scheduling.
     * @param receiverClass The receiver class used when scheduling.
     * @param requestCode  The request code used when scheduling.
     */
    fun cancel(
        taskId: String,
        alarmTag: String,
        receiverClass: Class<out BroadcastReceiver>,
        requestCode: Int = taskId.hashCode()
    ) {
        val pending = buildPendingIntent(context, taskId, alarmTag, receiverClass, requestCode)
        alarmManager.cancel(pending)

        TaskLens.emit(
            TaskLensEvent(
                taskId = taskId,
                type = EventType.TASK_CANCELLED,
                source = EventSource.ALARM_MANAGER,
                severity = EventSeverity.INFO,
                attributes = mapOf("alarm_tag" to alarmTag)
            )
        )
    }

    // -----------------------------------------------------------------------
    // Internal helpers
    // -----------------------------------------------------------------------

    private fun buildPendingIntent(
        context: Context,
        taskId: String,
        alarmTag: String,
        receiverClass: Class<out BroadcastReceiver>,
        requestCode: Int
    ): PendingIntent {
        val intent = Intent(context, receiverClass).apply {
            action = ACTION_ALARM_FIRED
            putExtra(EXTRA_TASK_ID, taskId)
            putExtra(EXTRA_ALARM_TAG, alarmTag)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        const val ACTION_ALARM_FIRED = "dev.shushant.tasklens.alarm.ACTION_ALARM_FIRED"
        const val EXTRA_TASK_ID = "tasklens_task_id"
        const val EXTRA_ALARM_TAG = "tasklens_alarm_tag"
    }
}

// ---------------------------------------------------------------------------
// AlarmTaskTracker
// ---------------------------------------------------------------------------

/**
 * Convenience object for emitting TaskLens events from alarm-driven tasks
 * without subclassing [AlarmTaskLensReceiver].
 */
object AlarmTaskTracker {

    fun trackStart(taskId: String, alarmTag: String, attributes: Map<String, String> = emptyMap()) {
        TaskLens.emit(
            TaskLensEvent(
                taskId = taskId,
                type = EventType.TASK_STARTED,
                source = EventSource.ALARM_MANAGER,
                severity = EventSeverity.INFO,
                attributes = attributes + mapOf("alarm_tag" to alarmTag)
            )
        )
    }

    fun trackSuccess(taskId: String, alarmTag: String, attributes: Map<String, String> = emptyMap()) {
        TaskLens.emit(
            TaskLensEvent(
                taskId = taskId,
                type = EventType.TASK_SUCCEEDED,
                source = EventSource.ALARM_MANAGER,
                severity = EventSeverity.INFO,
                attributes = attributes + mapOf("alarm_tag" to alarmTag)
            )
        )
    }

    fun trackFailure(
        taskId: String,
        alarmTag: String,
        error: Throwable? = null,
        attributes: Map<String, String> = emptyMap()
    ) {
        TaskLens.emit(
            TaskLensEvent(
                taskId = taskId,
                type = EventType.TASK_FAILED,
                source = EventSource.ALARM_MANAGER,
                severity = EventSeverity.ERROR,
                attributes = attributes + mapOf(
                    "alarm_tag" to alarmTag,
                    "error_type" to (error?.let { it::class.simpleName } ?: ""),
                    "error_message" to (error?.message?.take(500) ?: "")
                )
            )
        )
    }
}
