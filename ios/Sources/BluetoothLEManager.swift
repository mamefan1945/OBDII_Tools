import Combine
import CoreBluetooth
import Foundation

/// BLE adapter discovery and connection for UART-style ELM327 adapters.
/// iOS does not expose general-purpose Bluetooth Classic SPP/RFCOMM to third-party apps;
/// adapters using that Android transport are not supported by this class.
final class BluetoothLEManager: NSObject, ObservableObject {
    struct Adapter: Identifiable {
        let peripheral: CBPeripheral
        var id: UUID { peripheral.identifier }
        var name: String
        var rssi: Int
        var manufacturerDataHex: String
        var serviceUUIDs: [String]
    }

    @Published private(set) var adapters: [Adapter] = []
    @Published private(set) var status = "Bluetooth not initialized"
    @Published private(set) var connectedName: String?
    @Published private(set) var isScanning = false
    @Published private(set) var isReadyForELM = false
    @Published private(set) var isInitialized = false
    @Published private(set) var vehicleVIN: String?
    @Published private(set) var vinStatus = "VIN not read"
    @Published private(set) var activeSession: DiagnosticSession?
    private let sessionStore = SessionStore()
    private var connectedAdapter: Adapter?

    private var central: CBCentralManager!
    private var connectedPeripheral: CBPeripheral?
    private var writeCharacteristic: CBCharacteristic?
    private var notifyCharacteristic: CBCharacteristic?
    private var responseBuffer = ""
    private var pendingResponse: CheckedContinuation<String, Never>?
    private var queryInProgress = false
    private var initializationStarted = false
    private var notificationsActive = false

    private let uartServiceIDs: [CBUUID] = [
        CBUUID(string: "FFE0"),
        CBUUID(string: "FFF0")
    ]
    private let writeIDs: Set<String> = ["FFE1", "FFF2"]
    private let notifyIDs: Set<String> = ["FFE1", "FFF1"]

    override init() {
        super.init()
        central = CBCentralManager(delegate: self, queue: .main)
    }

    func startScan() {
        guard central.state == .poweredOn else {
            status = bluetoothStateDescription(central.state)
            return
        }
        adapters.removeAll()
        isScanning = true
        status = "Scanning for Bluetooth LE adapters…"
        // Scan broadly: inexpensive ELM327 BLE clones do not all advertise a service UUID.
        central.scanForPeripherals(withServices: nil, options: [
            CBCentralManagerScanOptionAllowDuplicatesKey: false
        ])
    }

    func stopScan() {
        central.stopScan()
        isScanning = false
        if connectedPeripheral == nil { status = "Scan stopped" }
    }

    func connect(to adapter: Adapter) {
        stopScan()
        status = "Connecting to \(adapter.name)…"
        connectedName = nil
        isReadyForELM = false
        isInitialized = false
        vehicleVIN = nil
        vinStatus = "Waiting for adapter initialization"
        initializationStarted = false
        notificationsActive = false
        writeCharacteristic = nil
        notifyCharacteristic = nil
        connectedPeripheral = adapter.peripheral
        connectedAdapter = adapter
        adapter.peripheral.delegate = self
        central.connect(adapter.peripheral, options: nil)
    }

    func disconnect() {
        if let peripheral = connectedPeripheral {
            central.cancelPeripheralConnection(peripheral)
        }
    }

    private func bluetoothStateDescription(_ state: CBManagerState) -> String {
        switch state {
        case .poweredOn: return "Bluetooth ready"
        case .poweredOff: return "Bluetooth is turned off"
        case .unauthorized: return "Bluetooth permission is not authorized"
        case .unsupported: return "Bluetooth LE is not supported on this device"
        case .resetting: return "Bluetooth is resetting"
        case .unknown: return "Waiting for Bluetooth"
        @unknown default: return "Bluetooth unavailable"
        }
    }

    /// Sends one ELM327 command and waits for the adapter prompt or timeout.
    /// Callers must serialize commands; overlapping requests are rejected.
    func query(_ command: String, timeout: TimeInterval = 1.5) async -> String {
        guard !queryInProgress,
              let peripheral = connectedPeripheral,
              let characteristic = writeCharacteristic,
              isReadyForELM else { return "" }
        queryInProgress = true
        defer { queryInProgress = false }
        responseBuffer = ""
        let payload = (command.trimmingCharacters(in: .whitespacesAndNewlines) + "\r")
        guard let data = payload.data(using: .ascii) else { return "" }

        return await withCheckedContinuation { continuation in
            pendingResponse = continuation
            let writeType: CBCharacteristicWriteType =
                characteristic.properties.contains(.write) ? .withResponse : .withoutResponse
            peripheral.writeValue(data, for: characteristic, type: writeType)
            Task { [weak self] in
                let nanos = UInt64(max(0.1, timeout) * 1_000_000_000)
                try? await Task.sleep(nanoseconds: nanos)
                self?.finishPendingResponse()
            }
        }
    }

    /// BLE-specific initialization intentionally omits ATZ: some HM-10 adapters
    /// reset their BLE radio when the ELM chip receives ATZ.
    func initializeAdapter() async -> Bool {
        let commands = ["ATE0", "ATL0", "ATS0", "ATH0", "ATAT2", "ATSP0"]
        for command in commands {
            let response = await query(command, timeout: 2.0)
            if response.isEmpty {
                isInitialized = false
                return false
            }
        }
        isInitialized = true
        return true
    }

    /// Reads VIN using Mode 09 PID 02, then UDS DID F190 as a fallback.
    func identifyVehicle() async -> String? {
        guard isInitialized else { return nil }
        vinStatus = "Reading VIN (Mode 09 PID 02)…"
        let mode09 = await query("0902", timeout: 6.0)
        if let vin = ELM327Protocol.parseMode09VIN(mode09) {
            vehicleVIN = vin
            vinStatus = "VIN read successfully using Mode 09 PID 02"
            sessionStore.updateVIN(vin)
            activeSession = sessionStore.activeSession
            return vin
        }
        vinStatus = "Mode 09 unavailable; trying UDS F190…"
        let uds = await queryUDS(ecuAddress: "7E0", did: "F190")
        if let vin = ELM327Protocol.parseUDSVIN(uds) {
            vehicleVIN = vin
            vinStatus = "VIN read successfully using UDS F190"
            sessionStore.updateVIN(vin)
            activeSession = sessionStore.activeSession
            return vin
        }
        vehicleVIN = nil
        sessionStore.updateVIN(nil)
        activeSession = sessionStore.activeSession
        vinStatus = "VIN could not be read from this adapter/vehicle"
        return nil
    }

    func queryUDS(ecuAddress: String, did: String) async -> String {
        let header = await query("ATSH \(ecuAddress)", timeout: 1.0)
        guard !header.isEmpty else { return "" }
        let response = await query("22\(did)", timeout: 3.0)
        _ = await query("ATSH 7DF", timeout: 1.0)
        return response
    }

    func pingECU(address: String) async -> String {
        _ = await query("ATSH \(address)", timeout: 1.0)
        let response = await query("1001", timeout: 1.5)
        if response.localizedCaseInsensitiveContains("5001") ||
            response.localizedCaseInsensitiveContains("7F10") {
            _ = await query("ATSH 7DF", timeout: 1.0)
            return response
        }
        let fallback = await query("0100", timeout: 1.5)
        _ = await query("ATSH 7DF", timeout: 1.0)
        return fallback
    }

    private func captureAdapterFingerprint() async {
        guard let adapter = connectedAdapter else { return }
        let ati = (await query("ATI", timeout: 1.5)).trimmingCharacters(in: .whitespacesAndNewlines)
        let description = (await query("AT@1", timeout: 1.5)).trimmingCharacters(in: .whitespacesAndNewlines)
        sessionStore.startSession(
            adapterName: adapter.name,
            peripheralIdentifier: adapter.id.uuidString,
            manufacturerDataHex: adapter.manufacturerDataHex,
            serviceUUIDs: adapter.serviceUUIDs,
            rssi: adapter.rssi,
            atiResponse: String(ati.prefix(256)),
            adapterDescription: String(description.prefix(256))
        )
        activeSession = sessionStore.activeSession
    }

    private func finishPendingResponse() {
        guard let continuation = pendingResponse else { return }
        pendingResponse = nil
        let response = responseBuffer
        responseBuffer = ""
        continuation.resume(returning: response)
    }

    private func updateELMReadiness() {
        guard let writeCharacteristic, let notifyCharacteristic else {
            isReadyForELM = false
            return
        }
        let canWrite = writeCharacteristic.properties.contains(.write) ||
            writeCharacteristic.properties.contains(.writeWithoutResponse)
        let canNotify = notifyCharacteristic.properties.contains(.notify) ||
            notifyCharacteristic.properties.contains(.indicate)
        isReadyForELM = canWrite && canNotify && notificationsActive
        if isReadyForELM {
            status = "BLE UART connected; initializing ELM327…"
            if !initializationStarted {
                initializationStarted = true
                Task { [weak self] in
                    guard let self else { return }
                    let success = await self.initializeAdapter()
                    self.status = success
                        ? "Connected and ELM327 initialized"
                        : "BLE connected, but ELM327 initialization did not complete"
                    if success { await self.captureAdapterFingerprint() }
                }
            }
        } else if canWrite && canNotify {
            status = "BLE UART found; enabling adapter notifications…"
        } else {
            status = "Connected, but no compatible ELM327 UART characteristics were found"
        }
    }
}

extension BluetoothLEManager: CBCentralManagerDelegate {
    func centralManagerDidUpdateState(_ central: CBCentralManager) {
        status = bluetoothStateDescription(central.state)
        if central.state != .poweredOn {
            isScanning = false
            isReadyForELM = false
        }
    }

    func centralManager(_ central: CBCentralManager,
                        didDiscover peripheral: CBPeripheral,
                        advertisementData: [String: Any],
                        rssi RSSI: NSNumber) {
        let id = peripheral.identifier
        if let index = adapters.firstIndex(where: { $0.id == id }) {
            adapters[index].rssi = RSSI.intValue
        } else {
            let manufacturerData = (advertisementData[CBAdvertisementDataManufacturerDataKey] as? Data)?
                .map { $0.map { String(format: "%02X", $0) }.joined() } ?? ""
            let serviceUUIDs = (advertisementData[CBAdvertisementDataServiceUUIDsKey] as? [CBUUID] ?? [])
                .map { $0.uuidString }
            let advertisedName = advertisementData[CBAdvertisementDataLocalNameKey] as? String
                ?? peripheral.name ?? "Unnamed OBD adapter"
            adapters.append(Adapter(
                peripheral: peripheral, name: advertisedName, rssi: RSSI.intValue,
                manufacturerDataHex: manufacturerData, serviceUUIDs: serviceUUIDs
            ))
            adapters.sort { $0.rssi > $1.rssi }
        }
    }

    func centralManager(_ central: CBCentralManager, didConnect peripheral: CBPeripheral) {
        connectedName = peripheral.name ?? "OBD adapter"
        status = "Connected; discovering adapter services…"
        peripheral.discoverServices(nil)
    }

    func centralManager(_ central: CBCentralManager,
                        didFailToConnect peripheral: CBPeripheral,
                        error: Error?) {
        status = "Connection failed: \(error?.localizedDescription ?? "unknown error")"
        connectedPeripheral = nil
        connectedName = nil
        isReadyForELM = false
    }

    func centralManager(_ central: CBCentralManager,
                        didDisconnectPeripheral peripheral: CBPeripheral,
                        error: Error?) {
        connectedPeripheral = nil
        connectedName = nil
        connectedAdapter = nil
        sessionStore.endActiveSession()
        activeSession = nil
        isInitialized = false
        vehicleVIN = nil
        writeCharacteristic = nil
        notifyCharacteristic = nil
        isReadyForELM = false
        status = error.map { "Disconnected: \($0.localizedDescription)" } ?? "Disconnected"
    }
}

extension BluetoothLEManager: CBPeripheralDelegate {
    func peripheral(_ peripheral: CBPeripheral, didDiscoverServices error: Error?) {
        guard error == nil else {
            status = "Service discovery failed: \(error!.localizedDescription)"
            return
        }
        guard let services = peripheral.services, !services.isEmpty else {
            status = "No BLE services found on adapter"
            return
        }
        for service in services {
            peripheral.discoverCharacteristics(nil, for: service)
        }
    }

    func peripheral(_ peripheral: CBPeripheral,
                    didDiscoverCharacteristicsFor service: CBService,
                    error: Error?) {
        guard error == nil, let characteristics = service.characteristics else {
            status = "Characteristic discovery failed: \(error?.localizedDescription ?? "unknown error")"
            return
        }
        for characteristic in characteristics {
            let uuid = characteristic.uuid.uuidString.uppercased()
            if writeIDs.contains(uuid) &&
                (characteristic.properties.contains(.write) ||
                 characteristic.properties.contains(.writeWithoutResponse)) {
                writeCharacteristic = characteristic
            }
            if notifyIDs.contains(uuid) &&
                (characteristic.properties.contains(.notify) ||
                 characteristic.properties.contains(.indicate)) {
                notifyCharacteristic = characteristic
                peripheral.setNotifyValue(true, for: characteristic)
            }
        }
        updateELMReadiness()
    }

    func peripheral(_ peripheral: CBPeripheral,
                    didUpdateValueFor characteristic: CBCharacteristic,
                    error: Error?) {
        guard error == nil, characteristic.uuid == notifyCharacteristic?.uuid,
              let data = characteristic.value,
              let chunk = String(data: data, encoding: .ascii) else { return }
        responseBuffer += chunk
        if responseBuffer.contains(">") {
            finishPendingResponse()
        }
    }

    func peripheral(_ peripheral: CBPeripheral,
                    didUpdateNotificationStateFor characteristic: CBCharacteristic,
                    error: Error?) {
        if let error {
            status = "Adapter notification setup failed: \(error.localizedDescription)"
            isReadyForELM = false
        } else {
            notificationsActive = characteristic.isNotifying
            updateELMReadiness()
        }
    }
}
