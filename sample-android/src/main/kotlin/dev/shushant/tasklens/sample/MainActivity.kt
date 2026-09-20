package dev.shushant.tasklens.sample

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import android.util.Log
import androidx.lifecycle.lifecycleScope
import dev.shushant.tasklens.android.TaskLens
import dev.shushant.tasklens.core.AttemptOutcome
import dev.shushant.tasklens.core.DefaultCorrelationEngine
import dev.shushant.tasklens.core.EventSeverity
import dev.shushant.tasklens.core.EventSource
import dev.shushant.tasklens.core.EventType
import dev.shushant.tasklens.core.ExecutionAttempt
import dev.shushant.tasklens.core.ScheduledWork
import dev.shushant.tasklens.core.SchedulerType
import dev.shushant.tasklens.core.TaskLensEvent
import dev.shushant.tasklens.core.TaskType
import dev.shushant.tasklens.diagnosis.DefaultDiagnosisEngine
import dev.shushant.tasklens.diagnosis.DiagnosisContext
import dev.shushant.tasklens.sample.workers.FatalFailureWorker
import dev.shushant.tasklens.sample.workers.FlakyRetryWorker
import dev.shushant.tasklens.sample.workers.LongRunningWorker
import dev.shushant.tasklens.sample.workers.NetworkRequiredWorker
import dev.shushant.tasklens.sample.workers.SyncWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.system.measureNanoTime
import kotlin.system.measureTimeMillis

class MainActivity : ComponentActivity() {

    companion object {
        var lastEnqueuedTaskId: String? = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    TaskLensLabScreen()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val action = intent?.getStringExtra("action") ?: return
        Log.i("TaskLensHarness", "Handling action: $action")
        val wm = WorkManager.getInstance(this)

        when (action) {
            "enqueue_success" -> {
                val req = OneTimeWorkRequestBuilder<SyncWorker>().build()
                lastEnqueuedTaskId = req.id.toString()
                wm.enqueue(req)
                Log.i("TaskLensHarness", "Enqueued SyncWorker: ${req.id}")
            }
            "enqueue_wifi" -> {
                val req = OneTimeWorkRequestBuilder<NetworkRequiredWorker>()
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build())
                    .build()
                lastEnqueuedTaskId = req.id.toString()
                wm.enqueue(req)
                Log.i("TaskLensHarness", "Enqueued NetworkRequiredWorker: ${req.id}")
            }
            "enqueue_charging" -> {
                val req = OneTimeWorkRequestBuilder<SyncWorker>()
                    .setConstraints(Constraints.Builder().setRequiresCharging(true).build())
                    .build()
                lastEnqueuedTaskId = req.id.toString()
                wm.enqueue(req)
                Log.i("TaskLensHarness", "Enqueued Charging Worker: ${req.id}")
            }
            "enqueue_flaky" -> {
                val req = OneTimeWorkRequestBuilder<FlakyRetryWorker>()
                    .setBackoffCriteria(BackoffPolicy.LINEAR, 5, TimeUnit.SECONDS)
                    .build()
                lastEnqueuedTaskId = req.id.toString()
                wm.enqueue(req)
                Log.i("TaskLensHarness", "Enqueued FlakyRetryWorker: ${req.id}")
            }
            "enqueue_fatal" -> {
                val req = OneTimeWorkRequestBuilder<FatalFailureWorker>().build()
                lastEnqueuedTaskId = req.id.toString()
                wm.enqueue(req)
                Log.i("TaskLensHarness", "Enqueued FatalFailureWorker: ${req.id}")
            }
            "enqueue_long" -> {
                val req = OneTimeWorkRequestBuilder<LongRunningWorker>()
                    .addTag("cancellable_group")
                    .build()
                lastEnqueuedTaskId = req.id.toString()
                wm.enqueue(req)
                Log.i("TaskLensHarness", "Enqueued LongRunningWorker: ${req.id}")
            }
            "cancel_long" -> {
                wm.cancelAllWorkByTag("cancellable_group")
                Log.i("TaskLensHarness", "Cancelled LongRunningWorker")
            }
            "export" -> {
                val targetFile = File(getExternalFilesDir(null), "android_pixel7pro_sample.tasklens")
                lifecycleScope.launch {
                    val taskId = intent.getStringExtra("task_id") ?: lastEnqueuedTaskId ?: "48d6cf1a-7f30-4f9c-a7cc-8d794651c223"
                    try {
                        TaskLens.export(taskId, targetFile)
                        Log.i("TaskLensExport", "EXPORT_SUCCESS: ${targetFile.absolutePath} size=${targetFile.length()}")
                    } catch (t: Throwable) {
                        Log.e("TaskLensExport", "EXPORT_FAILED: ${t.message}", t)
                    }
                }
            }
            "benchmark" -> {
                runPhysicalBenchmarks()
            }
        }
    }

    private fun runPhysicalBenchmarks() {
        lifecycleScope.launch(Dispatchers.Default) {
            Log.i("TaskLensBenchmark", "=== STARTING TASKLENS PHYSICAL BENCHMARKS ===")

            // 1. Idle Memory
            System.gc()
            val rt = Runtime.getRuntime()
            val idleMemoryMb = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024)
            Log.i("TaskLensBenchmark", "METRIC: idle_memory_mb=$idleMemoryMb")

            // 2. Event Ingestion Latency (1,000 emits)
            val count = 1000
            val latenciesMicros = LongArray(count)
            for (i in 0 until 50) {
                TaskLens.emit(
                    TaskLensEvent(
                        id = "warmup-$i",
                        taskId = "bench-task",
                        type = EventType.CUSTOM_BREADCRUMB,
                        source = EventSource.APPLICATION
                    )
                )
            }
            for (i in 0 until count) {
                val nanos = measureNanoTime {
                    TaskLens.emit(
                        TaskLensEvent(
                            id = "bench-evt-$i",
                            taskId = "bench-task",
                            type = EventType.CUSTOM_BREADCRUMB,
                            source = EventSource.APPLICATION,
                            attributes = mapOf("iteration" to i.toString())
                        )
                    )
                }
                latenciesMicros[i] = nanos / 1000L
            }
            latenciesMicros.sort()
            val p50 = latenciesMicros[(count * 0.50).toInt()]
            val p95 = latenciesMicros[(count * 0.95).toInt()]
            val p99 = latenciesMicros[(count * 0.99).toInt()]
            Log.i("TaskLensBenchmark", "METRIC: ingestion_p50_us=$p50, ingestion_p95_us=$p95, ingestion_p99_us=$p99")

            // 3. Timeline Reconstruction (1k, 10k, 100k)
            val correlation = DefaultCorrelationEngine()
            val baseTime = kotlin.time.Instant.fromEpochMilliseconds(1710000000000L)

            // 1k
            val events1k = (1..1000).map { i ->
                TaskLensEvent(
                    id = "evt-1k-$i",
                    taskId = "task-1k",
                    attemptId = "att-1k-1",
                    timestamp = kotlin.time.Instant.fromEpochMilliseconds(baseTime.toEpochMilliseconds() + i * 10L),
                    sequenceNumber = i.toLong(),
                    type = if (i == 1) EventType.TASK_STARTED else if (i == 1000) EventType.TASK_SUCCEEDED else EventType.CUSTOM_BREADCRUMB,
                    source = EventSource.WORK_MANAGER
                )
            }
            correlation.reconstructAttempts("task-1k", events1k) // warm up
            val time1k = measureTimeMillis {
                correlation.reconstructAttempts("task-1k", events1k)
            }
            Log.i("TaskLensBenchmark", "METRIC: timeline_reconstruction_1k_ms=$time1k")

            // 10k
            val events10k = (1..10000).map { i ->
                TaskLensEvent(
                    id = "evt-10k-$i",
                    taskId = "task-10k",
                    attemptId = "att-10k-1",
                    timestamp = kotlin.time.Instant.fromEpochMilliseconds(baseTime.toEpochMilliseconds() + i * 5L),
                    sequenceNumber = i.toLong(),
                    type = if (i == 1) EventType.TASK_STARTED else if (i == 10000) EventType.TASK_SUCCEEDED else EventType.CUSTOM_BREADCRUMB,
                    source = EventSource.WORK_MANAGER
                )
            }
            val time10k = measureTimeMillis {
                correlation.reconstructAttempts("task-10k", events10k)
            }
            Log.i("TaskLensBenchmark", "METRIC: timeline_reconstruction_10k_ms=$time10k")

            // 100k
            val events100k = (1..100000).map { i ->
                TaskLensEvent(
                    id = "evt-100k-$i",
                    taskId = "task-100k",
                    attemptId = "att-100k-1",
                    timestamp = kotlin.time.Instant.fromEpochMilliseconds(baseTime.toEpochMilliseconds() + i * 1L),
                    sequenceNumber = i.toLong(),
                    type = if (i == 1) EventType.TASK_STARTED else if (i == 100000) EventType.TASK_SUCCEEDED else EventType.CUSTOM_BREADCRUMB,
                    source = EventSource.WORK_MANAGER
                )
            }
            val time100k = measureTimeMillis {
                correlation.reconstructAttempts("task-100k", events100k)
            }
            Log.i("TaskLensBenchmark", "METRIC: timeline_reconstruction_100k_ms=$time100k")

            // 4. Diagnosis Engine Execution (1k, 10k)
            val diagnosisEngine = DefaultDiagnosisEngine()
            val dummyTask = ScheduledWork(
                id = "diag-task",
                name = "DiagBenchmarkWorker",
                type = TaskType.WORKER,
                scheduler = SchedulerType.WORK_MANAGER
            )
            val dummyAttempt = ExecutionAttempt(
                attemptId = "att-1",
                taskId = dummyTask.id,
                attemptNumber = 1,
                outcome = AttemptOutcome.FAILED
            )
            val diagContext1k = DiagnosisContext(dummyTask, listOf(dummyAttempt), events1k)
            diagnosisEngine.diagnose(diagContext1k) // warm up
            val diagTime1k = measureTimeMillis {
                diagnosisEngine.diagnose(diagContext1k)
            }
            Log.i("TaskLensBenchmark", "METRIC: diagnosis_execution_1k_ms=$diagTime1k")

            val diagContext10k = DiagnosisContext(dummyTask, listOf(dummyAttempt), events10k)
            val diagTime10k = measureTimeMillis {
                diagnosisEngine.diagnose(diagContext10k)
            }
            Log.i("TaskLensBenchmark", "METRIC: diagnosis_execution_10k_ms=$diagTime10k")

            // 5. Export Latency
            val targetFile = File(getExternalFilesDir(null), "android_pixel7pro_sample.tasklens")
            val exportTimeMs = measureTimeMillis {
                try {
                    val taskId = lastEnqueuedTaskId ?: "bench-task"
                    TaskLens.export(taskId, targetFile)
                } catch (t: Throwable) {
                    Log.w("TaskLensBenchmark", "Export threw: ${t.message}")
                }
            }
            Log.i("TaskLensBenchmark", "METRIC: export_latency_ms=$exportTimeMs, export_bytes=${targetFile.length()}")

            // 6. UI Memory
            val uiMemoryMb = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024)
            Log.i("TaskLensBenchmark", "METRIC: ui_active_memory_mb=$uiMemoryMb")

            Log.i("TaskLensBenchmark", "=== TASKLENS PHYSICAL BENCHMARKS COMPLETE ===")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskLensLabScreen() {
    val context = LocalContext.current
    val workManager = remember { WorkManager.getInstance(context) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "TaskLens Lab",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Background Task Diagnosis Playground",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    Button(
                        onClick = {
                            TaskLens.showUI(context)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Open Inspector")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Hero Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Why didn't my background task run?",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "TaskLens continuously correlates WorkManager lifecycle events with OS state (Doze, Network, Battery, Thermal). Use these triggers below to simulate background task scenarios, then inspect them with zero guess work.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                    )
                }
            }

            Text(
                text = "Simulate Task Scenarios",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            // 1. Success Worker
            ScenarioCard(
                title = "Standard Success Sync",
                subtitle = "Executes standard worker without constraints.",
                icon = Icons.Default.CheckCircle,
                accentColor = Color(0xFF2E7D32),
                buttonLabel = "Enqueue Success"
            ) {
                val req = OneTimeWorkRequestBuilder<SyncWorker>().build()
                workManager.enqueue(req)
                scope.launch {
                    snackbarHostState.showSnackbar("Enqueued SyncWorker [${req.id}]")
                }
            }

            // 2. Network Required Worker
            ScenarioCard(
                title = "Unmetered Wi-Fi Constraint",
                subtitle = "Will pause/hold if disconnected or on cellular data.",
                icon = Icons.Default.Wifi,
                accentColor = Color(0xFF1565C0),
                buttonLabel = "Enqueue Wi-Fi Worker"
            ) {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.UNMETERED)
                    .build()
                val req = OneTimeWorkRequestBuilder<NetworkRequiredWorker>()
                    .setConstraints(constraints)
                    .build()
                workManager.enqueue(req)
                scope.launch {
                    snackbarHostState.showSnackbar("Enqueued NetworkRequiredWorker [${req.id}]")
                }
            }

            // 3. Charging Required Worker
            ScenarioCard(
                title = "Charging Constraint Required",
                subtitle = "Requires external power. TaskLens flags constraint blockage.",
                icon = Icons.Default.BatteryAlert,
                accentColor = Color(0xFFE65100),
                buttonLabel = "Enqueue Charging Worker"
            ) {
                val constraints = Constraints.Builder()
                    .setRequiresCharging(true)
                    .build()
                val req = OneTimeWorkRequestBuilder<SyncWorker>()
                    .setConstraints(constraints)
                    .build()
                workManager.enqueue(req)
                scope.launch {
                    snackbarHostState.showSnackbar("Enqueued Charging Worker [${req.id}]")
                }
            }

            // 4. Flaky Retry Worker
            ScenarioCard(
                title = "Flaky Worker with Retries",
                subtitle = "Fails initially, triggers exponential backoff retries.",
                icon = Icons.Default.Replay,
                accentColor = Color(0xFF6A1B9A),
                buttonLabel = "Enqueue Flaky Worker"
            ) {
                val req = OneTimeWorkRequestBuilder<FlakyRetryWorker>()
                    .setBackoffCriteria(BackoffPolicy.LINEAR, 5, TimeUnit.SECONDS)
                    .build()
                workManager.enqueue(req)
                scope.launch {
                    snackbarHostState.showSnackbar("Enqueued FlakyRetryWorker [${req.id}]")
                }
            }

            // 5. Fatal Failure Worker
            ScenarioCard(
                title = "Fatal Task Failure",
                subtitle = "Returns Result.failure() with diagnostic payload.",
                icon = Icons.Default.Warning,
                accentColor = Color(0xFFC62828),
                buttonLabel = "Enqueue Fatal Worker"
            ) {
                val req = OneTimeWorkRequestBuilder<FatalFailureWorker>().build()
                workManager.enqueue(req)
                scope.launch {
                    snackbarHostState.showSnackbar("Enqueued FatalFailureWorker [${req.id}]")
                }
            }

            // 6. Long Running Worker (for Cancellation/Timeout)
            ScenarioCard(
                title = "Long Running Task",
                subtitle = "Runs a continuous loop. Cancel it to test stop reasons.",
                icon = Icons.Default.HourglassTop,
                accentColor = Color(0xFF00838F),
                buttonLabel = "Enqueue Long Running"
            ) {
                val req = OneTimeWorkRequestBuilder<LongRunningWorker>()
                    .addTag("cancellable_group")
                    .build()
                workManager.enqueue(req)
                scope.launch {
                    snackbarHostState.showSnackbar("Enqueued LongRunningWorker [${req.id}]")
                }
            }

            Spacer(Modifier.height(8.dp))

            // Management Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        workManager.cancelAllWorkByTag("cancellable_group")
                        scope.launch {
                            snackbarHostState.showSnackbar("Cancelled long running workers")
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Cancel Long Tasks")
                }

                Button(
                    onClick = {
                        TaskLens.showUI(context)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("View Diagnosis")
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun ScenarioCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    buttonLabel: String,
    onTrigger: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(36.dp)
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = onTrigger,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(buttonLabel, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
