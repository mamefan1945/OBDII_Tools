package com.obdiitools.bluetooth

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.obdiitools.MainActivity
import com.obdiitools.obd.ConnectionState
import com.obdiitools.obd.OBDData
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class OBDForegroundService : Service() {

    @Inject lateinit var repository: OBDRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val nm by lazy { getSystemService(NOTIFICATION_SERVICE) as NotificationManager }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val initial = buildNotification("Connecting…", null)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, initial, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(NOTIFICATION_ID, initial)
        }
        observeState()
    }

    private fun observeState() {
        serviceScope.launch {
            combine(repository.connectionState, repository.obdData) { state, data -> state to data }
                .collect { (state, data) ->
                    when (state) {
                        is ConnectionState.Connected  -> nm.notify(NOTIFICATION_ID, buildNotification(state.deviceName, data))
                        is ConnectionState.Connecting -> nm.notify(NOTIFICATION_ID, buildNotification("Connecting to ${state.deviceName}…", null))
                        is ConnectionState.Disconnected,
                        is ConnectionState.Error -> {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                stopForeground(STOP_FOREGROUND_REMOVE)
                            } else {
                                @Suppress("DEPRECATION")
                                stopForeground(true)
                            }
                            stopSelf()
                        }
                    }
                }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DISCONNECT) {
            repository.disconnect()
        }
        return START_STICKY
    }

    private fun buildNotification(deviceName: String, data: OBDData?): Notification {
        val tapIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
            PendingIntent.FLAG_IMMUTABLE,
        )
        val disconnectIntent = PendingIntent.getService(
            this, 1,
            Intent(this, OBDForegroundService::class.java).apply { action = ACTION_DISCONNECT },
            PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("OBD2 — $deviceName")
            .setContentText(data?.statsLine() ?: "Waiting for data…")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(tapIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Disconnect", disconnectIntent)
            .build()
    }

    private fun OBDData.statsLine(): String {
        val parts = mutableListOf<String>()
        rpm?.let { parts.add("$it RPM") }
        speedKph?.let { parts.add("$it km/h") }
        coolantTempC?.let { parts.add("${it}°C") }
        return parts.joinToString(" · ").ifEmpty { "Running…" }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "OBD Connection", NotificationManager.IMPORTANCE_LOW)
            .apply { description = "Live OBD2 connection status and sensor data" }
        nm.createNotificationChannel(channel)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_DISCONNECT = "com.obdiitools.ACTION_DISCONNECT"
        private const val CHANNEL_ID = "obd_service_channel"
        private const val NOTIFICATION_ID = 1001
    }
}
