import SwiftUI

struct ContentView: View {
    var body: some View {
        TabView {
            DashboardView()
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
    var body: some View {
        NavigationStack {
            List {
                Section("Connection") {
                    Label("Not connected", systemImage: "cable.connector.slash")
                        .foregroundStyle(.secondary)
                    Text("Connect a compatible OBD-II adapter to begin.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
                Section("Quick access") {
                    NavigationLink("Live data", destination: LiveDataView())
                    NavigationLink("Vehicle diagnostics", destination: DiagnosticsView())
                    NavigationLink("Deep scan", destination: PlaceholderDetail(title: "Deep scan"))
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
