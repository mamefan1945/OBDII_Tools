import Combine
import Foundation

struct VehicleDiscoveryItem: Codable, Identifiable, Equatable {
    var key: String
    var name: String
    var ecuAddress: String
    var category: String
    var known: Bool
    var status: String
    var rawResponse: String = ""
    var updatedAtMs: Int64 = Int64(Date().timeIntervalSince1970 * 1000)
    var id: String { key }
}

struct VehicleDiscoveryProfile: Codable, Identifiable, Equatable {
    var id: String
    var vin: String
    var make: String
    var model: String
    var year: String
    var lastSeenMs: Int64
    var ecuCount: Int
    var items: [VehicleDiscoveryItem]

    var knownCount: Int { items.filter(\.known).count }
    var unknownCount: Int { items.filter { !$0.known }.count }
}

final class VehicleDiscoveryStore: ObservableObject {
    @Published private(set) var profiles: [VehicleDiscoveryProfile]
    private let defaults: UserDefaults
    private let storageKey = "vehicle_discovery_profiles"

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        if let data = defaults.data(forKey: storageKey),
           let decoded = try? JSONDecoder().decode([VehicleDiscoveryProfile].self, from: data) {
            profiles = decoded.sorted { $0.lastSeenMs > $1.lastSeenMs }
        } else {
            profiles = []
        }
    }

    func deleteProfile(id: String) {
        persist(profiles.filter { $0.id != id })
    }

    func recordScan(vin: String?, items: [VehicleDiscoveryItem], ecuCount: Int,
                    make: String = "", model: String = "", year: String = "") {
        let candidate = (vin ?? "").uppercased().filter { $0.isLetter || $0.isNumber }
        let validVIN = candidate.range(of: #"^[A-HJ-NPR-Z0-9]{17}$"#, options: .regularExpression) != nil
            && candidate != "00000000000000000" ? candidate : nil
        guard validVIN != nil || !items.isEmpty else { return }

        let id = validVIN ?? "UNVERIFIED-\(Int(Date().timeIntervalSince1970 * 1000))"
        let old = profiles.first { $0.id == id }
        var merged = Dictionary((old?.items ?? []).map { ($0.key, $0) }, uniquingKeysWith: { _, latest in latest })
        for item in items { merged[item.key] = item }
        let profile = VehicleDiscoveryProfile(
            id: id,
            vin: validVIN ?? "",
            make: !make.isEmpty ? make : (old?.make.isEmpty == false ? old!.make : Self.makeFromVIN(validVIN ?? "")),
            model: !model.isEmpty ? model : (old?.model ?? ""),
            year: !year.isEmpty ? year : (old?.year ?? ""),
            lastSeenMs: Int64(Date().timeIntervalSince1970 * 1000),
            ecuCount: max(old?.ecuCount ?? 0, ecuCount),
            items: merged.values.sorted { $0.key < $1.key }
        )
        persist((profiles.filter { $0.id != id } + [profile]).sorted { $0.lastSeenMs > $1.lastSeenMs })
    }

    func recordMazdaDIDs(_ items: [VehicleDiscoveryItem]) {
        guard let old = profiles.first(where: { $0.make.caseInsensitiveCompare("Mazda") == .orderedSame }) else { return }
        var merged = Dictionary(old.items.map { ($0.key, $0) }, uniquingKeysWith: { _, latest in latest })
        for item in items { merged[item.key] = item }
        var updated = old
        updated.lastSeenMs = Int64(Date().timeIntervalSince1970 * 1000)
        updated.items = merged.values.sorted { $0.key < $1.key }
        persist((profiles.filter { $0.id != old.id } + [updated]).sorted { $0.lastSeenMs > $1.lastSeenMs })
    }

    private func persist(_ updated: [VehicleDiscoveryProfile]) {
        profiles = updated
        if let data = try? JSONEncoder().encode(updated) {
            defaults.set(data, forKey: storageKey)
        }
    }

    static func makeFromVIN(_ vin: String) -> String {
        switch String(vin.prefix(3)) {
        case "JM1", "JM3": return "Mazda"
        case "1N4", "1N6", "3N1", "JN1", "JN8": return "Nissan"
        case "1HG", "2HG", "JHM", "5J6": return "Honda"
        case "JT2", "JT3", "4T1", "5TD", "JTE": return "Toyota"
        case "WBA", "WBS": return "BMW"
        case "WVW", "3VW": return "Volkswagen"
        case "WAU": return "Audi"
        case "1FA", "1FM", "1FT", "3FA": return "Ford"
        default: return vin.count == 17 ? "VIN \(vin.prefix(3))" : "Unknown manufacturer"
        }
    }
}
