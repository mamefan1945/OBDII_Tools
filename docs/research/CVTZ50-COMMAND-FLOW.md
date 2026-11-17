# CVTz50 Custom Command Flow — Reference Findings

**Status:** Reverse-engineering notes only. These findings are not an implementation specification for Nissan CVT7 diagnostics.

**Reference artifact:** CVTz50 APK decompiled in the separate private `cvt-debugging` repository. The original APK is retained there; no CVTz50 source code is copied into this project.

## Confirmed Java-side behavior

The Special Commands UI splits the entered text on semicolons, queues each non-empty command, and appends a carriage return. The custom-command worker sends queued strings through the Bluetooth service.

The worker treats `>` as the normal response terminator. It accumulates incoming response fragments, strips whitespace and NUL characters for terminator detection, and advances to the next queued command when the expected terminator is found.

A trailing `!` is a repeat marker. The queue logic retains the marked command and sends a clean copy; after the response terminator, the state machine cycles and sends it again. This continues until the command session is stopped/closed.

**Important edge case:** in the traced state-1000 path, after sending a command the worker checks whether the queue is empty. If empty, it shuts down immediately. Thus the last command in an ordinary batch is transmitted without the Java worker waiting for its response terminator. Earlier commands in a multi-command batch do wait before the next command is sent.

The worker creates a 15-second timer and restarts it as response data arrives. The exact timeout-handler consequence has not yet been established.

## Unresolved behavior

- The Special Commands help text documents `*` as a wildcard expanded through hexadecimal values 0–F. The traced Java input/queue/transmit methods contain no such expansion. The implementation layer remains unknown.
- The custom-command state-1000 path does not invoke the Java helper used in adapter-initialization states to detect a response containing `?` without `OK`. Whether another layer reports these errors remains unknown.
- The exact handling of the worker's 15-second timeout message remains under investigation.

## Relevance to OBDII_Tools

These observations describe CVTz50's user-facing arbitrary-command queue and adapter interaction. They do **not** establish Nissan CVT7 request identifiers, ECU addressing, response payload layouts, scaling, or field offsets. They therefore do not justify changing `NissanCvt7Protocol` or enabling any unverified live-data decoding.

Potentially reusable *concepts*, if OBDII_Tools later adds a general raw-command console, are semicolon-separated command sequencing, explicit response-terminator handling, bounded response accumulation, cancellation, and timeout reporting. Those should be implemented against OBDII_Tools' own transport abstractions and tests, not by copying CVTz50 code or reproducing its final-command shutdown edge case.

For CVT7 telemetry, continue to require independently verified ECU identity, request/positive-response pairs, complete multi-frame capture, and validated field offsets/scales. Keep decoding fail-closed until those are established.


## Newly confirmed CVT7 decoder mapping and implementation

The APK's manual CVT7 profile directly links command ID 1100 to request `2101`, configures the request header as `7E1`, and uses response filter `7E9`. The Java formatter's `ic.f()` reads the response as an 83-byte-or-longer array whose indexes include the leading `61 01` bytes. It requires the adapter framing prefix `07A`, concatenates numbered ELM fragments, and applies an additional check when byte index 86 is present: that byte must be no greater than 4.

The directly decoded fields now implemented in `NissanCvt7Protocol.parseLiveData()` are:

| Byte index (zero-based) | Conversion | OBDII Tools field |
|---:|---|---|
| 15 | unsigned byte | Vehicle speed (km/h) |
| 21 | unsigned byte shifted left 5 | Engine RPM |
| 24 | value × 0.045 | Gear ratio |
| 36 | value × 0.025 | Secondary pressure (MPa) |
| 37 | value − 40 | CVT fluid temperature (°C) |
| 43 | value × 0.025 | Target secondary pressure and line pressure |
| 44 | value × 0.025 | Primary pressure (MPa) |

Both target-secondary and line-pressure display values are sourced from byte 43 in the reference formatter; OBDII Tools preserves that behavior rather than inventing a separate byte offset. Other extracted bytes and derived displays are not exposed as live fields yet.

The decoder now requires the `07A` adapter prefix, a complete `6101` payload of at least 83 bytes, valid hexadecimal framing, and the optional byte-86 sanity limit when that byte is present. Tests cover the supplied captured response, decoded values, truncated/unframed/negative responses, and the byte-86 rejection condition.

## OBDII Tools transport improvement from the response-flow findings

The reference app accumulates fragmented adapter input until its response terminator is satisfied. OBDII Tools already has a prompt-based line reader, but both Classic Bluetooth and BLE UDS paths previously returned only the first line beginning with `62` or `7F`. That silently discarded any later indexed continuation lines. The transport paths now preserve all response lines, and `UdsResponse.parse()` strips ELM's one-hex-digit frame labels and assembles the payload in order before validating the requested DID. Tests cover indexed positive and negative responses. This is a general transport fix; it does not copy CVTz50's timeout workaround or its final-command queue shutdown behavior.

## Remaining evidence boundaries

- The CVT7 values above reproduce the reference app's implemented offsets and conversions; they are not an independent Nissan engineering specification. Validate units and behavior against a complete capture and service documentation before presenting the values as OEM-certified measurements.
- The optional byte-86 value is checked for range by the reference app, but its semantic name is still unknown.
- The wildcard expansion and 15-second timeout findings apply to CVTz50's arbitrary-command UI. OBDII Tools has no equivalent arbitrary-command queue that needs those behaviors today, so they are documented but not added to custom PID execution.
- No unidentified CVT7 bytes, hydraulic control commands, write services, or actuator routines have been implemented.
