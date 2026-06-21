package com.obdiitools.util

fun getSessionTitle(make: String?, model: String?, bluetoothName: String?): String {
    val m = make?.trim().takeIf { !it.isNullOrEmpty() } ?: "unknown"
    val mo = model?.trim().takeIf { !it.isNullOrEmpty() } ?: "unknown"

    return if (m == "unknown" && mo == "unknown") {
        // Per your instruction, bluetoothName should always be present as a last-resort fallback
        bluetoothName?.trim().takeIf { it?.isNotEmpty() == true } ?: "unknown"
    } else {
        "$m $mo"
    }
}
