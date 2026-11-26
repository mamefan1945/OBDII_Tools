import XCTest
@testable import OBDIITools

final class AdapterFingerprintClassifierTests: XCTestCase {
    func testRecognizesVeepeakOBDCheckBLEPlusAsProbableFromName() {
        let result = AdapterFingerprintClassifier.identify(
            advertisedName: "OBDCheck BLE+", ati: "ELM327 v1.5", description: ""
        )
        XCTAssertEqual(result.manufacturer, "VEEPEAK")
        XCTAssertEqual(result.model, "OBDCheck BLE+")
        XCTAssertEqual(result.confidence, "probable")
    }

    func testExplicitATOneProductStringIsConfirmed() {
        let result = AdapterFingerprintClassifier.identify(
            advertisedName: "OBDII", ati: "ELM327", description: "OBDLink MX+"
        )
        XCTAssertEqual(result.manufacturer, "OBDLink")
        XCTAssertEqual(result.model, "MX+")
        XCTAssertEqual(result.confidence, "confirmed")
    }

    func testGenericElm327DoesNotInventBrandOrModel() {
        let result = AdapterFingerprintClassifier.identify(
            advertisedName: "OBDII", ati: "ELM327 v2.1", description: "ELM327"
        )
        XCTAssertEqual(result.manufacturer, "")
        XCTAssertEqual(result.model, "")
        XCTAssertEqual(result.confidence, "unknown")
    }
}
