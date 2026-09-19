package dev.shushant.tasklens.android.storage

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import dev.shushant.tasklens.core.AttemptOutcome
import dev.shushant.tasklens.core.Diagnosis
import dev.shushant.tasklens.core.DiagnosisClassification
import dev.shushant.tasklens.core.DiagnosisConfidence
import dev.shushant.tasklens.core.EventSource
import dev.shushant.tasklens.core.EventType
import dev.shushant.tasklens.core.ExecutionAttempt
import dev.shushant.tasklens.core.ScheduledWork
import dev.shushant.tasklens.core.SchedulerType
import dev.shushant.tasklens.core.TaskLensEvent
import dev.shushant.tasklens.core.TaskType
import dev.shushant.tasklens.storage.TaskQuery
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import kotlin.time.Instant

/**
 * Helper simulating a pre-existing Database at Version 1.
 */
private class V1TaskLensDbHelper(context: Context) : SQLiteOpenHelper(context, TaskLensDbHelper.DATABASE_NAME, null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE tasks (
                task_id TEXT PRIMARY KEY,
                name TEXT,
                scheduler TEXT,
                submitted_at INTEGER,
                data_json TEXT NOT NULL
            );
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_tasks_submitted_at ON tasks (submitted_at);")

        db.execSQL(
            """
            CREATE TABLE attempts (
                attempt_id TEXT PRIMARY KEY,
                task_id TEXT NOT NULL,
                attempt_number INTEGER NOT NULL,
                started_at INTEGER,
                ended_at INTEGER,
                outcome TEXT,
                data_json TEXT NOT NULL
            );
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_attempts_task_id ON attempts (task_id, attempt_number);")

        db.execSQL(
            """
            CREATE TABLE events (
                event_id TEXT PRIMARY KEY,
                task_id TEXT,
                attempt_id TEXT,
                timestamp INTEGER NOT NULL,
                event_type TEXT NOT NULL,
                source TEXT NOT NULL,
                sequence_number INTEGER NOT NULL,
                data_json TEXT NOT NULL
            );
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_events_task_ts ON events (task_id, timestamp);")
        db.execSQL("CREATE INDEX idx_events_attempt_ts ON events (attempt_id, timestamp);")

        db.execSQL(
            """
            CREATE TABLE diagnoses (
                diagnosis_id TEXT PRIMARY KEY,
                task_id TEXT NOT NULL,
                attempt_id TEXT,
                classification TEXT NOT NULL,
                confidence TEXT NOT NULL,
                data_json TEXT NOT NULL
            );
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_diagnoses_task_id ON diagnoses (task_id);")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // v1 does nothing
    }
}

@RunWith(RobolectricTestRunner::class)
class AndroidMigrationTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun testNonDestructiveSchemaUpgradeV1ToV2() = runTest {
        val context = RuntimeEnvironment.getApplication()
        context.deleteDatabase(TaskLensDbHelper.DATABASE_NAME)

        // 1. Create database with V1 schema and populate fixtures
        val v1Helper = V1TaskLensDbHelper(context)
        val v1Db = v1Helper.writableDatabase

        val task1 = ScheduledWork(
            id = "v1-task-1",
            name = "V1LegacyTask",
            type = TaskType.WORKER,
            scheduler = SchedulerType.WORK_MANAGER,
            submittedAt = Instant.fromEpochMilliseconds(1710000000000L)
        )
        val attempt1 = ExecutionAttempt(
            attemptId = "v1-attempt-1",
            taskId = "v1-task-1",
            attemptNumber = 1,
            startedAt = Instant.fromEpochMilliseconds(1710000000000L),
            endedAt = Instant.fromEpochMilliseconds(1710000010000L),
            outcome = AttemptOutcome.SUCCESS
        )
        val event1 = TaskLensEvent(
            id = "v1-event-1",
            taskId = "v1-task-1",
            attemptId = "v1-attempt-1",
            sequenceNumber = 1,
            type = EventType.TASK_STARTED,
            source = EventSource.WORK_MANAGER,
            timestamp = Instant.fromEpochMilliseconds(1710000000000L)
        )
        val event2 = TaskLensEvent(
            id = "v1-event-2",
            taskId = "v1-task-1",
            attemptId = "v1-attempt-1",
            sequenceNumber = 2,
            type = EventType.TASK_SUCCEEDED,
            source = EventSource.WORK_MANAGER,
            timestamp = Instant.fromEpochMilliseconds(1710000010000L)
        )
        val diagnosis1 = Diagnosis(
            id = "v1-diag-1",
            taskId = "v1-task-1",
            attemptId = "v1-attempt-1",
            ruleId = "SUCCESS_CONFIRMED",
            ruleVersion = "1.0",
            title = "Task completed successfully",
            summary = "Task completed without issues.",
            classification = DiagnosisClassification.UNKNOWN,
            confidence = DiagnosisConfidence.CONFIRMED
        )

        val taskValues = ContentValues().apply {
            put("task_id", task1.id)
            put("name", task1.name)
            put("scheduler", task1.scheduler.name)
            put("submitted_at", 1710000000000L)
            put("data_json", json.encodeToString(task1))
        }
        v1Db.insert("tasks", null, taskValues)

        val attemptValues = ContentValues().apply {
            put("attempt_id", attempt1.attemptId)
            put("task_id", attempt1.taskId)
            put("attempt_number", 1)
            put("started_at", 1710000000000L)
            put("ended_at", 1710000010000L)
            put("outcome", "SUCCESS")
            put("data_json", json.encodeToString(attempt1))
        }
        v1Db.insert("attempts", null, attemptValues)

        val evtValues1 = ContentValues().apply {
            put("event_id", event1.id)
            put("task_id", event1.taskId)
            put("attempt_id", event1.attemptId)
            put("timestamp", 1710000000000L)
            put("event_type", event1.type.name)
            put("source", event1.source.name)
            put("sequence_number", 1)
            put("data_json", json.encodeToString(event1))
        }
        v1Db.insert("events", null, evtValues1)

        val evtValues2 = ContentValues().apply {
            put("event_id", event2.id)
            put("task_id", event2.taskId)
            put("attempt_id", event2.attemptId)
            put("timestamp", 1710000010000L)
            put("event_type", event2.type.name)
            put("source", event2.source.name)
            put("sequence_number", 2)
            put("data_json", json.encodeToString(event2))
        }
        v1Db.insert("events", null, evtValues2)

        val diagValues = ContentValues().apply {
            put("diagnosis_id", diagnosis1.id)
            put("task_id", diagnosis1.taskId)
            put("attempt_id", diagnosis1.attemptId)
            put("classification", diagnosis1.classification.name)
            put("confidence", diagnosis1.confidence.name)
            put("data_json", json.encodeToString(diagnosis1))
        }
        v1Db.insert("diagnoses", null, diagValues)

        // Close V1 database connection
        v1Db.close()
        v1Helper.close()

        // 2. Open with TaskLensDbHelper (Version 2) to trigger migration
        val store = AndroidSqliteTaskLensStore(context)

        // 3. Verify all V1 data is preserved intact
        val tasks = store.tasks(TaskQuery())
        assertEquals(1, tasks.size)
        assertEquals("v1-task-1", tasks.first().id)
        assertEquals("V1LegacyTask", tasks.first().name)

        val attempts = store.attempts("v1-task-1")
        assertEquals(1, attempts.size)
        assertEquals("v1-attempt-1", attempts.first().attemptId)
        assertEquals(AttemptOutcome.SUCCESS, attempts.first().outcome)

        val events = store.events("v1-task-1")
        assertEquals(2, events.size)
        assertEquals(EventType.TASK_STARTED, events[0].type)
        assertEquals(EventType.TASK_SUCCEEDED, events[1].type)

        val diagnoses = store.diagnoses("v1-task-1")
        assertEquals(1, diagnoses.size)
        assertEquals("SUCCESS_CONFIRMED", diagnoses.first().ruleId)

        // 4. Verify new schema additions: metadata table and index
        val upgradedDb = TaskLensDbHelper(context).readableDatabase
        val cursor = upgradedDb.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='metadata'", null)
        assertTrue("metadata table should exist after migration to v2", cursor.use { it.moveToFirst() })

        val indexCursor = upgradedDb.rawQuery("SELECT name FROM sqlite_master WHERE type='index' AND name='idx_events_timestamp'", null)
        assertTrue("idx_events_timestamp index should exist after migration to v2", indexCursor.use { it.moveToFirst() })

        // 5. Verify writing new records to upgraded store works seamlessly
        val event3 = TaskLensEvent(
            id = "v2-event-3",
            taskId = "v1-task-1",
            sequenceNumber = 3,
            type = EventType.TASK_STOPPED,
            source = EventSource.WORK_MANAGER,
            timestamp = Instant.fromEpochMilliseconds(1710000020000L)
        )
        store.append(event3)

        val updatedEvents = store.events("v1-task-1")
        assertEquals(3, updatedEvents.size)
    }
}
