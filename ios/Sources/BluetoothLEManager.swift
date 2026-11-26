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
        var name: String { peripheral.name ?? "Unnamed OBD adapter" }
        var rssi: Int
    }

    @Published private(set) var adapters: [Adapter] = []
    @Published private(set) var status = "Bluetooth not initialized"
    @Published private(set) var connectedName: String?
    @Published private(set) var isScanning = false
    @Published private(set) var isReadyForELM = false

    private var central: CBCentralManager!
    private var connectedPeripheral: CBPeripheral?
    private var writeCharacteristic: CBCharacteristic?
    private var notifyCharacteristic: CBCharacteristic?

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
        writeCharacteristic = nil
        notifyCharacteristic = nil
        connectedPeripheral = adapter.peripheral
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

    private func updateELMReadiness() {
        guard let writeCharacteristic, let notifyCharacteristic else {
            isReadyForELM = false
            return
        }
        let canWrite = writeCharacteristic.properties.contains(.write) ||
            writeCharacteristic.properties.contains(.writeWithoutResponse)
        let canNotify = notifyCharacteristic.properties.contains(.notify) ||
            notifyCharacteristic.properties.contains(.indicate)
        isReadyForELM = canWrite && canNotify
        status = isReadyForELM
            ? "BLE link ready — ELM327 command transport next"
            : "Connected, but no compatible ELM327 UART characteristics were found"
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
            adapters.append(Adapter(peripheral: peripheral, rssi: RSSI.intValue))
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
                    didUpdateNotificationStateFor characteristic: CBCharacteristic,
                    error: Error?) {
        if let error {
            status = "Adapter notification setup failed: \(error.localizedDescription)"
            isReadyForELM = false
        } else {
            updateELMReadiness()
        }
    }
}
