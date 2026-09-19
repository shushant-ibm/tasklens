import XCTest
@testable import TaskLensCore

final class TaskLensConcurrencyTests: XCTestCase {

    func testHighConcurrencySequenceGenerationSwift() {
        TaskLensEvent.resetSequence(to: 0)

        let numThreads = 20
        let incrementsPerThread = 5_000
        let totalExpected = numThreads * incrementsPerThread

        var allSequences = [Int64](repeating: 0, count: totalExpected)
        let queue = DispatchQueue(label: "test.concurrency.queue", attributes: .concurrent)
        let group = DispatchGroup()

        for t in 0..<numThreads {
            group.enter()
            queue.async {
                let baseIdx = t * incrementsPerThread
                for i in 0..<incrementsPerThread {
                    allSequences[baseIdx + i] = TaskLensEvent.nextSequence()
                }
                group.leave()
            }
        }

        group.wait()

        allSequences.sort()

        XCTAssertEqual(allSequences.first, 1, "First sequence ID should be 1")
        XCTAssertEqual(allSequences.last, Int64(totalExpected), "Last sequence ID should be \(totalExpected)")

        var prev: Int64 = 0
        for (idx, seq) in allSequences.enumerated() {
            let expected = prev + 1
            XCTAssertEqual(seq, expected, "Mismatch at index \(idx): expected \(expected) but got \(seq)")
            prev = seq
        }

        XCTAssertEqual(prev, Int64(totalExpected), "All \(totalExpected) sequence IDs strictly monotonic with zero gaps")
    }
}
