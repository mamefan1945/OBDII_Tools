package com.obdiitools.car

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.Session
import com.obdiitools.bluetooth.OBDRepository
import com.obdiitools.data.PreferencesRepository

class OBDCarSession(
    private val repository: OBDRepository,
    private val prefsRepository: PreferencesRepository,
) : Session() {

    override fun onCreateScreen(intent: Intent): Screen =
        CarMainScreen(carContext, repository, prefsRepository)
}
