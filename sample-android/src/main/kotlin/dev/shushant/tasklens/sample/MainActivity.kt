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
import dev.shushant.tasklens.android.TaskLens
import dev.shushant.tasklens.sample.workers.FatalFailureWorker
import dev.shushant.tasklens.sample.workers.FlakyRetryWorker
import dev.shushant.tasklens.sample.workers.LongRunningWorker
import dev.shushant.tasklens.sample.workers.NetworkRequiredWorker
import dev.shushant.tasklens.sample.workers.SyncWorker
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
