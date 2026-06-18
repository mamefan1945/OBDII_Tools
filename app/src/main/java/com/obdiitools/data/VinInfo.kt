package com.obdiitools.data

data class VinInfo(
    val vin: String,
    val make: String = "",
    val model: String = "",
    val year: String = "",
    val trim: String = "",
) {
    val displayName: String get() = when {
        make.isBlank() -> vin
        model.isBlank() -> "$year $make"
        else -> "$year $make $model"
    }
}
