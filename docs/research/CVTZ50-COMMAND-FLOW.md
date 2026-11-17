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
