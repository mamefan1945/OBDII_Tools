import Foundation

struct AdapterIdentification {
    let manufacturer: String
    let model: String
    let confidence: String
    let evidence: String
}

/// Conservative matching of adapter self-reported identifiers.
/// Bluetooth names and ELM responses can be spoofed; an explicit AT@1
/// product string is marked confirmed, while advertisement-only matches are probable.
enum AdapterFingerprintClassifier {
    private struct Rule {
        let manufacturer: String
        let model: String
        let aliases: [String]
    }

    private static let rules: [Rule] = [
        Rule(manufacturer: "VEEPEAK", model: "OBDCheck BLE+", aliases: ["OBDCHECK BLE+", "OBDCHECK BLE PLUS"]),
        Rule(manufacturer: "VEEPEAK", model: "OBDCheck BLE", aliases: ["OBDCHECK BLE"]),
        Rule(manufacturer: "OBDLink", model: "MX+", aliases: ["OBDLINK MX+", "OBDLINK MX PLUS"]),
        Rule(manufacturer: "OBDLink", model: "CX", aliases: ["OBDLINK CX"]),
        Rule(manufacturer: "OBDLink", model: "LX", aliases: ["OBDLINK LX"]),
        Rule(manufacturer: "OBDLink", model: "EX", aliases: ["OBDLINK EX"]),
        Rule(manufacturer: "Vgate", model: "iCar Pro BLE 4.0", aliases: ["VGATE ICAR PRO", "ICAR PRO BLE"]),
        Rule(manufacturer: "Vgate", model: "iCar Pro Wi-Fi", aliases: ["VGATE ICAR PRO WIFI", "ICAR PRO WI-FI"]),
        Rule(manufacturer: "vLinker", model: "MC+", aliases: ["VLINKER MC+", "VLINKER MC PLUS"]),
        Rule(manufacturer: "vLinker", model: "FD+", aliases: ["VLINKER FD+", "VLINKER FD PLUS"]),
        Rule(manufacturer: "vLinker", model: "BM+", aliases: ["VLINKER BM+", "VLINKER BM PLUS"]),
        Rule(manufacturer: "vLinker", model: "FS", aliases: ["VLINKER FS"]),
        Rule(manufacturer: "Carista", model: "EVO", aliases: ["CARISTA EVO"]),
        Rule(manufacturer: "BlueDriver", model: "BlueDriver", aliases: ["BLUEDRIVER"]),
        Rule(manufacturer: "BAFX Products", model: "Bluetooth OBDII", aliases: ["BAFX"])
    ]

    static func identify(advertisedName: String, ati: String, description: String,
                         manufacturerDataHex: String = "") -> AdapterIdentification {
        let sources: [(String, String)] = [
            ("advertised name", normalize(advertisedName)),
            ("ATI", normalize(ati)),
            ("AT@1", normalize(description))
        ]

        for rule in rules {
            guard let alias = rule.aliases.first(where: { candidate in
                let token = normalize(candidate)
                return sources.contains(where: { $0.1.contains(token) })
            }) else { continue }

            let token = normalize(alias)
            let evidence = sources.filter { $0.1.contains(token) }.map { $0.0 }
            return AdapterIdentification(
                manufacturer: rule.manufacturer,
                model: rule.model,
                confidence: normalize(description).contains(token) ? "confirmed" : "probable",
                evidence: evidence.joined(separator: ", ").isEmpty ? "known product signature" : evidence.joined(separator: ", ")
            )
        }

        let all = sources.map { $0.1 }.joined(separator: " ")
        let vendor: String
        if all.contains("VEEPEAK") { vendor = "VEEPEAK" }
        else if all.contains("OBDLINK") { vendor = "OBDLink" }
        else if all.contains("VGATE") { vendor = "Vgate" }
        else if all.contains("VLINKER") { vendor = "vLinker" }
        else if all.contains("CARISTA") { vendor = "Carista" }
        else if all.contains("BLUEDRIVER") { vendor = "BlueDriver" }
        else if all.contains("BAFX") { vendor = "BAFX Products" }
        else { vendor = "" }

        var evidence: [String] = []
        if !vendor.isEmpty { evidence.append("brand string in adapter response/name") }
        if !manufacturerDataHex.isEmpty { evidence.append("manufacturer data captured (not yet mapped)") }
        return AdapterIdentification(
            manufacturer: vendor,
            model: "",
            confidence: "unknown",
            evidence: evidence.joined(separator: ", ")
        )
    }

    private static func normalize(_ value: String) -> String {
        value.uppercased().replacingOccurrences(
            of: "[^A-Z0-9+]+", with: " ", options: .regularExpression
        ).trimmingCharacters(in: .whitespacesAndNewlines)
    }
}
