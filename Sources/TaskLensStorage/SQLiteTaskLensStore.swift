import Foundation
import SQLite3
import TaskLensCore

public actor SQLiteTaskLensStore: TaskLensStore {
    private var db: OpaquePointer?
    private let dbPath: String
    private let encoder = JSONEncoder()
    private let decoder = JSONDecoder()

    public init(path: String? = nil) {
        if let customPath = path {
            self.dbPath = customPath
        } else {
            let appSupport = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first
                ?? FileManager.default.temporaryDirectory
            let dir = appSupport.appendingPathComponent("TaskLens", isDirectory: true)
            try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
            self.dbPath = dir.appendingPathComponent("tasklens.sqlite").path
        }

        var pointer: OpaquePointer?
        let flags = SQLITE_OPEN_READWRITE | SQLITE_OPEN_CREATE | SQLITE_OPEN_FULLMUTEX
        if sqlite3_open_v2(dbPath, &pointer, flags, nil) == SQLITE_OK {
            self.db = pointer
            Self.execute(db: pointer, sql: "PRAGMA journal_mode=WAL;")
            Self.execute(db: pointer, sql: "PRAGMA synchronous=NORMAL;")
            Self.execute(db: pointer, sql: "PRAGMA foreign_keys=ON;")
            Self.runMigrations(db: pointer)
        } else {
            if let ptr = pointer {
                sqlite3_close(ptr)
            }
            self.db = nil
        }
    }

    public func close() {
        if let db = db {
            sqlite3_close(db)
            self.db = nil
        }
    }

    @discardableResult
    private static func execute(db: OpaquePointer?, sql: String) -> Bool {
        guard let db = db else { return false }
        var errMsg: UnsafeMutablePointer<CChar>?
        let result = sqlite3_exec(db, sql, nil, nil, &errMsg)
        if result != SQLITE_OK {
            if let err = errMsg {
                sqlite3_free(err)
            }
            return false
        }
        return true
    }

    @discardableResult
    private func execute(sql: String) -> Bool {
        Self.execute(db: self.db, sql: sql)
    }

    private static func runMigrations(db: OpaquePointer?) {
        execute(db: db, sql: """
            CREATE TABLE IF NOT EXISTS schema_migrations (
                version INTEGER PRIMARY KEY,
                applied_at REAL NOT NULL
            );
        """)

        let currentVersion = getCurrentVersion(db: db)

        if currentVersion < 1 {
            applyMigrationV1(db: db)
        }
        if currentVersion < 2 {
            applyMigrationV2(db: db)
        }
    }

    private static func getCurrentVersion(db: OpaquePointer?) -> Int {
        guard let db = db else { return 0 }
        var stmt: OpaquePointer?
        let sql = "SELECT COALESCE(MAX(version), 0) FROM schema_migrations;"
        if sqlite3_prepare_v2(db, sql, -1, &stmt, nil) == SQLITE_OK {
            if sqlite3_step(stmt) == SQLITE_ROW {
                let v = Int(sqlite3_column_int(stmt, 0))
                sqlite3_finalize(stmt)
                return v
            }
        }
        sqlite3_finalize(stmt)
        return 0
    }

    private static func applyMigrationV1(db: OpaquePointer?) {
        execute(db: db, sql: """
            CREATE TABLE IF NOT EXISTS tasks (
                id TEXT PRIMARY KEY,
                name TEXT,
                scheduler TEXT,
                submitted_at REAL,
                data_json TEXT NOT NULL
            );
        """)
        execute(db: db, sql: "CREATE INDEX IF NOT EXISTS idx_tasks_submitted_at ON tasks (submitted_at);")

        execute(db: db, sql: """
            CREATE TABLE IF NOT EXISTS attempts (
                attempt_id TEXT PRIMARY KEY,
                task_id TEXT NOT NULL,
                attempt_number INTEGER NOT NULL,
                started_at REAL,
                ended_at REAL,
                outcome TEXT,
                data_json TEXT NOT NULL
            );
        """)
        execute(db: db, sql: "CREATE INDEX IF NOT EXISTS idx_attempts_task_id ON attempts (task_id, attempt_number);")

        execute(db: db, sql: """
            CREATE TABLE IF NOT EXISTS events (
                id TEXT PRIMARY KEY,
                task_id TEXT,
                attempt_id TEXT,
                timestamp REAL NOT NULL,
                event_type TEXT NOT NULL,
                source TEXT NOT NULL,
                sequence_number INTEGER NOT NULL,
                data_json TEXT NOT NULL
            );
        """)
        execute(db: db, sql: "CREATE INDEX IF NOT EXISTS idx_events_task_seq ON events (task_id, sequence_number);")
        execute(db: db, sql: "CREATE INDEX IF NOT EXISTS idx_events_attempt_seq ON events (attempt_id, sequence_number);")

        execute(db: db, sql: """
            CREATE TABLE IF NOT EXISTS diagnoses (
                id TEXT PRIMARY KEY,
                task_id TEXT NOT NULL,
                data_json TEXT NOT NULL
            );
        """)
        execute(db: db, sql: "CREATE INDEX IF NOT EXISTS idx_diagnoses_task_id ON diagnoses (task_id);")

        execute(db: db, sql: "INSERT INTO schema_migrations (version, applied_at) VALUES (1, \(Date().timeIntervalSince1970));")
    }

    private static func applyMigrationV2(db: OpaquePointer?) {
        execute(db: db, sql: """
            CREATE TABLE IF NOT EXISTS metadata (
                key TEXT PRIMARY KEY,
                value TEXT NOT NULL,
                updated_at REAL NOT NULL
            );
        """)
        execute(db: db, sql: "CREATE INDEX IF NOT EXISTS idx_events_timestamp ON events (timestamp);")
        execute(db: db, sql: "INSERT INTO schema_migrations (version, applied_at) VALUES (2, \(Date().timeIntervalSince1970));")
    }

    public func append(event: TaskLensEvent) {
        guard let db = db else { return }
        guard let jsonData = try? encoder.encode(event),
              let jsonStr = String(data: jsonData, encoding: .utf8) else { return }

        let sql = """
            INSERT OR REPLACE INTO events (id, task_id, attempt_id, timestamp, event_type, source, sequence_number, data_json)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?);
        """
        var stmt: OpaquePointer?
        if sqlite3_prepare_v2(db, sql, -1, &stmt, nil) == SQLITE_OK {
            sqlite3_bind_text(stmt, 1, (event.id as NSString).utf8String, -1, nil)
            if let tid = event.taskId {
                sqlite3_bind_text(stmt, 2, (tid as NSString).utf8String, -1, nil)
            } else {
                sqlite3_bind_null(stmt, 2)
            }
            if let aid = event.attemptId {
                sqlite3_bind_text(stmt, 3, (aid as NSString).utf8String, -1, nil)
            } else {
                sqlite3_bind_null(stmt, 3)
            }
            sqlite3_bind_double(stmt, 4, event.timestamp.timeIntervalSince1970)
            sqlite3_bind_text(stmt, 5, (event.type.rawValue as NSString).utf8String, -1, nil)
            sqlite3_bind_text(stmt, 6, (event.source.rawValue as NSString).utf8String, -1, nil)
            sqlite3_bind_int64(stmt, 7, event.sequenceNumber)
            sqlite3_bind_text(stmt, 8, (jsonStr as NSString).utf8String, -1, nil)

            sqlite3_step(stmt)
        }
        sqlite3_finalize(stmt)
    }

    public func saveTask(_ task: ScheduledWork) {
        guard let db = db else { return }
        guard let jsonData = try? encoder.encode(task),
              let jsonStr = String(data: jsonData, encoding: .utf8) else { return }

        let sql = """
            INSERT OR REPLACE INTO tasks (id, name, scheduler, submitted_at, data_json)
            VALUES (?, ?, ?, ?, ?);
        """
        var stmt: OpaquePointer?
        if sqlite3_prepare_v2(db, sql, -1, &stmt, nil) == SQLITE_OK {
            sqlite3_bind_text(stmt, 1, (task.id as NSString).utf8String, -1, nil)
            if let name = task.name {
                sqlite3_bind_text(stmt, 2, (name as NSString).utf8String, -1, nil)
            } else {
                sqlite3_bind_null(stmt, 2)
            }
            sqlite3_bind_text(stmt, 3, (task.scheduler.rawValue as NSString).utf8String, -1, nil)
            if let submitted = task.submittedAt {
                sqlite3_bind_double(stmt, 4, submitted.timeIntervalSince1970)
            } else {
                sqlite3_bind_null(stmt, 4)
            }
            sqlite3_bind_text(stmt, 5, (jsonStr as NSString).utf8String, -1, nil)

            sqlite3_step(stmt)
        }
        sqlite3_finalize(stmt)
    }

    public func saveAttempt(_ attempt: ExecutionAttempt) {
        guard let db = db else { return }
        guard let jsonData = try? encoder.encode(attempt),
              let jsonStr = String(data: jsonData, encoding: .utf8) else { return }

        let sql = """
            INSERT OR REPLACE INTO attempts (attempt_id, task_id, attempt_number, started_at, ended_at, outcome, data_json)
            VALUES (?, ?, ?, ?, ?, ?, ?);
        """
        var stmt: OpaquePointer?
        if sqlite3_prepare_v2(db, sql, -1, &stmt, nil) == SQLITE_OK {
            sqlite3_bind_text(stmt, 1, (attempt.attemptId as NSString).utf8String, -1, nil)
            sqlite3_bind_text(stmt, 2, (attempt.taskId as NSString).utf8String, -1, nil)
            sqlite3_bind_int(stmt, 3, Int32(attempt.attemptNumber))
            if let start = attempt.startedAt {
                sqlite3_bind_double(stmt, 4, start.timeIntervalSince1970)
            } else {
                sqlite3_bind_null(stmt, 4)
            }
            if let end = attempt.endedAt {
                sqlite3_bind_double(stmt, 5, end.timeIntervalSince1970)
            } else {
                sqlite3_bind_null(stmt, 5)
            }
            if let outcome = attempt.outcome {
                sqlite3_bind_text(stmt, 6, (outcome.rawValue as NSString).utf8String, -1, nil)
            } else {
                sqlite3_bind_null(stmt, 6)
            }
            sqlite3_bind_text(stmt, 7, (jsonStr as NSString).utf8String, -1, nil)

            sqlite3_step(stmt)
        }
        sqlite3_finalize(stmt)
    }

    public func saveDiagnosis(_ diagnosis: Diagnosis) {
        guard let db = db else { return }
        guard let jsonData = try? encoder.encode(diagnosis),
              let jsonStr = String(data: jsonData, encoding: .utf8) else { return }

        let sql = "INSERT OR REPLACE INTO diagnoses (id, task_id, data_json) VALUES (?, ?, ?);"
        var stmt: OpaquePointer?
        if sqlite3_prepare_v2(db, sql, -1, &stmt, nil) == SQLITE_OK {
            sqlite3_bind_text(stmt, 1, (diagnosis.id as NSString).utf8String, -1, nil)
            sqlite3_bind_text(stmt, 2, (diagnosis.taskId as NSString).utf8String, -1, nil)
            sqlite3_bind_text(stmt, 3, (jsonStr as NSString).utf8String, -1, nil)
            sqlite3_step(stmt)
        }
        sqlite3_finalize(stmt)
    }

    public func tasks(query: TaskQuery) -> [ScheduledWork] {
        guard let db = db else { return [] }
        var sql = "SELECT data_json FROM tasks WHERE 1=1"
        var params: [String] = []

        if let s = query.scheduler {
            sql += " AND scheduler = ?"
            params.append(s.rawValue)
        }
        if let q = query.searchQuery?.lowercased(), !q.isEmpty {
            sql += " AND (LOWER(id) LIKE ? OR LOWER(name) LIKE ?)"
            params.append("%\(q)%")
            params.append("%\(q)%")
        }

        sql += " ORDER BY submitted_at DESC LIMIT ? OFFSET ?;"

        var stmt: OpaquePointer?
        var results: [ScheduledWork] = []
        if sqlite3_prepare_v2(db, sql, -1, &stmt, nil) == SQLITE_OK {
            var bindIdx: Int32 = 1
            for p in params {
                sqlite3_bind_text(stmt, bindIdx, (p as NSString).utf8String, -1, nil)
                bindIdx += 1
            }
            sqlite3_bind_int(stmt, bindIdx, Int32(query.limit))
            bindIdx += 1
            sqlite3_bind_int(stmt, bindIdx, Int32(query.offset))

            while sqlite3_step(stmt) == SQLITE_ROW {
                if let rawText = sqlite3_column_text(stmt, 0) {
                    let jsonString = String(cString: rawText)
                    if let data = jsonString.data(using: .utf8),
                       let item = try? decoder.decode(ScheduledWork.self, from: data) {
                        results.append(item)
                    }
                }
            }
        }
        sqlite3_finalize(stmt)
        return results
    }

    public func task(id: String) -> ScheduledWork? {
        guard let db = db else { return nil }
        let sql = "SELECT data_json FROM tasks WHERE id = ? LIMIT 1;"
        var stmt: OpaquePointer?
        var result: ScheduledWork?
        if sqlite3_prepare_v2(db, sql, -1, &stmt, nil) == SQLITE_OK {
            sqlite3_bind_text(stmt, 1, (id as NSString).utf8String, -1, nil)
            if sqlite3_step(stmt) == SQLITE_ROW {
                if let rawText = sqlite3_column_text(stmt, 0) {
                    let jsonString = String(cString: rawText)
                    if let data = jsonString.data(using: .utf8) {
                        result = try? decoder.decode(ScheduledWork.self, from: data)
                    }
                }
            }
        }
        sqlite3_finalize(stmt)
        return result
    }

    public func attempts(taskId: String) -> [ExecutionAttempt] {
        guard let db = db else { return [] }
        let sql = "SELECT data_json FROM attempts WHERE task_id = ? ORDER BY attempt_number ASC;"
        var stmt: OpaquePointer?
        var results: [ExecutionAttempt] = []
        if sqlite3_prepare_v2(db, sql, -1, &stmt, nil) == SQLITE_OK {
            sqlite3_bind_text(stmt, 1, (taskId as NSString).utf8String, -1, nil)
            while sqlite3_step(stmt) == SQLITE_ROW {
                if let rawText = sqlite3_column_text(stmt, 0) {
                    let jsonString = String(cString: rawText)
                    if let data = jsonString.data(using: .utf8),
                       let item = try? decoder.decode(ExecutionAttempt.self, from: data) {
                        results.append(item)
                    }
                }
            }
        }
        sqlite3_finalize(stmt)
        return results
    }

    public func events(taskId: String) -> [TaskLensEvent] {
        guard let db = db else { return [] }
        let sql = "SELECT data_json FROM events WHERE task_id = ? ORDER BY sequence_number ASC;"
        var stmt: OpaquePointer?
        var results: [TaskLensEvent] = []
        if sqlite3_prepare_v2(db, sql, -1, &stmt, nil) == SQLITE_OK {
            sqlite3_bind_text(stmt, 1, (taskId as NSString).utf8String, -1, nil)
            while sqlite3_step(stmt) == SQLITE_ROW {
                if let rawText = sqlite3_column_text(stmt, 0) {
                    let jsonString = String(cString: rawText)
                    if let data = jsonString.data(using: .utf8),
                       let item = try? decoder.decode(TaskLensEvent.self, from: data) {
                        results.append(item)
                    }
                }
            }
        }
        sqlite3_finalize(stmt)
        return results
    }

    public func diagnoses(taskId: String) -> [Diagnosis] {
        guard let db = db else { return [] }
        let sql = "SELECT data_json FROM diagnoses WHERE task_id = ?;"
        var stmt: OpaquePointer?
        var results: [Diagnosis] = []
        if sqlite3_prepare_v2(db, sql, -1, &stmt, nil) == SQLITE_OK {
            sqlite3_bind_text(stmt, 1, (taskId as NSString).utf8String, -1, nil)
            while sqlite3_step(stmt) == SQLITE_ROW {
                if let rawText = sqlite3_column_text(stmt, 0) {
                    let jsonString = String(cString: rawText)
                    if let data = jsonString.data(using: .utf8),
                       let item = try? decoder.decode(Diagnosis.self, from: data) {
                        results.append(item)
                    }
                }
            }
        }
        sqlite3_finalize(stmt)
        return results
    }

    public func environmentEvents(limit: Int) -> [TaskLensEvent] {
        guard let db = db else { return [] }
        let sql = """
            SELECT data_json FROM events
            WHERE event_type IN ('NETWORK_CHANGED', 'BATTERY_CHANGED', 'POWER_MODE_CHANGED', 'APP_STATE_CHANGED')
            ORDER BY timestamp DESC LIMIT ?;
        """
        var stmt: OpaquePointer?
        var results: [TaskLensEvent] = []
        if sqlite3_prepare_v2(db, sql, -1, &stmt, nil) == SQLITE_OK {
            sqlite3_bind_int(stmt, 1, Int32(limit))
            while sqlite3_step(stmt) == SQLITE_ROW {
                if let rawText = sqlite3_column_text(stmt, 0) {
                    let jsonString = String(cString: rawText)
                    if let data = jsonString.data(using: .utf8),
                       let item = try? decoder.decode(TaskLensEvent.self, from: data) {
                        results.append(item)
                    }
                }
            }
        }
        sqlite3_finalize(stmt)
        return results
    }

    public func clear() {
        execute(sql: "DELETE FROM events;")
        execute(sql: "DELETE FROM attempts;")
        execute(sql: "DELETE FROM tasks;")
        execute(sql: "DELETE FROM diagnoses;")
        execute(sql: "DELETE FROM metadata;")
    }

    public func applyRetention(_ policy: RetentionPolicy) {
        switch policy {
        case .forever:
            break
        case .maxTasks(let count):
            let sql = """
                DELETE FROM tasks WHERE id NOT IN (
                    SELECT id FROM tasks ORDER BY submitted_at DESC LIMIT \(count)
                );
            """
            execute(sql: sql)
            execute(sql: "DELETE FROM attempts WHERE task_id NOT IN (SELECT id FROM tasks);")
            execute(sql: "DELETE FROM events WHERE task_id IS NOT NULL AND task_id NOT IN (SELECT id FROM tasks);")
            execute(sql: "DELETE FROM diagnoses WHERE task_id NOT IN (SELECT id FROM tasks);")
        case .lastDays(let days):
            let cutoff = Date().addingTimeInterval(-Double(days * 86400)).timeIntervalSince1970
            execute(sql: "DELETE FROM tasks WHERE submitted_at < \(cutoff);")
            execute(sql: "DELETE FROM attempts WHERE task_id NOT IN (SELECT id FROM tasks);")
            execute(sql: "DELETE FROM events WHERE task_id IS NOT NULL AND task_id NOT IN (SELECT id FROM tasks);")
            execute(sql: "DELETE FROM diagnoses WHERE task_id NOT IN (SELECT id FROM tasks);")
        }
    }
}
