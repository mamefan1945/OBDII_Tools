package com.obdiitools.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.obdiitools.obd.BluetoothDeviceInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BluetoothDeviceScanner @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    val adapter: BluetoothAdapter? get() = bluetoothManager.adapter

    val isBluetoothEnabled: Boolean get() = adapter?.isEnabled == true

    @SuppressLint("MissingPermission")
    fun getPairedDevices(): List<BluetoothDeviceInfo> {
        return adapter?.bondedDevices?.map { device ->
            BluetoothDeviceInfo(
                name     = device.name ?: "Unknown",
                address  = device.address,
                isPaired = true,
                isBle    = device.type == BluetoothDevice.DEVICE_TYPE_LE,
            )
        } ?: emptyList()
    }

    @SuppressLint("MissingPermission")
    fun discoverDevices(): Flow<BluetoothDeviceInfo> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                when (intent.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val device: BluetoothDevice? =
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        device?.let {
                            trySend(
                                BluetoothDeviceInfo(
                                    name = it.name ?: "Unknown",
                                    address = it.address,
                                    isPaired = it.bondState == BluetoothDevice.BOND_BONDED,
                                )
                            )
                        }
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> close()
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        context.registerReceiver(receiver, filter)
        adapter?.startDiscovery()

        awaitClose {
            adapter?.cancelDiscovery()
            runCatching { context.unregisterReceiver(receiver) }
        }
    }

    @SuppressLint("MissingPermission")
    fun startBleScan(): Flow<BluetoothDeviceInfo> = callbackFlow {
        val seen = mutableSetOf<String>()
        val leScanner = adapter?.bluetoothLeScanner ?: run { close(); return@callbackFlow }
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device
                val address = device.address ?: return
                if (seen.add(address)) {
                    val name = device.name
                        ?: result.scanRecord?.deviceName
                        ?: "BLE Device"
                    trySend(
                        BluetoothDeviceInfo(
                            name     = name,
                            address  = address,
                            isPaired = device.bondState == BluetoothDevice.BOND_BONDED,
                            isBle    = true,
                        )
                    )
                }
            }

            override fun onScanFailed(errorCode: Int) {
                close(Exception("BLE scan failed: error $errorCode"))
            }
        }

        leScanner.startScan(null, settings, callback)
        launch { delay(30_000); close() }

        awaitClose { runCatching { leScanner.stopScan(callback) } }
    }

    @SuppressLint("MissingPermission")
    fun getRemoteDevice(address: String): BluetoothDevice? =
        adapter?.getRemoteDevice(address)
}
