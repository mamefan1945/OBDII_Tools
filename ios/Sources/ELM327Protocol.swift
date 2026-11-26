import Foundation

/// Platform-independent ELM327 / SAE J1979 decoding.
/// Kept separate from CoreBluetooth so protocol behavior can be tested without hardware.
enum ELM327Protocol {
    static let initializationCommands = [
        "ATZ", "ATE0", "ATL0", "ATS0", "ATH0", "ATAT1", "ATSP0"
    ]

    enum PID {
        static let engineLoad = "0104"
        static let coolantTemp = "0105"
        static let stftBank1 = "0106"
        static let ltftBank1 = "0107"
        static let stftBank2 = "0108"
        static let ltftBank2 = "0109"
        static let fuelPressure = "010A"
        static let timingAdvance = "010E"
        static let intakeTemp = "010F"
        static let maf = "0110"
        static let throttle = "0111"
        static let o2Bank1Sensor1 = "0114"
        static let o2Bank1Sensor2 = "0115"
        static let o2Bank2Sensor1 = "0118"
        static let o2Bank2Sensor2 = "0119"
        static let rpm = "010C"
        static let speed = "010D"
        static let fuelLevel = "012F"
        static let barometricPressure = "0133"
        static let ambientTemp = "0146"
        static let oilTemp = "015C"
        static let readiness = "0101"
        static let storedDTC = "03"
        static let pendingDTC = "07"
        static let clearDTC = "04"
        static let voltage = "ATRV"
    }

    static let fastPIDs = [
        PID.rpm, PID.speed, PID.throttle, PID.coolantTemp, PID.engineLoad, PID.maf
    ]

    static let slowPIDs = [
        PID.intakeTemp, PID.timingAdvance, PID.fuelLevel, PID.ambientTemp,
        PID.oilTemp, PID.barometricPressure, PID.fuelPressure,
        PID.stftBank1, PID.ltftBank1, PID.stftBank2, PID.ltftBank2,
        PID.o2Bank1Sensor1, PID.o2Bank1Sensor2, PID.o2Bank2Sensor1, PID.o2Bank2Sensor2
    ]

    private static let pidByByte: [String: String] = Dictionary(
        uniqueKeysWithValues: (fastPIDs + slowPIDs).map { (String($0.suffix(2)), $0) }
    )

    static func filterSupportedPIDs(_ requested: [String], supported: Set<String>) -> [String] {
        guard !supported.isEmpty else { return requested }
        let alwaysProbe: Set<String> = [PID.maf, PID.fuelLevel]
        return requested.filter { supported.contains($0) || alwaysProbe.contains($0) }
    }

    static func buildMultiPIDCommand(_ pids: [String]) -> String {
        "01 " + pids.map { String($0.suffix(2)) }.joined(separator: " ")
    }

    static func parseMultiPIDResponse(_ lines: [String]) -> [String: Double] {
        var result: [String: Double] = [:]
        for rawLine in lines {
            let line = rawLine
                .replacingOccurrences(of: #"(?i)^[0-9A-F]:"#, with: "", options: .regularExpression)
                .filter { !$0.isWhitespace }
                .uppercased()
            var offset = 0
            while offset + 4 <= line.count {
                let start = line.index(line.startIndex, offsetBy: offset)
                let marker = line[start...].prefix(2)
                if marker == "41" {
                    let pidStart = line.index(start, offsetBy: 2)
                    let pidEnd = line.index(pidStart, offsetBy: 2)
                    let pidByte = String(line[pidStart..<pidEnd])
                    if let pid = pidByByte[pidByte] {
                        let payloadBytes = responsePayloadBytes(pid)
                        let endOffset = offset + 4 + payloadBytes * 2
                        let end = line.index(line.startIndex, offsetBy: min(endOffset, line.count))
                        if let value = parseResponse(pid, rawResponse: String(line[start..<end])) {
                            result[pid] = value
                        }
                        offset = min(endOffset, line.count)
                        continue
                    }
                }
                offset += 1
            }
        }
        return result
    }

    private static func responsePayloadBytes(_ pid: String) -> Int {
        [PID.rpm, PID.maf, PID.o2Bank1Sensor1, PID.o2Bank1Sensor2,
         PID.o2Bank2Sensor1, PID.o2Bank2Sensor2].contains(pid) ? 2 : 1
    }

    static func parseResponse(_ pid: String, rawResponse: String) -> Double? {
        let cleaned = rawResponse
            .replacingOccurrences(of: #"\s+"#, with: "", options: .regularExpression)
            .uppercased()
        guard !cleaned.isEmpty,
              !cleaned.contains("NODATA"), !cleaned.contains("ERROR"),
              !cleaned.contains("?") else { return nil }

        if pid == PID.voltage {
            return Double(cleaned.replacingOccurrences(of: "V", with: ""))
        }

        let bytes = extractBytes(cleaned, count: responsePayloadBytes(pid))
        guard let a = bytes.first else { return nil }
        let b = bytes.count > 1 ? bytes[1] : 0

        switch pid {
        case PID.rpm: return Double((a * 256 + b) / 4)
        case PID.speed, PID.barometricPressure, PID.readiness: return Double(a)
        case PID.coolantTemp, PID.intakeTemp, PID.oilTemp, PID.ambientTemp: return Double(a - 40)
        case PID.throttle, PID.engineLoad, PID.fuelLevel: return Double(a) * 100.0 / 255.0
        case PID.timingAdvance: return Double(a) / 2.0 - 64.0
        case PID.maf: return Double(a * 256 + b) / 100.0
        case PID.fuelPressure: return Double(a * 3)
        case PID.stftBank1, PID.stftBank2, PID.ltftBank1, PID.ltftBank2:
            return (Double(a) - 128.0) * 100.0 / 128.0
        case PID.o2Bank1Sensor1, PID.o2Bank1Sensor2, PID.o2Bank2Sensor1, PID.o2Bank2Sensor2:
            return Double(a) / 200.0
        default: return nil
        }
    }


    static func parseMode09VIN(_ rawResponse: String) -> String? {
        let lines = rawResponse.uppercased().components(separatedBy: .newlines)
            .map { $0.filter { $0.isHexDigit || $0 == ":" } }
            .filter { !$0.isEmpty }
        let frames = lines.compactMap { line -> (Int, String)? in
            guard let colon = line.firstIndex(of: ":"),
                  let index = Int(line[..<colon], radix: 16) else { return nil }
            return (index, String(line[line.index(after: colon)...]))
        }
        let payload: String
        if !frames.isEmpty {
            payload = frames.sorted { $0.0 < $1.0 }.map { frame in
                if frame.0 == 0 && frame.1.hasPrefix("4902") {
                    return String(frame.1.dropFirst(4))
                }
                return frame.0 == 0 ? "" : frame.1
            }.joined()
        } else {
            let compact = rawResponse.uppercased().filter { $0.isHexDigit }
            guard let range = compact.range(of: "4902") else { return nil }
            payload = String(compact[range.upperBound...])
        }
        return vinFromHexBytes(payload)
    }

    static func parseUDSVIN(_ rawResponse: String) -> String? {
        let compact = rawResponse.uppercased().filter { $0.isHexDigit }
        let payload: String
        if let range = compact.range(of: "62F190") {
            payload = String(compact[range.upperBound...])
        } else {
            payload = compact
        }
        return vinFromHexBytes(payload)
    }

    static func isValidVIN(_ vin: String) -> Bool {
        vin.range(of: #"^[A-HJ-NPR-Z0-9]{17}$"#, options: .regularExpression) != nil
            && vin != "00000000000000000"
    }

    private static func vinFromHexBytes(_ hex: String) -> String? {
        let chars = Array(hex)
        var decoded = ""
        var offset = 0
        while offset + 1 < chars.count && decoded.count < 17 {
            let byteText = String(chars[offset...offset + 1])
            if let byte = UInt8(byteText, radix: 16), (0x20...0x7E).contains(byte),
               let scalar = UnicodeScalar(byte) {
                decoded.append(Character(scalar))
            }
            offset += 2
        }
        return isValidVIN(decoded) ? decoded : nil
    }

    static func parseDTCs(_ rawResponse: String, isPending: Bool = false) -> [DiagnosticTroubleCode] {
        let cleaned = rawResponse.replacingOccurrences(of: #"\s+"#, with: " ", options: .regularExpression).trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleaned.localizedCaseInsensitiveContains("NODATA"),
              !cleaned.localizedCaseInsensitiveContains("NO DATA"),
              !cleaned.localizedCaseInsensitiveContains("ERROR") else { return [] }
        let prefix = isPending ? "47" : "43"
        var bytes: [String]
        if cleaned.contains(" ") {
            bytes = cleaned.split(separator: " ").map(String.init).filter { $0.count == 2 && Int($0, radix: 16) != nil }
        } else {
            let compact = cleaned.filter { !$0.isWhitespace }
            bytes = stride(from: 0, to: compact.count - compact.count % 2, by: 2).map { offset in
                let start = compact.index(compact.startIndex, offsetBy: offset)
                let end = compact.index(start, offsetBy: 2)
                return String(compact[start..<end])
            }.filter { Int($0, radix: 16) != nil }
        }
        var index = 2
        while index < bytes.count {
            if bytes[index].uppercased() == prefix {
                bytes.remove(at: index)
                if index < bytes.count { bytes.remove(at: index) }
            } else {
                index += 1
            }
        }
        var codes: [DiagnosticTroubleCode] = []
        var i = 0
        while i + 1 < bytes.count {
            if i == 0 && bytes[i].uppercased() == prefix {
                i += 2
                continue
            }
            if let first = Int(bytes[i], radix: 16), let second = Int(bytes[i + 1], radix: 16),
               first != 0 || second != 0 {
                let family = ["P", "C", "B", "U"][(first >> 6) & 0x03]
                let digit1 = (first >> 4) & 0x03
                let digit2 = String(first & 0x0F, radix: 16).uppercased()
                let digit3 = String((second >> 4) & 0x0F, radix: 16).uppercased()
                let digit4 = String(second & 0x0F, radix: 16).uppercased()
                codes.append(DiagnosticTroubleCode(code: "\(family)\(digit1)\(digit2)\(digit3)\(digit4)", isPending: isPending))
            }
            i += 2
        }
        return codes
    }

    private static func extractBytes(_ response: String, count: Int) -> [Int] {
        let hex = Array(response)
        let payloadStart = min(4, hex.count)
        guard payloadStart < hex.count else { return [] }
        let payload = Array(hex[payloadStart...])
        var values: [Int] = []
        var offset = 0
        while offset + 1 < payload.count && values.count < count {
            let pair = String(payload[offset...offset + 1])
            guard let value = Int(pair, radix: 16) else { break }
            values.append(value)
            offset += 2
        }
        return values
    }
}

struct DiagnosticTroubleCode: Equatable, Identifiable {
    let code: String
    let isPending: Bool
    var id: String { "\(isPending ? "pending" : "stored")-\(code)" }
}
