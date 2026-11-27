import SwiftUI

struct ContentView: View {
    @StateObject private var bluetooth = BluetoothLEManager()
    @StateObject private var vehicleStore = VehicleDiscoveryStore()
    @State private var selectedTab: String

    init() {
        let args = ProcessInfo.processInfo.arguments
        let requested = args.firstIndex(of: "-screenshot-tab").flatMap { index in
            args.indices.contains(index + 1) ? args[index + 1] : nil
        }
        _selectedTab = State(initialValue: requested ?? "dashboard")
    }

    var body: some View {
        TabView(selection: $selectedTab) {
            DashboardView(bluetooth: bluetooth, vehicleStore: vehicleStore)
                .tabItem { Label("Dashboard", systemImage: "gauge.with.dots.needle.67percent") }
                .tag("dashboard")
            VehiclesView(store: vehicleStore)
                .tabItem { Label("Vehicles", systemImage: "car.side") }
                .tag("vehicles")
            LiveDataView(bluetooth: bluetooth)
                .tabItem { Label("Live Data", systemImage: "waveform.path.ecg") }
                .tag("live-data")
            DiagnosticsView(bluetooth: bluetooth, vehicleStore: vehicleStore)
                .tabItem { Label("Diagnostics", systemImage: "stethoscope") }
                .tag("diagnostics")
            MoreView()
                .tabItem { Label("More", systemImage: "ellipsis.circle") }
                .tag("more")
        }
        .tint(.blue)
    }
}

private struct DashboardView: View {
    @ObservedObject var bluetooth: BluetoothLEManager
    @ObservedObject var vehicleStore: VehicleDiscoveryStore

    var body: some View {
        NavigationStack {
            List {
                Section("Adapter connection") {
                    HStack {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(bluetooth.connectedName ?? "No adapter connected")
                                .font(.headline)
                            Text(bluetooth.status)
                                .font(.footnote)
                                .foregroundStyle(.secondary)
                        }
                        Spacer()
                        if bluetooth.isReadyForELM {
                            Image(systemName: "checkmark.circle.fill")
                                .foregroundStyle(.green)
                                .accessibilityLabel("BLE UART characteristics ready")
                        }
                    }
                    if bluetooth.isInitialized {
                        LabeledContent("VIN", value: bluetooth.vehicleVIN ?? bluetooth.vinStatus)
                    }
                    if let session = bluetooth.activeSession {
                        Section("Session adapter fingerprint") {
                            LabeledContent("Transport", value: session.transport)
                            LabeledContent("Adapter", value: session.adapterName)
                            LabeledContent("Bluetooth identifier", value: session.peripheralIdentifier)
                            LabeledContent("MAC address", value: session.macAddress ?? "Not exposed by iOS")
                            LabeledContent("Manufacturer data", value: session.manufacturerDataHex.isEmpty ? "Not advertised" : session.manufacturerDataHex)
                            LabeledContent("Services", value: session.serviceUUIDs.isEmpty ? "Not advertised" : session.serviceUUIDs.joined(separator: ", "))
                            LabeledContent("ATI", value: session.atiResponse.isEmpty ? "No response" : session.atiResponse)
                            LabeledContent("AT@1", value: session.adapterDescription.isEmpty ? "No response" : session.adapterDescription)
                            LabeledContent("Model", value: session.adapterModel.isEmpty ? "Not identified" : session.adapterModel)
                            LabeledContent("Manufacturer", value: session.adapterManufacturer ?? "Not identified")
                            LabeledContent("Identification confidence", value: session.adapterModelConfidence.capitalized)
                            if let evidence = session.adapterIdentificationEvidence, !evidence.isEmpty {
                                LabeledContent("Identification evidence", value: evidence)
                            }
                        }
                    }
                    if bluetooth.connectedName == nil {
                        Button {
                            bluetooth.startScan()
                        } label: {
                            Label(bluetooth.isScanning ? "Scanning…" : "Scan for BLE adapters",
                                  systemImage: "dot.radiowaves.left.and.right")
                        }
                        .disabled(bluetooth.isScanning)
                    } else {
                        Button(role: .destructive) {
                            bluetooth.disconnect()
                        } label: {
                            Label("Disconnect", systemImage: "xmark.circle")
                        }
                    }
                    if bluetooth.isScanning {
                        Button("Stop scanning", role: .cancel) { bluetooth.stopScan() }
                    }
                    if !bluetooth.adapters.isEmpty && bluetooth.connectedName == nil {
                        ForEach(bluetooth.adapters) { adapter in
                            Button {
                                bluetooth.connect(to: adapter)
                            } label: {
                                HStack {
                                    VStack(alignment: .leading) {
                                        Text(adapter.name)
                                        Text(adapter.id.uuidString)
                                            .font(.caption2)
                                            .foregroundStyle(.secondary)
                                    }
                                    Spacer()
                                    Text("\(adapter.rssi) dBm")
                                        .font(.caption.monospacedDigit())
                                        .foregroundStyle(.secondary)
                                }
                            }
                        }
                    }
                }
                Section("Quick access") {
                    NavigationLink("Live data", destination: LiveDataView(bluetooth: bluetooth))
                    NavigationLink("Vehicle diagnostics", destination: DiagnosticsView(bluetooth: bluetooth, vehicleStore: vehicleStore))
                    NavigationLink("Deep scan", destination: DeepScanView(bluetooth: bluetooth, vehicleStore: vehicleStore))
                }
                Section {
                    Text("BLE UART adapters are supported. Bluetooth Classic SPP adapters generally require an Apple-supported accessory/MFi path and cannot be accessed through Core Bluetooth.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }
            .navigationTitle("OBDII Tools")
        }
        .task(id: bluetooth.isInitialized) {
            guard bluetooth.isInitialized else { return }
            if let vin = await bluetooth.identifyVehicle() {
                vehicleStore.recordScan(
                    vin: vin,
                    items: [],
                    ecuCount: 0,
                    make: VehicleDiscoveryStore.makeFromVIN(vin)
                )
            }
        }
    }
}

private struct VehiclesView: View {
    @ObservedObject var store: VehicleDiscoveryStore

    var body: some View {
        NavigationStack {
            List {
                if store.profiles.isEmpty {
                    ContentUnavailableView(
                        "No saved vehicles",
                        systemImage: "car.side",
                        description: Text("Profiles are saved when a vehicle scan discovers a VIN or records diagnostic data.")
                    )
                } else {
                    ForEach(store.profiles) { profile in
                        NavigationLink {
                            VehicleProfileDetail(profile: profile)
                        } label: {
                            VStack(alignment: .leading, spacing: 5) {
                                Text([profile.year, profile.make, profile.model]
                                    .filter { !$0.isEmpty }.joined(separator: " ")
                                    .ifEmpty(profile.make.isEmpty ? "Unverified vehicle" : profile.make))
                                    .font(.headline)
                                Text(profile.vin.isEmpty ? "VIN not verified" : profile.vin)
                                    .font(.caption.monospaced())
                                    .foregroundStyle(.secondary)
                                HStack(spacing: 12) {
                                    Label("\(profile.ecuCount) ECUs", systemImage: "cpu")
                                    Label("\(profile.knownCount) known", systemImage: "checkmark.circle")
                                    Label("\(profile.unknownCount) unknown", systemImage: "questionmark.circle")
                                }
                                .font(.caption2)
                                .foregroundStyle(.secondary)
                            }
                            .padding(.vertical, 3)
                        }
                        .swipeActions {
                            Button("Delete", role: .destructive) {
                                store.deleteProfile(id: profile.id)
                            }
                        }
                    }
                }
            }
            .navigationTitle("Vehicles")
            .toolbar {
                if !store.profiles.isEmpty { EditButton() }
            }
        }
    }
}

private struct VehicleProfileDetail: View {
    let profile: VehicleDiscoveryProfile

    var body: some View {
        List {
            Section("Vehicle") {
                LabeledContent("Make", value: profile.make.isEmpty ? "Unknown" : profile.make)
                LabeledContent("Model", value: profile.model.isEmpty ? "Unknown" : profile.model)
                LabeledContent("Year", value: profile.year.isEmpty ? "Unknown" : profile.year)
                LabeledContent("VIN", value: profile.vin.isEmpty ? "Unverified" : profile.vin)
                LabeledContent("ECUs discovered", value: "\(profile.ecuCount)")
                LabeledContent("Last scanned", value: Date(timeIntervalSince1970: Double(profile.lastSeenMs) / 1000).formatted(date: .abbreviated, time: .shortened))
            }
            Section("Discovery summary") {
                LabeledContent("Total entries", value: "\(profile.items.count)")
                LabeledContent("Known", value: "\(profile.knownCount)")
                LabeledContent("Unknown", value: "\(profile.unknownCount)")
            }
            Section("Discovered items") {
                if profile.items.isEmpty {
                    Text("No discovery entries recorded.")
                        .foregroundStyle(.secondary)
                } else {
                    ForEach(profile.items) { item in
                        NavigationLink {
                            VehicleDiscoveryItemDetail(item: item)
                        } label: {
                            VStack(alignment: .leading, spacing: 3) {
                                Text(item.name.isEmpty ? item.key : item.name)
                                Text("\(item.key) · ECU \(item.ecuAddress) · \(item.category)")
                                    .font(.caption2)
                                    .foregroundStyle(.secondary)
                                Text(item.known ? "Known" : "Unknown")
                                    .font(.caption2)
                                    .foregroundStyle(item.known ? .green : .secondary)
                            }
                        }
                    }
                }
            }
        }
        .navigationTitle(profile.make.isEmpty ? "Vehicle profile" : profile.make)
        .navigationBarTitleDisplayMode(.inline)
    }
}

private struct VehicleDiscoveryItemDetail: View {
    let item: VehicleDiscoveryItem

    var body: some View {
        List {
            LabeledContent("Name", value: item.name.isEmpty ? "—" : item.name)
            LabeledContent("Key", value: item.key)
            LabeledContent("ECU address", value: item.ecuAddress)
            LabeledContent("Category", value: item.category)
            LabeledContent("Status", value: item.status)
            LabeledContent("Classification", value: item.known ? "Known" : "Unknown")
            Section("Raw response") {
                Text(item.rawResponse.isEmpty ? "No raw response saved" : item.rawResponse)
                    .font(.system(.body, design: .monospaced))
                    .textSelection(.enabled)
            }
        }
        .navigationTitle("Discovery detail")
        .navigationBarTitleDisplayMode(.inline)
    }
}

private extension String {
    func ifEmpty(_ fallback: String) -> String { isEmpty ? fallback : self }
}

private struct LiveDataView: View {
    private struct PIDRow: Identifiable {
        let command: String
        let title: String
        let unit: String
        var id: String { command }
    }

    @ObservedObject var bluetooth: BluetoothLEManager
    @State private var values: [String: Double] = [:]
    @State private var isMonitoring = false
    @State private var monitorTask: Task<Void, Never>?
    @State private var lastUpdated: Date?
    @State private var slowPIDIndex = 0

    private let fastPIDs = [
        PIDRow(command: "010C", title: "Engine RPM", unit: "rpm"),
        PIDRow(command: "010D", title: "Vehicle speed", unit: "km/h"),
        PIDRow(command: "0111", title: "Throttle position", unit: "%"),
        PIDRow(command: "0105", title: "Coolant temperature", unit: "°C"),
        PIDRow(command: "0104", title: "Calculated engine load", unit: "%"),
        PIDRow(command: "0110", title: "Mass air flow", unit: "g/s")
    ]
    private let slowPIDs = [
        PIDRow(command: "010F", title: "Intake air temperature", unit: "°C"),
        PIDRow(command: "010E", title: "Timing advance", unit: "°"),
        PIDRow(command: "012F", title: "Fuel level", unit: "%"),
        PIDRow(command: "0146", title: "Ambient air temperature", unit: "°C"),
        PIDRow(command: "015C", title: "Engine oil temperature", unit: "°C"),
        PIDRow(command: "0133", title: "Barometric pressure", unit: "kPa"),
        PIDRow(command: "010A", title: "Fuel pressure", unit: "kPa"),
        PIDRow(command: "0106", title: "Short-term fuel trim B1", unit: "%"),
        PIDRow(command: "0107", title: "Long-term fuel trim B1", unit: "%"),
        PIDRow(command: "0108", title: "Short-term fuel trim B2", unit: "%"),
        PIDRow(command: "0109", title: "Long-term fuel trim B2", unit: "%"),
        PIDRow(command: "0114", title: "O2 voltage B1S1", unit: "V"),
        PIDRow(command: "0115", title: "O2 voltage B1S2", unit: "V"),
        PIDRow(command: "0118", title: "O2 voltage B2S1", unit: "V"),
        PIDRow(command: "0119", title: "O2 voltage B2S2", unit: "V")
    ]

    var body: some View {
        List {
            Section("Live monitoring") {
                LabeledContent("Adapter", value: bluetooth.connectedName ?? "Not connected")
                LabeledContent("Status", value: bluetooth.isReadyForELM ? (isMonitoring ? "Monitoring" : "Ready") : "Not ready")
                if let lastUpdated {
                    LabeledContent("Last update", value: lastUpdated.formatted(date: .omitted, time: .standard))
                }
                Button {
                    if isMonitoring {
                        stopMonitoring()
                    } else {
                        startMonitoring()
                    }
                } label: {
                    Label(isMonitoring ? "Stop live data" : "Start live data",
                          systemImage: isMonitoring ? "stop.circle" : "waveform")
                }
                .disabled(!bluetooth.isReadyForELM)
                Text("Reads standard SAE Mode 01 PIDs. Values appear only when the vehicle responds; unsupported PIDs remain blank.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
            Section("Engine and vehicle") {
                ForEach(fastPIDs) { pid in
                    pidRow(pid)
                }
            }
            Section("Additional parameters") {
                ForEach(slowPIDs) { pid in
                    pidRow(pid)
                }
            }
        }
        .navigationTitle("Live Data")
        .onDisappear { stopMonitoring() }
    }

    @ViewBuilder
    private func pidRow(_ pid: PIDRow) -> some View {
        HStack {
            VStack(alignment: .leading, spacing: 3) {
                Text(pid.title)
                Text(pid.command).font(.caption2.monospaced()).foregroundStyle(.secondary)
            }
            Spacer()
            if let value = values[pid.command] {
                Text("\(value.formatted(.number.precision(.fractionLength(0...1)))) \(pid.unit)")
                    .font(.body.monospacedDigit())
            } else {
                Text("—").foregroundStyle(.tertiary)
            }
        }
    }

    private func startMonitoring() {
        guard !isMonitoring, bluetooth.isReadyForELM else { return }
        isMonitoring = true
        monitorTask = Task {
            while !Task.isCancelled {
                for pid in fastPIDs {
                    if Task.isCancelled { break }
                    let raw = await bluetooth.query(pid.command, timeout: 1.0)
                    if Task.isCancelled { break }
                    if let value = ELM327Protocol.parseResponse(pid.command, rawResponse: raw) {
                        values[pid.command] = value
                    }
                }
                if Task.isCancelled { break }
                let pid = slowPIDs[slowPIDIndex]
                let raw = await bluetooth.query(pid.0, timeout: 1.0)
                if let value = ELM327Protocol.parseResponse(pid.0, rawResponse: raw) {
                    values[pid.0] = value
                }
                slowPIDIndex = (slowPIDIndex + 1) % slowPIDs.count
                lastUpdated = Date()
                try? await Task.sleep(nanoseconds: 150_000_000)
            }
            isMonitoring = false
            monitorTask = nil
        }
    }

    private func stopMonitoring() {
        monitorTask?.cancel()
        monitorTask = nil
        isMonitoring = false
    }
}

private struct DiagnosticsView: View {
    @ObservedObject var bluetooth: BluetoothLEManager
    @ObservedObject var vehicleStore: VehicleDiscoveryStore

    var body: some View {
        NavigationStack {
            List {
                NavigationLink("Trouble codes", destination: PlaceholderDetail(title: "Trouble codes"))
                NavigationLink("Readiness monitors", destination: PlaceholderDetail(title: "Readiness monitors"))
                NavigationLink("Freeze frame", destination: PlaceholderDetail(title: "Freeze frame"))
                NavigationLink("Deep scan", destination: DeepScanView(bluetooth: bluetooth, vehicleStore: vehicleStore))
                NavigationLink("UDS diagnostics", destination: PlaceholderDetail(title: "UDS diagnostics"))
                NavigationLink("CAN monitor", destination: PlaceholderDetail(title: "CAN monitor"))
            }
            .navigationTitle("Diagnostics")
        }
    }
}

private struct MoreView: View {
    var body: some View {
        NavigationStack {
            List {
                NavigationLink("Sessions and logs", destination: PlaceholderDetail(title: "Sessions and logs"))
                NavigationLink("Custom PIDs", destination: PlaceholderDetail(title: "Custom PIDs"))
                NavigationLink("Settings", destination: PlaceholderDetail(title: "Settings"))
                NavigationLink("Vehicle discovery", destination: PlaceholderDetail(title: "Vehicle discovery"))
            }
            .navigationTitle("More")
        }
    }
}

private struct PlaceholderDetail: View {
    let title: String

    var body: some View {
        ContentUnavailableView(
            title,
            systemImage: "hammer",
            description: Text("This feature is planned. The Android implementation is the functional reference.")
        )
        .navigationTitle(title)
        .navigationBarTitleDisplayMode(.inline)
    }
}

#Preview {
    ContentView()
}
