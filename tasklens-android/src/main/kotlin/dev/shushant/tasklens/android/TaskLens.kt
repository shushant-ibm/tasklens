package dev.shushant.tasklens.android

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import dev.shushant.tasklens.android.collectors.AndroidCollector
import dev.shushant.tasklens.android.collectors.AppStateMonitor
import dev.shushant.tasklens.android.collectors.BatteryMonitor
import dev.shushant.tasklens.android.collectors.NetworkMonitor
import dev.shushant.tasklens.android.collectors.PowerManagerMonitor
import dev.shushant.tasklens.android.storage.AndroidSqliteTaskLensStore
import dev.shushant.tasklens.core.DefaultCorrelationEngine
import dev.shushant.tasklens.core.EventSeverity
import dev.shushant.tasklens.core.EventSource
import dev.shushant.tasklens.core.EventType
import dev.shushant.tasklens.core.HealthState
import dev.shushant.tasklens.core.LogLevel
import dev.shushant.tasklens.core.ScheduledWork
import dev.shushant.tasklens.core.TaskLensEvent
import dev.shushant.tasklens.export.DefaultTaskLensExporter
import dev.shushant.tasklens.storage.TaskLensStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.CoroutineContext

object TaskLens {

    private const val TAG = "TaskLens"

    private val isInstalled = AtomicBoolean(false)
    private lateinit var applicationContext: Context
    private lateinit var currentConfig: TaskLensConfig
    private lateinit var storeInstance: TaskLensStore

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val eventChannel = Channel<TaskLensEvent>(
        capacity = 1000,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private val activeCollectors = mutableListOf<AndroidCollector>()
    private val correlationEngine = DefaultCorrelationEngine()
    private var workManagerAdapter: dev.shushant.tasklens.workmanager.DefaultWorkManagerAdapter? = null
    private var kourierBridge: dev.shushant.tasklens.kourier.KourierTelemetryBridge? = null

    // Sub-system health flags — set to false when the sub-system fails to start
    @Volatile private var storageHealthy = true
    @Volatile private var kourierHealthy = true
    @Volatile private var exportHealthy = true

    private val currentCorrelationToken = ThreadLocal<String?>()

    /** Terminal event types that trigger attempt-reconstruction and diagnosis. */
    private val DIAGNOSIS_TRIGGER_TYPES = setOf(
        EventType.TASK_RETRY_REQUESTED,
        EventType.TASK_STOPPED,
        EventType.TASK_FAILED,
        EventType.TASK_EXPIRED,
        EventType.TASK_CANCELLED,
        EventType.TASK_SUCCEEDED
    )

    fun isInstalled(): Boolean = isInstalled.get()

    fun install(
        application: Application,
        config: TaskLensConfig = TaskLensConfig()
    ) {
        if (!isInstalled.compareAndSet(false, true)) {
            config.logger.log(LogLevel.WARN, TAG, "TaskLens is already installed. Ignoring duplicate call.")
            return
        }

        applicationContext = application.applicationContext
        currentConfig = config
        try {
            storeInstance = AndroidSqliteTaskLensStore(applicationContext)
        } catch (t: Throwable) {
            storageHealthy = false
            throw t // storage is required; propagate so caller knows install failed
        }

        // Initialize UI Bridge
        dev.shushant.tasklens.ui.TaskLensBridge.storeProvider = { storeInstance }
        dev.shushant.tasklens.ui.TaskLensBridge.exportProvider = { taskId -> export(taskId) }
        dev.shushant.tasklens.ui.TaskLensBridge.clearProvider = { clear() }

        config.logger.log(LogLevel.INFO, TAG, "Installing TaskLens Android SDK (v0.1.0)...")

        // Start background event processing loop
        scope.launch {
            processEvents()
        }

        // Apply retention asynchronously at startup
        scope.launch {
            try {
                storeInstance.applyRetention(config.retentionPolicy)
            } catch (t: Throwable) {
                config.logger.log(LogLevel.ERROR, TAG, "Failed to apply retention policy at startup", t)
            }
        }

        // Start environment collectors if enabled
        if (config.captureEnvironment) {
            startCollectors(application)
        }

        // Auto-wire KourierTelemetryBridge if enabled
        if (config.enableKourierBridge) {
            try {
                val bridge = dev.shushant.tasklens.kourier.KourierTelemetryBridge(
                    eventSink = { emit(it) }
                )
                bridge.start()
                kourierBridge = bridge
                config.logger.log(LogLevel.INFO, TAG, "KourierTelemetryBridge started.")
            } catch (t: Throwable) {
                kourierHealthy = false
                config.logger.log(LogLevel.WARN, TAG, "Kourier bridge failed to start: ${t.message}")
            }
        }

        // Automatically start WorkManager observer if enabled
        if (config.autoObserveWorkManager) {
            try {
                val adapter = dev.shushant.tasklens.workmanager.DefaultWorkManagerAdapter(
                    context = applicationContext,
                    scope = scope,
                    taskSink = { storeInstance.saveTask(it) },
                    eventSink = { emit(it) }
                )
                adapter.start()
                workManagerAdapter = adapter
                config.logger.log(LogLevel.INFO, TAG, "Auto-started WorkManager observer.")
            } catch (t: Throwable) {
                config.logger.log(LogLevel.WARN, TAG, "WorkManagerAdapter could not be started: ${t.message}")
            }
        }

        config.logger.log(LogLevel.INFO, TAG, "TaskLens installed successfully.")
    }

    /** Builder-DSL variant — preferred for clean, idiomatic configuration. */
    fun install(
        application: Application,
        block: TaskLensConfig.Builder.() -> Unit
    ) {
        install(application, TaskLensConfig.Builder().apply(block).build())
    }

    private fun startCollectors(application: Application) {
        try {
            val network = NetworkMonitor(applicationContext, scope)
            network.start { emit(it) }
            activeCollectors.add(network)

            val battery = BatteryMonitor(applicationContext)
            battery.start { emit(it) }
            activeCollectors.add(battery)

            val power = PowerManagerMonitor(applicationContext)
            power.start { emit(it) }
            activeCollectors.add(power)

            val appState = AppStateMonitor(application)
            appState.start { emit(it) }
            activeCollectors.add(appState)
        } catch (t: Throwable) {
            currentConfig.logger.log(LogLevel.ERROR, TAG, "Failed to initialize some environment collectors", t)
        }
    }

    fun emit(event: TaskLensEvent) {
        if (!isInstalled.get()) return

        // Attach correlation context if active
        val token = currentCorrelationToken.get()
        val finalEvent = if (token != null && !event.attributes.containsKey("correlation_id")) {
            event.copy(attributes = event.attributes + ("correlation_id" to token))
        } else {
            event
        }

        eventChannel.trySend(finalEvent)
    }

    private suspend fun processEvents() {
        for (event in eventChannel) {
            try {
                // Redact sensitive attributes
                val redactedAttributes = event.attributes.mapValues { (k, v) ->
                    currentConfig.redactor.redact(k, v)
                }
                val sanitizedEvent = event.copy(attributes = redactedAttributes)

                storeInstance.append(sanitizedEvent)

                // Reconstruct attempts and run diagnosis only on terminal/significant events
                // to avoid O(n²) work on every intermediate event.
                val taskId = sanitizedEvent.taskId
                if (taskId != null && sanitizedEvent.type in DIAGNOSIS_TRIGGER_TYPES) {
                    val taskEvents = storeInstance.events(taskId)
                    val attempts = correlationEngine.reconstructAttempts(taskId, taskEvents)
                    for (attempt in attempts) {
                        storeInstance.saveAttempt(attempt)
                    }
                }
            } catch (t: Throwable) {
                currentConfig.logger.log(LogLevel.ERROR, TAG, "Error writing event to persistent store", t)
            }
        }
    }

    fun breadcrumb(
        title: String,
        message: String,
        taskId: String? = null,
        attributes: Map<String, String> = emptyMap()
    ) {
        emit(
            TaskLensEvent(
                taskId = taskId,
                type = EventType.CUSTOM_BREADCRUMB,
                source = EventSource.APPLICATION,
                attributes = attributes + mapOf("title" to title, "message" to message)
            )
        )
    }

    suspend fun <T> withCorrelation(
        correlationId: String,
        block: suspend () -> T
    ): T {
        val previous = currentCorrelationToken.get()
        currentCorrelationToken.set(correlationId)
        return try {
            block()
        } finally {
            currentCorrelationToken.set(previous)
        }
    }

    fun show(context: Context? = null) {
        val ctx = context ?: if (isInstalled.get()) applicationContext else return
        try {
            val intent = Intent().apply {
                setClassName(ctx.packageName, "dev.shushant.tasklens.ui.TaskLensActivity")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(intent)
        } catch (t: Throwable) {
            currentConfig.logger.log(LogLevel.WARN, TAG, "Could not open TaskLensActivity: ${t.message}")
        }
    }

    /** Programmatically opens the TaskLens on-device inspector UI. */
    fun showUI(context: Context? = null) = show(context)

    /** Programmatically closes the TaskLens inspector UI. */
    fun hideUI() = hide()

    fun hide() = Unit

    fun clear() {
        scope.launch {
            try {
                storeInstance.clear()
            } catch (t: Throwable) {
                currentConfig.logger.log(LogLevel.ERROR, TAG, "Failed to clear store", t)
            }
        }
    }

    suspend fun export(taskId: String, destinationFile: File? = null): File {
        val exporter = DefaultTaskLensExporter(storeInstance)
        val target = destinationFile ?: File(
            applicationContext.cacheDir,
            "tasklens_${taskId}_${System.currentTimeMillis()}.tasklens"
        )
        val deviceMeta = mapOf(
            "model" to Build.MODEL,
            "manufacturer" to Build.MANUFACTURER,
            "android_version" to Build.VERSION.RELEASE,
            "sdk_int" to Build.VERSION.SDK_INT.toString(),
            "brand" to Build.BRAND
        )
        return exporter.export(
            taskId = taskId,
            outputFile = target,
            includePayloads = currentConfig.captureWorkerPayloads,
            deviceMetadata = deviceMeta,
            platform = "android",
            redactor = currentConfig.redactor
        )
    }

    /**
     * Returns the current health state of the SDK.
     *
     * Returns [HealthState.DISABLED] if the SDK has not been installed yet.
     * Sub-system degradations are checked in priority order: storage > kourier > export.
     */
    fun health(): HealthState {
        if (!isInstalled.get()) return HealthState.DISABLED
        return when {
            !storageHealthy -> HealthState.DEGRADED_STORAGE
            !kourierHealthy -> HealthState.DEGRADED_KOURIER
            !exportHealthy  -> HealthState.DEGRADED_EXPORT
            else            -> HealthState.READY
        }
    }

    fun getStore(): TaskLensStore {
        check(isInstalled.get()) { "TaskLens is not installed. Call TaskLens.install(application) first." }
        return storeInstance
    }

    fun getConfig(): TaskLensConfig = currentConfig
}
