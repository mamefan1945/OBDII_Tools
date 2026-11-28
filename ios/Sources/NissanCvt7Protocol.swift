import Foundation

struct NissanCvt7LiveData {
    let vehicleSpeedKph: Int
    let engineRpm: Int
    let slipRpm: Int
    let fluidTemperatureC: Int
    let gearRatio: Double
    let primaryPressureMpa: Double
    let secondaryPressureMpa: Double
    let targetSecondaryPressureMpa: Double
    let linePressureMpa: Double
}

struct NissanCvt7PollResult {
    let sample: NissanCvt7LiveData?
    let sessionResponse: String
    let rawResponse: String
    let parseStatus: String
}

/// Read-only Nissan CVT7 21 01 decoder. The payload layout follows the
/// CVT7 reference formatter and is accepted only when the ELM 07A framing
/// prefix and the complete minimum response length are present.
enum NissanCvt7Protocol {
    private static let requiredResponseBytes = 83

    static func parseLiveData(_ rawResponse: String) -> NissanCvt7LiveData? {
        let lines = rawResponse.components(separatedBy: .newlines)
        guard let first = lines.first?
            .filter({ !$0.isWhitespace }).uppercased(),
              first.hasPrefix("07A") else { return nil }

        var payload = ""
        for (index, rawLine) in lines.enumerated() {
            var line = rawLine.filter { !$0.isWhitespace }.uppercased()
            if index == 0 { line = String(line.dropFirst(3)) }
            guard !line.isEmpty else { continue }
            if let colon = line.firstIndex(of: ":") {
                let label = String(line[..<colon])
                guard label.count == 1, label.first?.isHexDigit == true else { continue }
                line = String(line[line.index(after: colon)...])
            }
            guard line.allSatisfy(\.isHexDigit) else { continue }
            payload += line
        }

        guard payload.hasPrefix("6101"), payload.count.isMultiple(of: 2) else { return nil }
        var bytes: [Int] = []
        var cursor = payload.startIndex
        while cursor < payload.endIndex {
            let end = payload.index(cursor, offsetBy: 2)
            guard let value = Int(payload[cursor..<end], radix: 16) else { return nil }
            bytes.append(value)
            cursor = end
        }
        guard bytes.count >= requiredResponseBytes else { return nil }

        return NissanCvt7LiveData(
            vehicleSpeedKph: bytes[15],
            engineRpm: bytes[21] << 5,
            slipRpm: bytes[22] > 127 ? bytes[22] - 256 : bytes[22],
            fluidTemperatureC: bytes[37] - 40,
            gearRatio: Double(bytes[24]) * 0.045,
            primaryPressureMpa: Double(bytes[44]) * 0.025,
            secondaryPressureMpa: Double(bytes[36]) * 0.025,
            targetSecondaryPressureMpa: Double(bytes[43]) * 0.025,
            linePressureMpa: Double(bytes[43]) * 0.025
        )
    }

    static func diagnoseLiveData(_ rawResponse: String) -> String {
        let lines = rawResponse.components(separatedBy: .newlines)
        guard let first = lines.first?
            .filter({ !$0.isWhitespace }).uppercased(),
              first.hasPrefix("07A") else {
            return "Framing mismatch: expected ELM 07A response prefix."
        }
        var payload = ""
        for (index, rawLine) in lines.enumerated() {
            var line = rawLine.filter { !$0.isWhitespace }.uppercased()
            if index == 0 { line = String(line.dropFirst(3)) }
            if let colon = line.firstIndex(of: ":") {
                let label = String(line[..<colon])
                guard label.count == 1, label.first?.isHexDigit == true else { continue }
                line = String(line[line.index(after: colon)...])
            }
            if !line.isEmpty && line.allSatisfy(\.isHexDigit) { payload += line }
        }
        guard payload.hasPrefix("6101") else {
            return "Unexpected response payload; expected 6101."
        }
        guard payload.count.isMultiple(of: 2) else { return "Malformed response length." }
        let count = payload.count / 2
        guard count >= requiredResponseBytes else {
            return "Short response: \(count) bytes; need at least \(requiredResponseBytes)."
        }
        return "Complete response received, but decoding failed."
    }
}
