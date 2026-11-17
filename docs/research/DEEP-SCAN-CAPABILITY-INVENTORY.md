# Deep Scan capability inventory

## Scope

Deep Scan inventories responses from the application's current 11-bit CAN request-address candidate set:

- 0x700–0x77F (128 addresses)
- 0x7E0–0x7E7 (8 addresses)

A responding address is evidence that a diagnostic request received a response. It does not, by itself, establish the controller's manufacturer, function, or vehicle-specific capabilities.

## Evidence collected

For each individually responding address, Deep Scan:

1. Records whether the initial default-session request received a positive response or a negative response.
2. Queries Mode 01 supported-PID bitmasks and lists only PIDs indicated by valid support bitmasks.
3. Queries Mode 09 support on the broadcast address and conventional 0x7E0 engine request address.
4. Attempts the read-only UDS ReadDataByIdentifier request 0x22 F190 (VIN) against each individually responding ECU address.
5. Displays a VIN only when the response is positive for DID F190 and the first 17 payload bytes form a valid 17-character VIN (excluding I, O, and Q).

## Interpretation rules

- Address hints are heuristics based on common address conventions. They are not verified ECU identities.
- A positive Mode 01 support bit means the ECU reported that PID as supported. It does not guarantee that a live value will be available under all operating conditions.
- Mode 09 support is queried only on the broadcast address and 0x7E0 in this implementation.
- A successful F190 read is evidence that the ECU returned a VIN. An unsupported DID, negative response, timeout, or malformed payload is shown as unavailable; no VIN is inferred.
- A VIN returned by multiple ECUs may be shared vehicle identity, not a unique controller identifier.
- No undocumented DID sweep, diagnostic-session change, security access, actuator test, reset, coding, or clear command is performed by this inventory scan.

## Known limitations

- The address sweep is a finite candidate set, not a complete scan of all possible CAN identifiers, 29-bit addressing, gateways, or transport/protocol variants.
- The scan does not yet retrieve and verify ECU part numbers, hardware/software versions, supplier IDs, or controller serial numbers.
- Address-to-module labels remain hints until confirmed by ECU identity data or vehicle-specific documentation.
- A negative response proves that a request reached a diagnostic responder, but its NRC must be interpreted in the context of the request and ECU state.
