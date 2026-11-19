package com.obdiitools.data

import android.annotation.SuppressLint
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GpsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val fusedClient = LocationServices.getFusedLocationProviderClient(context)

    // A stream, not a StateFlow: every delivered fix must be available to the recorder.
    private val _locations = MutableSharedFlow<Location>(
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val locations: SharedFlow<Location> = _locations

    private var lastRecordedElapsedRealtimeNanos = 0L

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val nowNanos = android.os.SystemClock.elapsedRealtimeNanos()
            result.locations.forEach { location ->
                // Do not turn a cached/old fix into a new track point.
                val ageNanos = nowNanos - location.elapsedRealtimeNanos
                if (location.elapsedRealtimeNanos <= lastRecordedElapsedRealtimeNanos) return@forEach
                if (ageNanos < 0L || ageNanos > MAX_LOCATION_AGE_NANOS) return@forEach
                if (!location.hasAccuracy() || location.accuracy > MAX_ACCEPTED_ACCURACY_METERS) return@forEach

                lastRecordedElapsedRealtimeNanos = location.elapsedRealtimeNanos
                _locations.tryEmit(location)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startUpdates() {
        val hasFineLocation = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val hasCoarseLocation = context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!hasFineLocation && !hasCoarseLocation) return

        lastRecordedElapsedRealtimeNanos = 0L
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, UPDATE_INTERVAL_MS)
            .setMinUpdateIntervalMillis(MIN_UPDATE_INTERVAL_MS)
            .setMaxUpdateDelayMillis(UPDATE_INTERVAL_MS)
            .setWaitForAccurateLocation(false)
            .build()
        fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
    }

    fun stopUpdates() {
        fusedClient.removeLocationUpdates(callback)
    }

    private companion object {
        const val UPDATE_INTERVAL_MS = 1_000L
        const val MIN_UPDATE_INTERVAL_MS = 500L
        const val MAX_LOCATION_AGE_NANOS = 15_000_000_000L
        const val MAX_ACCEPTED_ACCURACY_METERS = 100f
    }
}
