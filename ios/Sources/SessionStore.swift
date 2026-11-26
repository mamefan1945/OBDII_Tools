import Foundation
import Combine

struct DiagnosticSession: Codable, Identifiable {
    let id: UUID
    let startedAt: Date
    var endedAt: Date?
    let adapterName: String
    let transport: String
    let peripheralIdentifier: String
    let macAddress: String?
    let manufacturerDataHex: String
    let serviceUUIDs: [String]
    let rssi: Int
    var atiResponse: String
    var adapterDescription: String
    var adapterModel: String
    var adapterModelConfidence: String
    var vin: String?

    init(adapterName: String, peripheralIdentifier: String,
         manufacturerDataHex: String, serviceUUIDs: [String], rssi: Int,
         atiResponse: String, adapterDescription: String) {
        id = UUID()
        startedAt = Date()
        endedAt = nil
        self.adapterName = adapterName
        transport = "BLE"
        self.peripheralIdentifier = peripheralIdentifier
        macAddress = nil // CoreBluetooth does not expose the BLE MAC address.
        self.manufacturerDataHex = manufacturerDataHex
        self.serviceUUIDs = serviceUUIDs
        self.rssi = rssi
        self.atiResponse = atiResponse
        self.adapterDescription = adapterDescription
        adapterModel = ""
        adapterModelConfidence = "unknown"
        vin = nil
    }
}

final class SessionStore: ObservableObject {
    @Published private(set) var sessions: [DiagnosticSession] = []
    @Published private(set) var activeSession: DiagnosticSession?

    private let storageKey = "obdii.diagnosticSessions.v1"

    init() {
        if let data = UserDefaults.standard.data(forKey: storageKey),
           let saved = try? JSONDecoder().decode([DiagnosticSession].self, from: data) {
            sessions = saved
            activeSession = saved.first(where: { $0.endedAt == nil })
        }
    }

    func startSession(adapterName: String, peripheralIdentifier: String,
                      manufacturerDataHex: String, serviceUUIDs: [String],
                      rssi: Int, atiResponse: String, adapterDescription: String) {
        endActiveSession()
        let session = DiagnosticSession(
            adapterName: adapterName,
            peripheralIdentifier: peripheralIdentifier,
            manufacturerDataHex: manufacturerDataHex,
            serviceUUIDs: serviceUUIDs,
            rssi: rssi,
            atiResponse: atiResponse,
            adapterDescription: adapterDescription
        )
        activeSession = session
        sessions.insert(session, at: 0)
        persist()
    }

    func updateVIN(_ vin: String?) {
        guard var session = activeSession else { return }
        session.vin = vin
        replaceActive(session)
    }

    func endActiveSession() {
        guard var session = activeSession else { return }
        session.endedAt = Date()
        replaceActive(session)
        activeSession = nil
    }

    private func replaceActive(_ session: DiagnosticSession) {
        if let index = sessions.firstIndex(where: { $0.id == session.id }) {
            sessions[index] = session
        }
        if session.endedAt == nil { activeSession = session }
        persist()
    }

    private func persist() {
        guard let data = try? JSONEncoder().encode(sessions) else { return }
        UserDefaults.standard.set(data, forKey: storageKey)
    }
}
