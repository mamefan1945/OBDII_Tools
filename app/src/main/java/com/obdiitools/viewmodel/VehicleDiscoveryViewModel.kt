package com.obdiitools.viewmodel
import androidx.lifecycle.ViewModel
import com.obdiitools.data.VehicleDiscoveryProfile
import com.obdiitools.data.VehicleDiscoveryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
@HiltViewModel
class VehicleDiscoveryViewModel @Inject constructor(repository:VehicleDiscoveryRepository):ViewModel() {
 val profiles:StateFlow<List<VehicleDiscoveryProfile>> = repository.profiles
}