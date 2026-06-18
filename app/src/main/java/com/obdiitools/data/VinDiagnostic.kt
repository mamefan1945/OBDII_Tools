package com.obdiitools.data

data class VinDiagnostic(
    // Mode 09 PID 02
    val mode09RawLines: List<String> = emptyList(),
    val mode09FrameFormat: Boolean = false,   // true = ELM327 N: frame-numbered format
    val mode09FilteredHex: String = "",        // bytes after stripping headers
    val mode09ParsedVin: String? = null,

    // UDS DID F190 fallback
    val udsAttempted: Boolean = false,
    val udsRaw: String = "",
    val udsParsedVin: String? = null,

    // Which VIN was ultimately selected
    val vinSelected: String? = null,

    // NHTSA VPIC decode
    val nhtsaAttempted: Boolean = false,
    val nhtsaHttpStatus: Int? = null,
    val nhtsaError: String? = null,
    val nhtsaMake: String? = null,
    val nhtsaModel: String? = null,
    val nhtsaYear: String? = null,
)
