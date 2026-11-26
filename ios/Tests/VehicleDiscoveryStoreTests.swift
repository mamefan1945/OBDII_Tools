import XCTest
@testable import OBDIITools

final class VehicleDiscoveryStoreTests: XCTestCase {
    func testVINManufacturerMatchesAndroidMappings() {
        XCTAssertEqual(VehicleDiscoveryStore.makeFromVIN("JM1XXXXXXXXXXXXXX"), "Mazda")
        XCTAssertEqual(VehicleDiscoveryStore.makeFromVIN("JN8XXXXXXXXXXXXXX"), "Nissan")
        XCTAssertEqual(VehicleDiscoveryStore.makeFromVIN("1HGXXXXXXXXXXXXXX"), "Honda")
    }

    func testRecordsAndDeletesUnverifiedDeepScanProfile() {
        let suite = "VehicleDiscoveryStoreTests-\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suite)!
        defer { defaults.removePersistentDomain(forName: suite) }
        let store = VehicleDiscoveryStore(defaults: defaults)
        let item = VehicleDiscoveryItem(
            key: "7E8-010C", name: "Engine RPM", ecuAddress: "7E8",
            category: "PID", known: true, status: "Supported", rawResponse: "410C1AF8"
        )
        store.recordScan(vin: nil, items: [item], ecuCount: 1)
        XCTAssertEqual(store.profiles.count, 1)
        XCTAssertEqual(store.profiles[0].knownCount, 1)
        XCTAssertEqual(store.profiles[0].unknownCount, 0)

        let id = store.profiles[0].id
        let reloaded = VehicleDiscoveryStore(defaults: defaults)
        XCTAssertEqual(reloaded.profiles.count, 1)
        reloaded.deleteProfile(id: id)
        XCTAssertTrue(VehicleDiscoveryStore(defaults: defaults).profiles.isEmpty)
    }
}
