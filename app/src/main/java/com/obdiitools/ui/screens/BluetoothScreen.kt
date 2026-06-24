package com.obdiitools.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Surface
import androidx.compose.ui.platform.LocalContext
import com.obdiitools.data.VinInfo
import com.obdiitools.obd.BluetoothDeviceInfo
import com.obdiitools.obd.ConnectionState
import com.obdiitools.ui.components.ConnectionStatusBar
import com.obdiitools.ui.theme.BackgroundDeep
import com.obdiitools.ui.theme.NeonCyan
import com.obdiitools.ui.theme.NeonGreen
import com.obdiitools.ui.theme.NeonOrange
import com.obdiitools.ui.theme.NeonRed
import com.obdiitools.ui.theme.SurfaceBorder
import com.obdiitools.ui.theme.SurfaceElevated
import com.obdiitools.ui.theme.TextPrimary
import com.obdiitools.ui.theme.TextSecondary
import com.obdiitools.viewmodel.MainViewModel

@Composable
fun BluetoothScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val connectionState by viewModel.connectionState.collectAsState()
    val bleDevices by viewModel.bleDevices.collectAsState()
    val bleScanning by viewModel.isBleScanRunning.collectAsState()
    val bleScanError by viewModel.bleScanError.collectAsState()
    val vinInfo by viewModel.vinInfo.collectAsState()
    val vinLoading by viewModel.vinLoading.collectAsState()
    var pairedDevices by remember { mutableStateOf<List<BluetoothDeviceInfo>>(emptyList()) }
    var permissionsGranted by remember { mutableStateOf(false) }
    val isConnected = connectionState is ConnectionState.Connected
    var pendingDevice by remember { mutableStateOf<BluetoothDeviceInfo?>(null) }

    val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN,
        )
    } else {
        arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION,
        )
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        permissionsGranted = granted.values.all { it }
        if (permissionsGranted) {
            pairedDevices = viewModel.getPairedDevices()
            viewModel.startBleScan()
        }
    }

    val locationPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        // Connect regardless of grant — GPS omitted if denied, OBD still works.
        pendingDevice?.let { viewModel.connect(it) }
        pendingDevice = null
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDeep)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 16.dp)
        ) {
            // Header
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                Text(
                    text = "BLUETOOTH",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = NeonCyan,
                    letterSpacing = 3.sp,
                )
                Text(
                    text = "Device Pairing",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp,
                    color = TextPrimary,
                )
            }

            Spacer(Modifier.height(8.dp))
            ConnectionStatusBar(state = connectionState)
            HorizontalDivider(color = SurfaceBorder, modifier = Modifier.padding(horizontal = 20.dp))
            Spacer(Modifier.height(16.dp))

            // Action buttons
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { launcher.launch(permissions) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan.copy(alpha = 0.15f)),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Default.BluetoothSearching, null, tint = NeonCyan, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(10.dp))
                    Text(
                        "SCAN FOR DEVICES",
                        color = NeonCyan,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                    )
                }

                Button(
                    onClick = {
                        context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        })
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan.copy(alpha = 0.08f)),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Default.Settings, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(10.dp))
                    Text(
                        "PAIR CLASSIC DEVICE",
                        color = TextSecondary,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                    )
                }

                if (connectionState is ConnectionState.Connected) {
                    Button(
                        onClick = { viewModel.disconnect() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = NeonRed.copy(alpha = 0.15f)),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Default.LinkOff, null, tint = NeonRed, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(10.dp))
                        Text(
                            "DISCONNECT",
                            color = NeonRed,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                        )
                    }
                }

                if (bleScanning) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp, color = NeonGreen)
                            Text("Scanning…", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = NeonGreen.copy(alpha = 0.7f))
                        }
                        TextButton(onClick = { viewModel.stopBleScan() }) {
                            Text("STOP", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = NeonRed, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (bleScanError != null) {
                    Text(
                        text = bleScanError!!,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = NeonRed,
                    )
                }
            }

            // VIN card — shown whenever connected
            AnimatedVisibility(
                visible = isConnected,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut(),
            ) {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    Spacer(Modifier.height(8.dp))
                    VinCard(
                        vinInfo = vinInfo,
                        loading = vinLoading,
                        onRetry = { viewModel.fetchVin() },
                    )
                }
            }

            val allDevices = remember(pairedDevices, bleDevices) {
                (pairedDevices + bleDevices).distinctBy { it.address }
            }

            Spacer(Modifier.height(24.dp))

            when {
                allDevices.isNotEmpty() -> {
                    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                        Text(
                            text = "AVAILABLE DEVICES",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = TextSecondary,
                            letterSpacing = 2.sp,
                        )
                        Spacer(Modifier.height(12.dp))
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(allDevices, key = { it.address }) { device ->
                                fun connectWith(isBle: Boolean) {
                                    val d = device.copy(isBle = isBle)
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                                        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                                            != PackageManager.PERMISSION_GRANTED) {
                                        pendingDevice = d
                                        locationPermLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                                    } else {
                                        viewModel.connect(d)
                                    }
                                }
                                DeviceRow(
                                    device = device,
                                    isConnected = connectionState is ConnectionState.Connected &&
                                            (connectionState as? ConnectionState.Connected)?.deviceAddress == device.address,
                                    isConnecting = connectionState is ConnectionState.Connecting &&
                                            (connectionState as? ConnectionState.Connecting)?.deviceAddress == device.address,
                                    onClick = { connectWith(device.isBle) },
                                    onConnectAs = { isBle -> connectWith(isBle) },
                                    onForget = if (!device.isPaired) {
                                        { viewModel.forgetBleDevice(device.address) }
                                    } else null,
                                    showBleBadge = device.isBle,
                                )
                            }
                            item { Spacer(Modifier.height(80.dp)) }
                        }
                    }
                }
                !permissionsGranted -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Bluetooth, null, tint = SurfaceBorder, modifier = Modifier.size(64.dp))
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "Tap Scan to find adapters",
                                color = TextSecondary,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 14.sp,
                            )
                        }
                    }
                }
                !bleScanning -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.BluetoothSearching, null, tint = SurfaceBorder, modifier = Modifier.size(64.dp))
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "No adapters found",
                                color = TextSecondary,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 14.sp,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Tap Scan to try again",
                                color = TextSecondary.copy(alpha = 0.6f),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VinCard(
    vinInfo: VinInfo?,
    loading: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceElevated)
            .border(1.dp, NeonCyan.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "VEHICLE IDENTITY",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    color = TextSecondary,
                    letterSpacing = 2.sp,
                )
                if (vinInfo != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(NeonCyan.copy(alpha = 0.12f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            "NHTSA",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 8.sp,
                            color = NeonCyan,
                            letterSpacing = 0.5.sp,
                        )
                    }
                }
            }

            when {
                loading -> Text(
                    "Fetching VIN via OBD…",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    color = TextSecondary.copy(alpha = 0.6f),
                )
                vinInfo == null -> {
                    Text(
                        "VIN not available on this vehicle",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        color = TextSecondary.copy(alpha = 0.5f),
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "RETRY",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        color = NeonCyan,
                        letterSpacing = 1.sp,
                        modifier = Modifier.clickable(onClick = onRetry),
                    )
                }
                else -> {
                    Text(
                        vinInfo.vin,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = NeonCyan,
                        letterSpacing = 1.sp,
                    )
                    val vehicleName = vinInfo.displayName
                    if (vehicleName != vinInfo.vin) {
                        Text(
                            vehicleName,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
                            color = TextPrimary,
                        )
                    }
                    if (vinInfo.trim.isNotBlank()) {
                        Text(
                            vinInfo.trim,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            color = TextSecondary,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DeviceRow(
    device: BluetoothDeviceInfo,
    isConnected: Boolean,
    isConnecting: Boolean,
    onClick: () -> Unit,
    onConnectAs: (isBle: Boolean) -> Unit,
    onForget: (() -> Unit)?,
    showBleBadge: Boolean = false,
) {
    val borderColor = when {
        isConnected  -> NeonGreen
        isConnecting -> NeonOrange
        else         -> SurfaceBorder
    }
    val iconColor = when {
        isConnected  -> NeonGreen
        isConnecting -> NeonOrange
        else         -> NeonCyan
    }
    var showMenu by remember { mutableStateOf(false) }

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceElevated)
                .border(1.dp, borderColor.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                .combinedClickable(
                    enabled = !isConnected && !isConnecting,
                    onClick = onClick,
                    onLongClick = { showMenu = true },
                )
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (isConnected) Icons.Default.BluetoothConnected else Icons.Default.Bluetooth,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(24.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = device.name,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = TextPrimary,
                    )
                    if (showBleBadge) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = NeonGreen.copy(alpha = 0.15f),
                            modifier = Modifier.padding(0.dp),
                        ) {
                            Text(
                                "BLE",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 8.sp,
                                color = NeonGreen,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
                Text(
                    text = device.address,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = TextSecondary,
                )
            }
            when {
                isConnected  -> Text("●  LIVE",  color = NeonGreen,  fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                isConnecting -> Text("○  WAIT",  color = NeonOrange, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                else         -> Icon(Icons.Default.Link, null, tint = TextSecondary, modifier = Modifier.size(16.dp))
            }
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
        ) {
            DropdownMenuItem(
                text = { Text("Connect as BLE", fontFamily = FontFamily.Monospace, fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.BluetoothSearching, null, tint = NeonGreen, modifier = Modifier.size(18.dp)) },
                onClick = { showMenu = false; onConnectAs(true) },
            )
            DropdownMenuItem(
                text = { Text("Connect as Classic BT", fontFamily = FontFamily.Monospace, fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Bluetooth, null, tint = NeonCyan, modifier = Modifier.size(18.dp)) },
                onClick = { showMenu = false; onConnectAs(false) },
            )
            if (onForget != null) {
                DropdownMenuItem(
                    text = { Text("Forget", fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = NeonRed) },
                    leadingIcon = { Icon(Icons.Default.LinkOff, null, tint = NeonRed, modifier = Modifier.size(18.dp)) },
                    onClick = { showMenu = false; onForget() },
                )
            }
        }
    }
}
