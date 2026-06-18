package com.obdiitools.car

import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator
import com.obdiitools.bluetooth.OBDRepository
import com.obdiitools.data.PreferencesRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class OBDCarAppService : CarAppService() {

    @Inject lateinit var repository: OBDRepository
    @Inject lateinit var prefsRepository: PreferencesRepository

    override fun createHostValidator(): HostValidator = HostValidator.ALLOW_ALL_HOSTS_VALIDATOR

    override fun onCreateSession(): Session = OBDCarSession(repository, prefsRepository)
}
