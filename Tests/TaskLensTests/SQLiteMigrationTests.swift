import XCTest
import SQLite3
@testable import TaskLensCore
@testable import TaskLensStorage

final class SQLiteMigrationTests: XCTestCase {

    var tempDbUrl: URL!

    override func setUp() {
        super.setUp()
        let tempDir = FileManager.default.temporaryDirectory
        tempDbUrl = tempDir.appendingPathComponent("migration_test_\(UUID().uuidString).sqlite")
    }

    override func tearDown() {
        if let url = tempDbUrl {
            try? FileManager.default.removeItem(at: url)
        }
        super.tearDown()
    }

    func testNonDestructiveSchemaUpgradeV1ToV2() async throws {
        // Step 1: Create a Legacy V1 Database manually
        var db: OpaquePointer?
        let flags = SQLITE_OPEN_READWRITE | SQLITE_OPEN_CREATE | SQLITE_OPEN_FULLMUTEX
        XCTAssertEqual(sqlite3_open_v2(tempDbUrl.path, &db, flags, nil), SQLITE_OK)

        // Execute V1 DDL
        let v1Sql = """
            CREATE TABLE schema_migrations (
                version INTEGER PRIMARY KEY,
                applied_at REAL NOT NULL
            );
            CREATE TABLE tasks (
                id TEXT PRIMARY KEY,
                name TEXT,
                scheduler TEXT,
                submitted_at REAL,
                data_json TEXT NOT NULL
            );
            CREATE TABLE attempts (
                attempt_id TEXT PRIMARY KEY,
                task_id TEXT NOT NULL,
                attempt_number INTEGER NOT NULL,
                started_at REAL,
                ended_at REAL,
                outcome TEXT,
                data_json TEXT NOT NULL
            );
            CREATE TABLE events (
                id TEXT PRIMARY KEY,
                task_id TEXT,
                attempt_id TEXT,
                timestamp REAL NOT NULL,
                event_type TEXT NOT NULL,
                source TEXT NOT NULL,
                sequence_number INTEGER NOT NULL,
                data_json TEXT NOT NULL
            );
            CREATE TABLE diagnoses (
                id TEXT PRIMARY KEY,
                task_id TEXT NOT NULL,
                data_json TEXT NOT NULL
            );
            INSERT INTO schema_migrations (version, applied_at) VALUES (1, 1000.0);
        """
        XCTAssertEqual(sqlite3_exec(db, v1Sql, nil, nil, nil), SQLITE_OK)

        // Step 2: Insert V1 Data
        let v1Task = ScheduledWork(
            id: "v1-task-1",
            name: "LegacyTask",
            type: .bgProcessing,
            scheduler: .bgTaskScheduler,
            submittedAt: Date(timeIntervalSince1970: 1000.0)
        )
        let v1TaskJson = String(data: try JSONEncoder().encode(v1Task), encoding: .utf8)!
        let insertTaskSql = "INSERT INTO tasks (id, name, scheduler, submitted_at, data_json) VALUES ('v1-task-1', 'LegacyTask', 'BG_TASK_SCHEDULER', 1000.0, '\(v1TaskJson)');"
        XCTAssertEqual(sqlite3_exec(db, insertTaskSql, nil, nil, nil), SQLITE_OK)

        let v1Event = TaskLensEvent(
            id: "v1-ev-1",
            taskId: "v1-task-1",
            timestamp: Date(timeIntervalSince1970: 1000.0),
            sequenceNumber: 1,
            type: .taskSubmitted,
            source: .bgTaskScheduler
        )
        let v1EventJson = String(data: try JSONEncoder().encode(v1Event), encoding: .utf8)!
        let insertEventSql = "INSERT INTO events (id, task_id, attempt_id, timestamp, event_type, source, sequence_number, data_json) VALUES ('v1-ev-1', 'v1-task-1', NULL, 1000.0, 'TASK_SUBMITTED', 'BG_TASK_SCHEDULER', 1, '\(v1EventJson)');"
        XCTAssertEqual(sqlite3_exec(db, insertEventSql, nil, nil, nil), SQLITE_OK)

        sqlite3_close(db)

        // Step 3: Open database with SQLiteTaskLensStore — triggers automatic migration to V2
        let store = SQLiteTaskLensStore(path: tempDbUrl.path)

        // Step 4: Verify data survived the upgrade
        let loadedTask = await store.task(id: "v1-task-1")
        XCTAssertNotNil(loadedTask, "Task from V1 database must survive migration to V2")
        XCTAssertEqual(loadedTask?.id, "v1-task-1")
        XCTAssertEqual(loadedTask?.name, "LegacyTask")

        let loadedEvents = await store.events(taskId: "v1-task-1")
        XCTAssertEqual(loadedEvents.count, 1, "Events from V1 database must survive migration to V2")
        XCTAssertEqual(loadedEvents.first?.id, "v1-ev-1")

        // Step 5: Verify V2 schema additions exist (check metadata table)
        var verifyDb: OpaquePointer?
        XCTAssertEqual(sqlite3_open_v2(tempDbUrl.path, &verifyDb, SQLITE_OPEN_READONLY, nil), SQLITE_OK)

        var stmt: OpaquePointer?
        let checkMigrationSql = "SELECT MAX(version) FROM schema_migrations;"
        XCTAssertEqual(sqlite3_prepare_v2(verifyDb, checkMigrationSql, -1, &stmt, nil), SQLITE_OK)
        XCTAssertEqual(sqlite3_step(stmt), SQLITE_ROW)
        let currentVersion = sqlite3_column_int(stmt, 0)
        XCTAssertEqual(currentVersion, 2, "Database must be migrated to version 2")
        sqlite3_finalize(stmt)

        // Verify metadata table exists
        let checkTableSql = "SELECT name FROM sqlite_master WHERE type='table' AND name='metadata';"
        XCTAssertEqual(sqlite3_prepare_v2(verifyDb, checkTableSql, -1, &stmt, nil), SQLITE_OK)
        XCTAssertEqual(sqlite3_step(stmt), SQLITE_ROW, "Metadata table must exist in V2")
        sqlite3_finalize(stmt)

        sqlite3_close(verifyDb)
        await store.close()
    }
}
