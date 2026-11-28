import XCTest
@testable import OBDIITools

final class NissanCvt7ProtocolTests: XCTestCase {
    func testDecodesCompleteCVT7TelemetryAndSignedSlip() {
        var bytes = [Int](repeating: 0, count: 83)
        bytes[0] = 0x61
        bytes[1] = 0x01
        bytes[15] = 30
        bytes[21] = 24
        bytes[22] = 0xFE
        bytes[24] = 55
        bytes[36] = 23
        bytes[37] = 96
        bytes[43] = 20
        bytes[44] = 10
        let sample = NissanCvt7Protocol.parseLiveData(indexedResponse(bytes))

        XCTAssertEqual(sample?.vehicleSpeedKph, 30)
        XCTAssertEqual(sample?.engineRpm, 768)
        XCTAssertEqual(sample?.slipRpm, -2)
        XCTAssertEqual(sample?.fluidTemperatureC, 56)
        XCTAssertEqual(sample?.gearRatio ?? 0, 2.475, accuracy: 0.0001)
        XCTAssertEqual(sample?.primaryPressureMpa ?? 0, 0.25, accuracy: 0.0001)
        XCTAssertEqual(sample?.secondaryPressureMpa ?? 0, 0.575, accuracy: 0.0001)
    }

    func testRejectsObserved62BytePartialResponse() {
        var bytes = [Int](repeating: 0, count: 62)
        bytes[0] = 0x61
        bytes[1] = 0x01
        let raw = indexedResponse(bytes)
        XCTAssertNil(NissanCvt7Protocol.parseLiveData(raw))
        XCTAssertEqual(
            NissanCvt7Protocol.diagnoseLiveData(raw),
            "Short response: 62 bytes; need at least 83."
        )
    }

    private func indexedResponse(_ bytes: [Int]) -> String {
        let hex = bytes.map { String(format: "%02X", $0) }.joined()
        var lines = ["07A"]
        let chars = Array(hex)
        for start in stride(from: 0, to: chars.count, by: 14) {
            let end = min(start + 14, chars.count)
            lines.append(String(format: "%X:", start / 14) + String(chars[start..<end]))
        }
        return lines.joined(separator: "\n")
    }
}
