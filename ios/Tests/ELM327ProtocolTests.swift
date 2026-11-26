import XCTest
@testable import OBDIITools

final class ELM327ProtocolTests: XCTestCase {
    func testBuildsMultiPIDCommandLikeAndroid() {
        XCTAssertEqual(ELM327Protocol.buildMultiPIDCommand([
            ELM327Protocol.PID.rpm, ELM327Protocol.PID.speed, ELM327Protocol.PID.maf
        ]), "01 0C 0D 10")
    }

    func testKeepsMAFAndFuelLevelAsAlwaysProbe() {
        let result = ELM327Protocol.filterSupportedPIDs(
            [ELM327Protocol.PID.rpm, ELM327Protocol.PID.maf, ELM327Protocol.PID.fuelLevel],
            supported: [ELM327Protocol.PID.rpm]
        )
        XCTAssertEqual(result, [ELM327Protocol.PID.rpm, ELM327Protocol.PID.maf, ELM327Protocol.PID.fuelLevel])
    }

    func testDecodesRPMAndMAF() {
        XCTAssertEqual(ELM327Protocol.parseResponse("010C", rawResponse: "410C1AF8"), 1726)
        XCTAssertEqual(ELM327Protocol.parseResponse("0110", rawResponse: "411001F4"), 5.0)
    }

    func testParsesConcatenatedMultiPIDResponses() {
        let result = ELM327Protocol.parseMultiPIDResponse(["410C1AF8410D32"])
        XCTAssertEqual(result[ELM327Protocol.PID.rpm], 1726)
        XCTAssertEqual(result[ELM327Protocol.PID.speed], 50)
    }

    func testParsesStoredAndPendingTroubleCodes() {
        XCTAssertEqual(ELM327Protocol.parseDTCs("430201042000").map(\.code), ["P0104", "P2000"])
        XCTAssertEqual(ELM327Protocol.parseDTCs("47010133", isPending: true).map(\.code), ["P0133"])
    }

    func testParsesVINFromMode09IndexedFrames() {
        let response = "0:490201314847434d1:3832363333413030 2:34333532"
        XCTAssertEqual(ELM327Protocol.parseMode09VIN(response), "1HGCM82633A004352")
    }

    func testParsesVINFromUDSF190() {
        XCTAssertEqual(
            ELM327Protocol.parseUDSVIN("62F190314847434D383236333341303034333532"),
            "1HGCM82633A004352"
        )
    }

    func testRejectsInvalidVIN() {
        XCTAssertNil(ELM327Protocol.parseMode09VIN("NO DATA"))
        XCTAssertFalse(ELM327Protocol.isValidVIN("00000000000000000"))
    }

    func testRejectsNoData() {
        XCTAssertNil(ELM327Protocol.parseResponse("010C", rawResponse: "NO DATA"))
        XCTAssertTrue(ELM327Protocol.parseDTCs("NO DATA").isEmpty)
    }
}
