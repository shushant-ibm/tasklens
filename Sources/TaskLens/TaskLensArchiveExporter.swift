import Foundation
import TaskLensCore
import TaskLensStorage
import TaskLensDiagnosis

public enum TaskLensArchiveExporter {

    private struct ZipEntryItem {
        let name: String
        let data: Data
    }

    public static func createArchive(
        taskId: String,
        store: any TaskLensStore,
        redactor: TaskLensRedactor = DefaultTaskLensRedactor.shared,
        appVersion: String = "1.0.0",
        deviceMetadata: [String: String] = [:]
    ) async throws -> URL {
        let events = await store.events(taskId: taskId)
        let attempts = await store.attempts(taskId: taskId)
        let diagnoses = await store.diagnoses(taskId: taskId)
        let task = await store.task(id: taskId)
        let envEvents = await store.environmentEvents(limit: 100)

        var entries: [ZipEntryItem] = []

        // 1. manifest.json
        let manifest: [String: Any] = [
            "schema_version": 1,
            "task_id": taskId,
            "platform": "ios",
            "app_version": appVersion,
            "exported_at_epoch_ms": Int64(Date().timeIntervalSince1970 * 1000)
        ]
        let manifestData = try JSONSerialization.data(withJSONObject: manifest, options: [.sortedKeys])
        entries.append(ZipEntryItem(name: "manifest.json", data: manifestData))

        // 2. task.json
        let taskDict: [String: Any]
        if let t = task {
            taskDict = [
                "id": t.id,
                "name": t.name ?? "",
                "scheduler_type": t.scheduler.rawValue,
                "type": t.type.rawValue,
                "periodic": t.periodic
            ]
        } else {
            taskDict = [:]
        }
        let taskData = try JSONSerialization.data(withJSONObject: taskDict, options: [.sortedKeys])
        entries.append(ZipEntryItem(name: "task.json", data: taskData))

        // 3. attempts.json
        let attemptsArr: [[String: Any]] = attempts.map { a in
            let duration: Int64
            if let s = a.startedAt, let e = a.endedAt {
                duration = Int64(e.timeIntervalSince(s) * 1000)
            } else {
                duration = -1
            }
            return [
                "id": a.attemptId,
                "attempt_id": a.attemptId,
                "attemptId": a.attemptId,
                "task_id": a.taskId,
                "attempt_number": a.attemptNumber,
                "outcome": a.outcome?.rawValue ?? "UNKNOWN",
                "started_at_epoch_ms": a.startedAt != nil ? Int64(a.startedAt!.timeIntervalSince1970 * 1000) : -1,
                "ended_at_epoch_ms": a.endedAt != nil ? Int64(a.endedAt!.timeIntervalSince1970 * 1000) : -1,
                "duration_ms": duration,
                "stop_reason": a.platformReason?.name ?? ""
            ]
        }
        let attemptsData = try JSONSerialization.data(withJSONObject: attemptsArr, options: [.sortedKeys])
        entries.append(ZipEntryItem(name: "attempts.json", data: attemptsData))

        // 4. timeline.json
        let sortedEvents = events.sorted { $0.sequenceNumber < $1.sequenceNumber }
        let timelineArr: [[String: Any]] = sortedEvents.map { e in
            [
                "type": e.type.rawValue,
                "timestamp_epoch_ms": Int64(e.timestamp.timeIntervalSince1970 * 1000),
                "attributes": e.attributes
            ]
        }
        let timelineData = try JSONSerialization.data(withJSONObject: timelineArr, options: [.sortedKeys])
        entries.append(ZipEntryItem(name: "timeline.json", data: timelineData))

        // 5. diagnosis.json
        let diagnosisArr: [[String: Any]] = diagnoses.map { d in
            [
                "id": d.id,
                "task_id": d.taskId,
                "rule_id": d.ruleId,
                "title": d.title,
                "summary": d.summary,
                "confidence": d.confidence.rawValue,
                "classification": d.classification.rawValue
            ]
        }
        let diagnosisData = try JSONSerialization.data(withJSONObject: diagnosisArr, options: [.sortedKeys])
        entries.append(ZipEntryItem(name: "diagnosis.json", data: diagnosisData))

        // 6. evidence.json
        let allEvidence = diagnoses.flatMap { $0.evidence }
        let evidenceArr: [[String: Any]] = allEvidence.map { ev in
            [
                "id": ev.id,
                "type": ev.type.rawValue,
                "source": ev.source.rawValue,
                "title": ev.title,
                "description": ev.description
            ]
        }
        let evidenceData = try JSONSerialization.data(withJSONObject: evidenceArr, options: [.sortedKeys])
        entries.append(ZipEntryItem(name: "evidence.json", data: evidenceData))

        // 7. environment.json
        let envArr: [[String: Any]] = envEvents.map { e in
            [
                "id": e.id,
                "type": e.type.rawValue,
                "timestamp_epoch_ms": Int64(e.timestamp.timeIntervalSince1970 * 1000),
                "attributes": e.attributes
            ]
        }
        let envData = try JSONSerialization.data(withJSONObject: envArr, options: [.sortedKeys])
        entries.append(ZipEntryItem(name: "environment.json", data: envData))

        // 8. limitations.json
        let allLimitations = diagnoses.flatMap { $0.limitations }
        let limitationsArr: [[String: Any]] = allLimitations.map { lim in
            [
                "code": lim.code,
                "message": lim.message
            ]
        }
        let limitationsData = try JSONSerialization.data(withJSONObject: limitationsArr, options: [.sortedKeys])
        entries.append(ZipEntryItem(name: "limitations.json", data: limitationsData))

        // 9. events.json (with redaction)
        let eventsArr: [[String: Any]] = sortedEvents.map { e in
            var redactedAttrs: [String: String] = [:]
            for (k, v) in e.attributes {
                redactedAttrs[k] = redactor.redact(key: k, value: v)
            }
            return [
                "id": e.id,
                "task_id": e.taskId ?? "",
                "attempt_id": e.attemptId ?? "",
                "timestamp_epoch_ms": Int64(e.timestamp.timeIntervalSince1970 * 1000),
                "sequence_number": e.sequenceNumber,
                "type": e.type.rawValue,
                "source": e.source.rawValue,
                "severity": e.severity.rawValue,
                "schema_version": e.schemaVersion,
                "attributes": redactedAttrs
            ]
        }
        let eventsData = try JSONSerialization.data(withJSONObject: eventsArr, options: [.sortedKeys])
        entries.append(ZipEntryItem(name: "events.json", data: eventsData))

        // 10. device.json
        let deviceData = try JSONSerialization.data(withJSONObject: deviceMetadata, options: [.sortedKeys])
        entries.append(ZipEntryItem(name: "device.json", data: deviceData))

        // 11. README.html
        let taskName = task?.name ?? taskId
        let html = """
        <!DOCTYPE html>
        <html><head><meta charset="utf-8">
        <title>TaskLens Export — \(taskName)</title></head>
        <body>
        <h1>TaskLens Export</h1>
        <h2>Task: \(taskName)</h2>
        <p>Platform: ios</p>
        <p>App version: \(appVersion)</p>
        <p>Events: \(events.count)</p>
        <p>Attempts: \(attempts.count)</p>
        </body></html>
        """
        entries.append(ZipEntryItem(name: "README.html", data: Data(html.utf8)))

        // Build ZIP container
        let zipData = buildZipArchive(entries: entries)

        let tempDir = FileManager.default.temporaryDirectory
        let exportUrl = tempDir.appendingPathComponent("tasklens_\(taskId).tasklens")
        try zipData.write(to: exportUrl, options: .atomic)
        return exportUrl
    }

    private static func buildZipArchive(entries: [ZipEntryItem]) -> Data {
        var zip = Data()
        var centralDirectory = Data()
        var localHeaderOffsets: [UInt32] = []

        for entry in entries {
            let offset = UInt32(zip.count)
            localHeaderOffsets.append(offset)

            let nameData = Data(entry.name.utf8)
            let nameLength = UInt16(nameData.count)
            let crc = crc32(data: entry.data)
            let size = UInt32(entry.data.count)

            // Local File Header
            zip.append(UInt32(0x04034b50).littleEndianBytes) // Signature
            zip.append(UInt16(20).littleEndianBytes)        // Version needed (2.0)
            zip.append(UInt16(0x0800).littleEndianBytes)     // Bit flag (UTF-8)
            zip.append(UInt16(0).littleEndianBytes)          // Compression: Store (0)
            zip.append(UInt16(0).littleEndianBytes)          // Mod time
            zip.append(UInt16(0).littleEndianBytes)          // Mod date
            zip.append(crc.littleEndianBytes)               // CRC-32
            zip.append(size.littleEndianBytes)              // Compressed size
            zip.append(size.littleEndianBytes)              // Uncompressed size
            zip.append(nameLength.littleEndianBytes)        // Name length
            zip.append(UInt16(0).littleEndianBytes)          // Extra field length
            zip.append(nameData)
            zip.append(entry.data)

            // Central Directory Header
            centralDirectory.append(UInt32(0x02014b50).littleEndianBytes) // Central directory signature
            centralDirectory.append(UInt16(20).littleEndianBytes)         // Version made by
            centralDirectory.append(UInt16(20).littleEndianBytes)         // Version needed
            centralDirectory.append(UInt16(0x0800).littleEndianBytes)      // Bit flag
            centralDirectory.append(UInt16(0).littleEndianBytes)           // Compression: Store
            centralDirectory.append(UInt16(0).littleEndianBytes)           // Mod time
            centralDirectory.append(UInt16(0).littleEndianBytes)           // Mod date
            centralDirectory.append(crc.littleEndianBytes)                // CRC-32
            centralDirectory.append(size.littleEndianBytes)               // Compressed size
            centralDirectory.append(size.littleEndianBytes)               // Uncompressed size
            centralDirectory.append(nameLength.littleEndianBytes)         // Name length
            centralDirectory.append(UInt16(0).littleEndianBytes)           // Extra field length
            centralDirectory.append(UInt16(0).littleEndianBytes)           // Comment length
            centralDirectory.append(UInt16(0).littleEndianBytes)           // Disk number start
            centralDirectory.append(UInt16(0).littleEndianBytes)           // Internal attributes
            centralDirectory.append(UInt32(0).littleEndianBytes)           // External attributes
            centralDirectory.append(offset.littleEndianBytes)             // Relative offset of local header
            centralDirectory.append(nameData)
        }

        let centralDirOffset = UInt32(zip.count)
        let centralDirSize = UInt32(centralDirectory.count)
        let entryCount = UInt16(entries.count)

        zip.append(centralDirectory)

        // End of Central Directory Record (EOCD)
        zip.append(UInt32(0x06054b50).littleEndianBytes) // EOCD signature
        zip.append(UInt16(0).littleEndianBytes)          // Disk number
        zip.append(UInt16(0).littleEndianBytes)          // Start disk
        zip.append(entryCount.littleEndianBytes)         // Entries on this disk
        zip.append(entryCount.littleEndianBytes)         // Total entries
        zip.append(centralDirSize.littleEndianBytes)     // Central dir size
        zip.append(centralDirOffset.littleEndianBytes)   // Central dir offset
        zip.append(UInt16(0).littleEndianBytes)          // Comment length

        return zip
    }

    private static func crc32(data: Data) -> UInt32 {
        var table = [UInt32](repeating: 0, count: 256)
        for i in 0..<256 {
            var c = UInt32(i)
            for _ in 0..<8 {
                if (c & 1) != 0 {
                    c = 0xedb88320 ^ (c >> 1)
                } else {
                    c = c >> 1
                }
            }
            table[i] = c
        }

        var crc: UInt32 = 0xffffffff
        for byte in data {
            let index = Int((crc ^ UInt32(byte)) & 0xff)
            crc = (crc >> 8) ^ table[index]
        }
        return crc ^ 0xffffffff
    }
}

private extension UInt16 {
    var littleEndianBytes: Data {
        var val = self.littleEndian
        return Data(bytes: &val, count: MemoryLayout<UInt16>.size)
    }
}

private extension UInt32 {
    var littleEndianBytes: Data {
        var val = self.littleEndian
        return Data(bytes: &val, count: MemoryLayout<UInt32>.size)
    }
}
