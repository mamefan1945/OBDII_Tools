import SwiftUI
import Foundation

/// Unified read-only scan flow for iOS. Manufacturer routines are selected only
/// after VIN decoding confirms an exact supported application.
struct DeepScanView: View {
    @ObservedObject var bluetooth: BluetoothLEManager
    @ObservedObject var vehicleStore: VehicleDiscoveryStore

    @State private var isScanning = false
    @State private var phase = "Ready to scan"
    @State private var progress = 0
    @State private var total = 0
    @State private var vehicleDescription = ""
    @State private var ecuAddresses: [String] = []
    @State private var findings: [VehicleDiscoveryItem] = []
    @State private var errorMessage: String?
    @State private var scanTask: Task<Void, Never>?

    private let mazdaDIDs: [(String, String)] = [
        ("1E12", "Selected transmission gear"),
        ("1E1C", "Transmission fluid temperature"),
        ("1674", "Transmission temperature alternate (catalogue lead)"),
        ("1E3F", "Transmission temperature alternate (catalogue lead)"),
        ("11B3", "Transmission gear alternate (catalogue lead)"),
        ("11BD", "Transmission temperature alternate (catalogue lead)"),
        ("163A", "Torque converter input torque (catalogue lead)"),
        ("DD01", "Transmission odometer (catalogue lead)")
    ]

    var body: some View {
        List {
            Section("Vehicle") {
                LabeledContent("VIN", value: bluetooth.vehicleVIN ?? "Not available")
                LabeledContent("Identification", value: vehicleDescription.isEmpty ? bluetooth.vinStatus : vehicleDescription)
                LabeledContent("Connection", value: bluetooth.isReadyForELM ? "Connected" : "Not ready")
            }

            Section("Deep scan") {
                Text(phase)
                    .font(.subheadline)
                if isScanning {
                    ProgressView(value: Double(progress), total: Double(max(total, 1)))
                    Text("\(progress) / \(total)")
                        .font(.caption.monospacedDigit())
                        .foregroundStyle(.secondary)
                    Button("Cancel scan", role: .destructive) {
                        scanTask?.cancel()
                        phase = "Cancelling…"
                    }
                } else {
                    Button("Start Deep Scan") {
                        scanTask = Task { await runDeepScan() }
                    }
                    .disabled(!bluetooth.isReadyForELM)
                }
                if let errorMessage {
                    Text(errorMessage).foregroundStyle(.red)
                }
            }

            Section("Responding ECUs (\(ecuAddresses.count))") {
                if ecuAddresses.isEmpty {
                    Text("No scan results yet").foregroundStyle(.secondary)
                } else {
                    ForEach(ecuAddresses, id: \.self) { address in
                        Text("ECU 0x\(address)")
                            .font(.system(.body, design: .monospaced))
                    }
                }
            }

            Section("Read-only findings (\(findings.count))") {
                if findings.isEmpty {
                    Text("No findings recorded").foregroundStyle(.secondary)
                } else {
                    ForEach(findings) { item in
                        VStack(alignment: .leading, spacing: 4) {
                            Text(item.name).font(.headline)
                            Text("ECU 0x\(item.ecuAddress) · \(item.category)")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                            Text(item.status)
                                .font(.caption)
                            if !item.rawResponse.isEmpty {
                                Text(item.rawResponse)
                                    .font(.system(.caption2, design: .monospaced))
                                    .textSelection(.enabled)
                            }
                        }
                    }
                }
            }
        }
        .navigationTitle("Deep Scan")
        .navigationBarTitleDisplayMode(.inline)
    }

    @MainActor
    private func runDeepScan() async {
        guard !isScanning, bluetooth.isReadyForELM else { return }
        isScanning = true
        errorMessage = nil
        ecuAddresses = []
        findings = []
        progress = 0
        let addresses = (0x700...0x77F).map { String(format: "%03X", $0) }
            + (0x7E0...0x7E7).map { String(format: "%03X", $0) }
        total = addresses.count
        defer {
            isScanning = false
            scanTask = nil
        }
        // Restore the broadcast header even when the user cancels midway through a scan.
        defer {
            Task { @MainActor in
                _ = await bluetooth.query("ATSH 7DF", timeout: 0.8)
            }
        }

        let vin = bluetooth.vehicleVIN
        guard let vin, vin.count == 17 else {
            phase = "VIN unavailable — reconnect and allow vehicle identification before scanning"
            errorMessage = "A valid VIN is required to select vehicle-specific procedures safely."
            return
        }

        phase = "Decoding VIN and discovering ECUs…"
        let decoded = await decodeVIN(vin)
        guard !Task.isCancelled else { phase = "Cancelled"; return }
        vehicleDescription = [decoded.make, decoded.model, decoded.year].filter { !$0.isEmpty }.joined(separator: " ")
        var found: [String] = []
        var items: [VehicleDiscoveryItem] = []

        for (index, address) in addresses.enumerated() {
            guard !Task.isCancelled else { phase = "Cancelled"; return }
            _ = await bluetooth.query("ATSH \(address)", timeout: 0.7)
            guard !Task.isCancelled else { phase = "Cancelled"; return }
            let uds = await bluetooth.query("1001", timeout: 0.8)
            guard !Task.isCancelled else { phase = "Cancelled"; return }
            var response = uds
            let compactUDS = uds.uppercased().filter { $0.isHexDigit }
            if !compactUDS.contains("5001") && !compactUDS.contains("7F10") {
                response = await bluetooth.query("0100", timeout: 0.8)
                guard !Task.isCancelled else { phase = "Cancelled"; return }
            }
            let compact = response.uppercased().filter { $0.isHexDigit }
            let responds = compact.contains("5001") || compact.contains("7F10")
                || compact.contains("4100") || compact.contains("7F01")
            if responds {
                found.append(address)
                items.append(VehicleDiscoveryItem(
                    key: "ECU:\(address)", name: "Responding control module",
                    ecuAddress: address, category: "ECU discovery", known: false,
                    status: "Responded to read-only diagnostic probe",
                    rawResponse: String(response.prefix(180))
                ))
            }
            progress = index + 1
            phase = "Discovering ECUs — \(index + 1) / \(addresses.count)"
        }
        _ = await bluetooth.query("ATSH 7DF", timeout: 0.8)
        ecuAddresses = found

        guard !Task.isCancelled else { phase = "Cancelled"; return }
        phase = "Reading standard OBD support…"
        for address in (["7DF"] + found).prefix(24) {
            guard !Task.isCancelled else { phase = "Cancelled"; return }
            _ = await bluetooth.query("ATSH \(address)", timeout: 0.7)
            let raw = await bluetooth.query("0100", timeout: 1.2)
            guard !Task.isCancelled else { phase = "Cancelled"; return }
            if !raw.isEmpty {
                items.append(VehicleDiscoveryItem(
                    key: "\(address):01:00", name: "Supported Mode 01 PID bitmap",
                    ecuAddress: address, category: "Mode 01", known: true,
                    status: raw.localizedCaseInsensitiveContains("4100") ? "Positive response" : "No positive support response",
                    rawResponse: String(raw.prefix(180))
                ))
            }
        }
        _ = await bluetooth.query("ATSH 7DF", timeout: 0.8)

        let isConfirmed2016CX5 = decoded.make.caseInsensitiveCompare("Mazda") == .orderedSame
            && decoded.model.localizedCaseInsensitiveContains("CX-5")
            && decoded.year == "2016"
        if isConfirmed2016CX5 {
            phase = "2016 CX-5 confirmed — reading compatible TCM identifiers…"
            for (did, name) in mazdaDIDs {
                guard !Task.isCancelled else { phase = "Cancelled"; return }
                let raw = await bluetooth.queryUDS(ecuAddress: "7E1", did: did)
                guard !Task.isCancelled else { phase = "Cancelled"; return }
                let compact = raw.uppercased().filter { $0.isHexDigit }
                let positive = compact.contains("62" + did)
                items.append(VehicleDiscoveryItem(
                    key: "7E1:22:\(did)", name: name, ecuAddress: "7E1",
                    category: "Mazda CX-5 TCM UDS DID",
                    known: did == "1E12" || did == "1E1C",
                    status: positive ? (did == "1E12" || did == "1E1C" ? "Positive response — known DID" : "Positive response — raw only") : "No positive response",
                    rawResponse: String(raw.prefix(220))
                ))
            }
        }

        _ = await bluetooth.query("ATSH 7DF", timeout: 0.8)
        guard !Task.isCancelled else { phase = "Cancelled"; return }
        findings = items
        vehicleStore.recordScan(
            vin: vin, items: items, ecuCount: found.count,
            make: decoded.make, model: decoded.model, year: decoded.year
        )
        phase = isConfirmed2016CX5
            ? "Complete — standard scan and 2016 CX-5 TCM checks finished"
            : "Complete — standard scan finished; no matching manufacturer procedure"
    }

    private func decodeVIN(_ vin: String) async -> (make: String, model: String, year: String) {
        guard let url = URL(string: "https://vpic.nhtsa.dot.gov/api/vehicles/decodevin/\(vin)?format=json"),
              let (data, _) = try? await URLSession.shared.data(from: url),
              let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let rows = json["Results"] as? [[String: Any]] else {
            return (VehicleDiscoveryStore.makeFromVIN(vin), "", "")
        }
        func value(_ variable: String) -> String {
            rows.first(where: { ($0["Variable"] as? String) == variable })?["Value"] as? String ?? ""
        }
        return (value("Make"), value("Model"), value("Model Year"))
    }
}
