# 2016 Mazda CX-5 compatibility notes

## Confirmed baseline

The standard OBD-II path is compatible in principle with a 2016 North-American CX-5:

- The vehicle has a diagnostic connector intended for diagnostic equipment.
- The CX-5 service information describes separate high-speed CAN (HS-CAN) and mid-speed CAN (MS-CAN) networks.
- The HS-CAN network includes the PCM, DSC hydraulic/control module, automatic-transmission TCM (when fitted), front body control module, AWD control module (when fitted), EPS, SAS, instrument cluster and start-stop unit.
- The MS-CAN network includes the rear body control module, blind-spot modules (when fitted), climate control and audio-related modules.
- The instrument cluster connects the HS-CAN and MS-CAN networks.

References:
- Mazda CX-5 service-manual network description: https://www.mcx5.org/multiplex_communication_system-1170.html
- Mazda CX-5 CAN architecture: https://www.mcx5.org/controller_area_network_can_system-1163.html
- 2016 CX-5 owner's manual diagnostic-connector warning: https://www.mazdausa.com/siteassets/pdf/owners-optimized/2016/cx5/2016-cx-5-owners-manual.pdf

## Deep Scan coverage

The application currently probes 11-bit request IDs 0x700–0x77F and 0x7E0–0x7E7. The latter includes the standardized physical OBD request address 0x7E0 used for the engine/PCM in common ISO 15765-4 addressing.

ECU discovery now tries:
1. UDS default-session request 10 01.
2. If no UDS response is received, standard OBD Mode 01 PID-support request 01 00.

A positive UDS 50 01 or OBD 41 00 response is recorded. A service-specific negative response is also recorded as evidence that a controller responded, but is not treated as proof that the requested service is supported.

For each discovered ECU, Deep Scan queries Mode 01 support and attempts UDS DID F190 (VIN). The VIN is displayed only if the positive response contains a valid 17-character VIN.

## Mazda-specific data: research leads, not yet enabled

A community-maintained CX-5 parameter catalogue lists Mode 22 DID 0202 (DTC count) and DID F43C (catalyst temperature) as supported for some 2016 CX-5 configurations, with the engine at 0x7E0. These are useful candidates for later read-only probing, not yet verified against this vehicle or every engine calibration.

Reference: https://pelican.clutch.engineering/cars/mazda/cx-5/parameters

No Mazda-specific value is decoded or presented until a real response has been captured and its byte layout, scaling, units and applicability are verified.

## Important limits

- This is not yet a complete Mazda M-MDS replacement or a guarantee of access to every module.
- The current scan uses 11-bit CAN requests on the adapter's active CAN channel. It does not switch physical CAN networks, access extra diagnostic connector pins, or implement Mazda's complete module-discovery procedure.
- Because the CX-5 has HS-CAN and MS-CAN segments, access to some MS-CAN modules may depend on the vehicle gateway and the adapter's electrical/network capabilities.
- ECU addresses are not assigned Mazda module identities unless confirmed by vehicle-specific evidence.
- Engine, transmission, AWD, driver-assistance and equipment variants can change which modules and data identifiers are present.
- No actuator tests, resets, coding, programming, security access or clearing operations are part of this scan.

## Vehicle validation still required

A real scan on the 2016 CX-5 should capture:
- Adapter make/model and supported CAN pins/network access.
- Protocol selected by ELM auto-detection.
- Raw request and response for each responding ECU.
- VIN and any available ECU identification values.
- Supported Mode 01 bitmasks and raw responses for candidate Mode 22 DIDs.

Only after those captures should Mazda-specific decoders or module labels be promoted from research candidates to confirmed support.
