import SwiftUI

struct ContentView: View {
    @StateObject private var bluetooth = BluetoothLEManager()

    var body: some View {
        TabView {
            DashboardView(bluetooth: bluetooth)
                .tabItem { Label("Dashboard", systemImage: "gauge.with.dots.needle.67percent") }
            VehiclesView()
                .tabItem { Label("Vehicles", systemImage: "car.side") }
            LiveDataView()
                .tabItem { Label("Live Data", systemImage: "waveform.path.ecg") }
            DiagnosticsView()
                .tabItem { Label("Diagnostics", systemImage: "stethoscope") }
            MoreView()
                .tabItem { Label("More", systemImage: "ellipsis.circle") }
        }
        .tint(.blue)
    }
}

private struct DashboardView: View {
    @ObservedObject var bluetooth: BluetoothLEManager

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
                    NavigationLink("Live data", destination: LiveDataView())
                    NavigationLink("Vehicle diagnostics", destination: DiagnosticsView())
                    NavigationLink("Deep scan", destination: PlaceholderDetail(title: "Deep scan"))
                }
                Section {
                    Text("This initial connection layer discovers BLE UART-style adapters. Bluetooth Classic SPP adapters are not generally accessible to third-party iOS apps. Actual ELM327 command/query transport is the next implementation step.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }
            .navigationTitle("OBDII Tools")
        }
    }
}

private struct VehiclesView: View {
    var body: some View {
        NavigationStack {
            ContentUnavailableView(
                "No saved vehicles",
                systemImage: "car.side",
                description: Text("Vehicle discovery and saved profiles will be implemented here.")
            )
            .navigationTitle("Vehicles")
        }
    }
}

private struct LiveDataView: View {
    var body: some View {
        NavigationStack {
            ContentUnavailableView(
                "No live data",
                systemImage: "waveform.path.ecg",
                description: Text("Connect to an OBD-II adapter to view supported vehicle data.")
            )
            .navigationTitle("Live Data")
        }
    }
}

private struct DiagnosticsView: View {
    var body: some View {
        NavigationStack {
            List {
                NavigationLink("Trouble codes", destination: PlaceholderDetail(title: "Trouble codes"))
                NavigationLink("Readiness monitors", destination: PlaceholderDetail(title: "Readiness monitors"))
                NavigationLink("Freeze frame", destination: PlaceholderDetail(title: "Freeze frame"))
                NavigationLink("Deep scan", destination: PlaceholderDetail(title: "Deep scan"))
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
