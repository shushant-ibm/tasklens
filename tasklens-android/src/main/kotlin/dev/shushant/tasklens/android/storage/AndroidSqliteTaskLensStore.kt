package dev.shushant.tasklens.android.storage

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import dev.shushant.tasklens.core.AttemptOutcome
import dev.shushant.tasklens.core.Diagnosis
import dev.shushant.tasklens.core.EventType
import dev.shushant.tasklens.core.ExecutionAttempt
import dev.shushant.tasklens.core.ScheduledWork
import dev.shushant.tasklens.core.TaskLensEvent
import dev.shushant.tasklens.storage.RetentionPolicy
import dev.shushant.tasklens.storage.TaskLensStore
import dev.shushant.tasklens.storage.TaskQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

class TaskLensDbHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "tasklens.db"
        const val DATABASE_VERSION = 2

        const val TABLE_TASKS = "tasks"
        const val TABLE_ATTEMPTS = "attempts"
        const val TABLE_EVENTS = "events"
        const val TABLE_DIAGNOSES = "diagnoses"

        const val COL_TASK_ID = "task_id"
        const val COL_ATTEMPT_ID = "attempt_id"
        const val COL_EVENT_ID = "event_id"
        const val COL_TIMESTAMP = "timestamp"
        const val COL_DATA_JSON = "data_json"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_TASKS (
                $COL_TASK_ID TEXT PRIMARY KEY,
                name TEXT,
                scheduler TEXT,
                submitted_at INTEGER,
                $COL_DATA_JSON TEXT NOT NULL
            );
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_tasks_submitted_at ON $TABLE_TASKS (submitted_at);")

        db.execSQL(
            """
            CREATE TABLE $TABLE_ATTEMPTS (
                $COL_ATTEMPT_ID TEXT PRIMARY KEY,
                $COL_TASK_ID TEXT NOT NULL,
                attempt_number INTEGER NOT NULL,
                started_at INTEGER,
                ended_at INTEGER,
                outcome TEXT,
                $COL_DATA_JSON TEXT NOT NULL
            );
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_attempts_task_id ON $TABLE_ATTEMPTS ($COL_TASK_ID, attempt_number);")

        db.execSQL(
            """
            CREATE TABLE $TABLE_EVENTS (
                $COL_EVENT_ID TEXT PRIMARY KEY,
                $COL_TASK_ID TEXT,
                $COL_ATTEMPT_ID TEXT,
                $COL_TIMESTAMP INTEGER NOT NULL,
                event_type TEXT NOT NULL,
                source TEXT NOT NULL,
                sequence_number INTEGER NOT NULL,
                $COL_DATA_JSON TEXT NOT NULL
            );
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_events_task_ts ON $TABLE_EVENTS ($COL_TASK_ID, $COL_TIMESTAMP);")
        db.execSQL("CREATE INDEX idx_events_attempt_ts ON $TABLE_EVENTS ($COL_ATTEMPT_ID, $COL_TIMESTAMP);")

        db.execSQL(
            """
            CREATE TABLE $TABLE_DIAGNOSES (
                diagnosis_id TEXT PRIMARY KEY,
                $COL_TASK_ID TEXT NOT NULL,
                $COL_ATTEMPT_ID TEXT,
                classification TEXT NOT NULL,
                confidence TEXT NOT NULL,
                $COL_DATA_JSON TEXT NOT NULL
            );
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_diagnoses_task_id ON $TABLE_DIAGNOSES ($COL_TASK_ID);")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS metadata (
                key TEXT PRIMARY KEY,
                value TEXT NOT NULL,
                updated_at INTEGER NOT NULL
            );
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_events_timestamp ON $TABLE_EVENTS ($COL_TIMESTAMP);")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Non-destructive migration from version 1 to 2
        if (oldVersion < 2) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS metadata (
                    key TEXT PRIMARY KEY,
                    value TEXT NOT NULL,
                    updated_at INTEGER NOT NULL
                );
                """.trimIndent()
            )
            if (!indexExists(db, "idx_events_timestamp")) {
                db.execSQL("CREATE INDEX idx_events_timestamp ON $TABLE_EVENTS ($COL_TIMESTAMP);")
            }
        }
    }

    private fun indexExists(db: SQLiteDatabase, indexName: String): Boolean {
        val cursor = db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='index' AND name=?",
            arrayOf(indexName)
        )
        return cursor.use { it.moveToFirst() }
    }
}

class AndroidSqliteTaskLensStore(
    context: Context
) : TaskLensStore {

    private val dbHelper = TaskLensDbHelper(context.applicationContext)
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    }

    override suspend fun append(event: TaskLensEvent): Unit = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(TaskLensDbHelper.COL_EVENT_ID, event.id)
            put(TaskLensDbHelper.COL_TASK_ID, event.taskId)
            put(TaskLensDbHelper.COL_ATTEMPT_ID, event.attemptId)
            put(TaskLensDbHelper.COL_TIMESTAMP, event.timestamp.toEpochMilliseconds())
            put("event_type", event.type.name)
            put("source", event.source.name)
            put("sequence_number", event.sequenceNumber)
            put(TaskLensDbHelper.COL_DATA_JSON, json.encodeToString(event))
        }
        db.insertWithOnConflict(TaskLensDbHelper.TABLE_EVENTS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    override suspend fun saveTask(task: ScheduledWork): Unit = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(TaskLensDbHelper.COL_TASK_ID, task.id)
            put("name", task.name)
            put("scheduler", task.scheduler.name)
            put("submitted_at", task.submittedAt?.toEpochMilliseconds() ?: 0L)
            put(TaskLensDbHelper.COL_DATA_JSON, json.encodeToString(task))
        }
        db.insertWithOnConflict(TaskLensDbHelper.TABLE_TASKS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    override suspend fun saveAttempt(attempt: ExecutionAttempt): Unit = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(TaskLensDbHelper.COL_ATTEMPT_ID, attempt.attemptId)
            put(TaskLensDbHelper.COL_TASK_ID, attempt.taskId)
            put("attempt_number", attempt.attemptNumber)
            put("started_at", attempt.startedAt?.toEpochMilliseconds())
            put("ended_at", attempt.endedAt?.toEpochMilliseconds())
            put("outcome", attempt.outcome?.name)
            put(TaskLensDbHelper.COL_DATA_JSON, json.encodeToString(attempt))
        }
        db.insertWithOnConflict(TaskLensDbHelper.TABLE_ATTEMPTS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    override suspend fun saveDiagnosis(diagnosis: Diagnosis): Unit = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("diagnosis_id", diagnosis.id)
            put(TaskLensDbHelper.COL_TASK_ID, diagnosis.taskId)
            put(TaskLensDbHelper.COL_ATTEMPT_ID, diagnosis.attemptId)
            put("classification", diagnosis.classification.name)
            put("confidence", diagnosis.confidence.name)
            put(TaskLensDbHelper.COL_DATA_JSON, json.encodeToString(diagnosis))
        }
        db.insertWithOnConflict(TaskLensDbHelper.TABLE_DIAGNOSES, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    override suspend fun tasks(query: TaskQuery): List<ScheduledWork> = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        val whereClauses = mutableListOf<String>()
        val args = mutableListOf<String>()

        val scheduler = query.scheduler
        if (scheduler != null) {
            whereClauses.add("scheduler = ?")
            args.add(scheduler.name)
        }

        if (!query.searchQuery.isNullOrBlank()) {
            whereClauses.add("(name LIKE ? OR task_id LIKE ?)")
            args.add("%${query.searchQuery}%")
            args.add("%${query.searchQuery}%")
        }

        val selection = if (whereClauses.isEmpty()) null else whereClauses.joinToString(" AND ")
        val selectionArgs = if (args.isEmpty()) null else args.toTypedArray()

        val cursor = db.query(
            TaskLensDbHelper.TABLE_TASKS,
            arrayOf(TaskLensDbHelper.COL_DATA_JSON),
            selection,
            selectionArgs,
            null,
            null,
            "submitted_at DESC",
            "${query.offset}, ${query.limit}"
        )

        val result = mutableListOf<ScheduledWork>()
        cursor.use {
            while (it.moveToNext()) {
                val jsonStr = it.getString(0)
                try {
                    result.add(json.decodeFromString<ScheduledWork>(jsonStr))
                } catch (t: Throwable) {
                    // Skip corrupt entry
                }
            }
        }
        result
    }

    override suspend fun task(taskId: String): ScheduledWork? = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            TaskLensDbHelper.TABLE_TASKS,
            arrayOf(TaskLensDbHelper.COL_DATA_JSON),
            "${TaskLensDbHelper.COL_TASK_ID} = ?",
            arrayOf(taskId),
            null,
            null,
            null
        )
        cursor.use {
            if (it.moveToFirst()) {
                try {
                    json.decodeFromString<ScheduledWork>(it.getString(0))
                } catch (t: Throwable) {
                    null
                }
            } else null
        }
    }

    override suspend fun attempts(taskId: String): List<ExecutionAttempt> = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            TaskLensDbHelper.TABLE_ATTEMPTS,
            arrayOf(TaskLensDbHelper.COL_DATA_JSON),
            "${TaskLensDbHelper.COL_TASK_ID} = ?",
            arrayOf(taskId),
            null,
            null,
            "attempt_number ASC"
        )
        val result = mutableListOf<ExecutionAttempt>()
        cursor.use {
            while (it.moveToNext()) {
                try {
                    result.add(json.decodeFromString<ExecutionAttempt>(it.getString(0)))
                } catch (t: Throwable) {
                    // Skip corrupt entry
                }
            }
        }
        result
    }

    override suspend fun events(taskId: String): List<TaskLensEvent> = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            TaskLensDbHelper.TABLE_EVENTS,
            arrayOf(TaskLensDbHelper.COL_DATA_JSON),
            "${TaskLensDbHelper.COL_TASK_ID} = ?",
            arrayOf(taskId),
            null,
            null,
            "${TaskLensDbHelper.COL_TIMESTAMP} ASC, sequence_number ASC"
        )
        val result = mutableListOf<TaskLensEvent>()
        cursor.use {
            while (it.moveToNext()) {
                try {
                    result.add(json.decodeFromString<TaskLensEvent>(it.getString(0)))
                } catch (t: Throwable) {
                    // Skip corrupt entry
                }
            }
        }
        result
    }

    override suspend fun diagnoses(taskId: String): List<Diagnosis> = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            TaskLensDbHelper.TABLE_DIAGNOSES,
            arrayOf(TaskLensDbHelper.COL_DATA_JSON),
            "${TaskLensDbHelper.COL_TASK_ID} = ?",
            arrayOf(taskId),
            null,
            null,
            null
        )
        val result = mutableListOf<Diagnosis>()
        cursor.use {
            while (it.moveToNext()) {
                try {
                    result.add(json.decodeFromString<Diagnosis>(it.getString(0)))
                } catch (t: Throwable) {
                    // Skip corrupt entry
                }
            }
        }
        result
    }

    override suspend fun environmentEvents(limit: Int): List<TaskLensEvent> = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            TaskLensDbHelper.TABLE_EVENTS,
            arrayOf(TaskLensDbHelper.COL_DATA_JSON),
            "event_type IN (?, ?, ?, ?, ?)",
            arrayOf(
                EventType.NETWORK_CHANGED.name,
                EventType.BATTERY_CHANGED.name,
                EventType.POWER_MODE_CHANGED.name,
                EventType.APP_STATE_CHANGED.name,
                EventType.PROCESS_STATE_CHANGED.name
            ),
            null,
            null,
            "${TaskLensDbHelper.COL_TIMESTAMP} DESC",
            limit.toString()
        )
        val result = mutableListOf<TaskLensEvent>()
        cursor.use {
            while (it.moveToNext()) {
                try {
                    result.add(json.decodeFromString<TaskLensEvent>(it.getString(0)))
                } catch (t: Throwable) {
                    // Skip corrupt entry
                }
            }
        }
        result.reversed()
    }

    override suspend fun delete(taskId: String): Unit = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.delete(TaskLensDbHelper.TABLE_TASKS, "${TaskLensDbHelper.COL_TASK_ID} = ?", arrayOf(taskId))
        db.delete(TaskLensDbHelper.TABLE_ATTEMPTS, "${TaskLensDbHelper.COL_TASK_ID} = ?", arrayOf(taskId))
        db.delete(TaskLensDbHelper.TABLE_EVENTS, "${TaskLensDbHelper.COL_TASK_ID} = ?", arrayOf(taskId))
        db.delete(TaskLensDbHelper.TABLE_DIAGNOSES, "${TaskLensDbHelper.COL_TASK_ID} = ?", arrayOf(taskId))
    }

    override suspend fun clear(): Unit = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.delete(TaskLensDbHelper.TABLE_TASKS, null, null)
        db.delete(TaskLensDbHelper.TABLE_ATTEMPTS, null, null)
        db.delete(TaskLensDbHelper.TABLE_EVENTS, null, null)
        db.delete(TaskLensDbHelper.TABLE_DIAGNOSES, null, null)
    }

    override suspend fun applyRetention(policy: RetentionPolicy): Unit = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val now = Clock.System.now().toEpochMilliseconds()

        when (policy) {
            is RetentionPolicy.Forever -> Unit

            is RetentionPolicy.LastDays -> {
                val cutoff = now - policy.days.days.inWholeMilliseconds
                db.delete(TaskLensDbHelper.TABLE_TASKS, "submitted_at < ?", arrayOf(cutoff.toString()))
                db.delete(TaskLensDbHelper.TABLE_EVENTS, "${TaskLensDbHelper.COL_TIMESTAMP} < ?", arrayOf(cutoff.toString()))
            }

            is RetentionPolicy.MaxTasks -> {
                db.execSQL(
                    """
                    DELETE FROM ${TaskLensDbHelper.TABLE_TASKS} WHERE ${TaskLensDbHelper.COL_TASK_ID} NOT IN (
                        SELECT ${TaskLensDbHelper.COL_TASK_ID} FROM ${TaskLensDbHelper.TABLE_TASKS}
                        ORDER BY submitted_at DESC LIMIT ${policy.maxCount}
                    )
                    """.trimIndent()
                )
            }

            is RetentionPolicy.Combined -> {
                val cutoff = now - policy.days.days.inWholeMilliseconds
                db.delete(TaskLensDbHelper.TABLE_TASKS, "submitted_at < ?", arrayOf(cutoff.toString()))
                db.delete(TaskLensDbHelper.TABLE_EVENTS, "${TaskLensDbHelper.COL_TIMESTAMP} < ?", arrayOf(cutoff.toString()))

                db.execSQL(
                    """
                    DELETE FROM ${TaskLensDbHelper.TABLE_TASKS} WHERE ${TaskLensDbHelper.COL_TASK_ID} NOT IN (
                        SELECT ${TaskLensDbHelper.COL_TASK_ID} FROM ${TaskLensDbHelper.TABLE_TASKS}
                        ORDER BY submitted_at DESC LIMIT ${policy.maxTasks}
                    )
                    """.trimIndent()
                )
            }
        }
    }
}
