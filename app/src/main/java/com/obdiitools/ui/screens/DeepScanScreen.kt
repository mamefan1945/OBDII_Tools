package com.obdiitools.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.obdiitools.obd.ConnectionState
import com.obdiitools.ui.theme.BackgroundCard
import com.obdiitools.ui.theme.BackgroundDeep
import com.obdiitools.ui.theme.NeonCyan
import com.obdiitools.ui.theme.NeonGreen
import com.obdiitools.ui.theme.NeonOrange
import com.obdiitools.ui.theme.NeonPurple
import com.obdiitools.ui.theme.NeonYellow
import com.obdiitools.ui.theme.SurfaceBorder
import com.obdiitools.ui.theme.TextPrimary
import com.obdiitools.ui.theme.TextSecondary
import com.obdiitools.viewmodel.DeepScanViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeepScanScreen(
    onNavigateBack: () -> Unit,
    onNavigateToVehicleDiscovery: () -> Unit = {},
    viewModel: DeepScanViewModel = hiltViewModel(),
) {
    val scanState by viewModel.scanState.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val isConnected = connectionState is ConnectionState.Connected
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val report = buildDeepScanReport(scanState)

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = BackgroundDeep,
        topBar = {
            CenterAlignedTopAppBar(
        windowInsets = WindowInsets(0, 0, 0, 0),
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "DEEP SCAN",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = TextPrimary,
                            letterSpacing = 1.sp,
                        )
                        if (scanState.results.isNotEmpty() || scanState.isScanning) {
                            Text(
                                scanState.phase,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                color = if (scanState.isScanning) NeonCyan else TextSecondary,
                                letterSpacing = 0.5.sp,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = NeonCyan)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = BackgroundCard),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // Progress bar during scan
            if (scanState.isScanning) {
                val (done, total) = scanState.progress ?: (0 to 1)
                LinearProgressIndicator(
                    progress = { if (total > 0) done.toFloat() / total else 0f },
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = NeonCyan,
                    trackColor = SurfaceBorder,
                )
            }

            if (!isConnected) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.BluetoothDisabled, null, tint = SurfaceBorder, modifier = Modifier.size(64.dp))
                        Spacer(Modifier.height(16.dp))
                        Text("Connect to a device", color = TextSecondary, fontFamily = FontFamily.Monospace, fontSize = 15.sp)
                        Text("to run a deep scan", color = TextSecondary.copy(alpha = 0.6f), fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                        Spacer(Modifier.height(18.dp))
                        OutlinedButton(
                            onClick = onNavigateToVehicleDiscovery,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan),
                        ) {
                            Text("VIEW SAVED VEHICLE PROFILES", fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                        }
                    }
                }
                return@Scaffold
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { Spacer(Modifier.height(8.dp)) }

                // Explanation / start panel
                if (scanState.results.isEmpty() && !scanState.isScanning) {
                    item {
                        DeepScanInfoPanel(
                            onStart = { viewModel.startScan() },
                            onProbeMazda = { viewModel.probeMazdaCx5Transmission() },
                            isProbingMazda = scanState.isProbingMazda,
                            onDiscoverMazdaDids = { viewModel.discoverMazdaTcmDids() },
                            isDiscoveringMazdaDids = scanState.isDiscoveringMazdaDids,
                        )
                    }
                }

                val mazdaProbeStatus = scanState.mazdaProbeStatus
                if (mazdaProbeStatus != null) {
                    item {
                        MazdaTransmissionProbeCard(mazdaProbeStatus, scanState.mazdaTransmission)
                    }
                }

                if (scanState.mazdaDidResults.isNotEmpty() || scanState.isDiscoveringMazdaDids) {
                    item { MazdaDidDiscoveryCard(scanState.mazdaDidResults, scanState.mazdaDidProgress) }
                }

                item {
                    OutlinedButton(onClick = onNavigateToVehicleDiscovery, modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan)) {
                        Text("VEHICLE DISCOVERY / PROFILES", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    }
                }

                // ECU result cards
                items(scanState.results, key = { it.address }) { ecuResult ->
                    EcuResultCard(ecuResult)
                }

                item { Spacer(Modifier.height(80.dp)) }
            }

            // Bottom action bar
            if (isConnected && (scanState.isScanning || scanState.results.isNotEmpty() || scanState.mazdaProbeStatus != null || scanState.mazdaDidResults.isNotEmpty() || scanState.isDiscoveringMazdaDids)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(BackgroundCard)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (!scanState.isScanning && report.isNotBlank()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            TextButton(onClick = { clipboardManager.setText(AnnotatedString(report)) }) {
                                Text("COPY REPORT", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = NeonCyan)
                            }
                            TextButton(
                                onClick = {
                                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_SUBJECT, "OBDII Tools Deep Scan Report")
                                        putExtra(Intent.EXTRA_TEXT, report)
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, "Share Deep Scan Report"))
                                },
                            ) {
                                Text("SHARE REPORT", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = NeonGreen)
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                    if (scanState.isScanning) {
                        OutlinedButton(
                            onClick = { viewModel.cancelScan() },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                        ) {
                            Text("CANCEL", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                        }
                    } else {
                        OutlinedButton(
                            onClick = { viewModel.probeMazdaCx5Transmission() },
                            enabled = !scanState.isProbingMazda && !scanState.isDiscoveringMazdaDids,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonOrange),
                        ) {
                            Text("MAZDA TCM", fontFamily = FontFamily.Monospace, fontSize = 9.sp)
                        }
                        OutlinedButton(
                            onClick = { viewModel.discoverMazdaTcmDids() },
                            enabled = !scanState.isDiscoveringMazdaDids && !scanState.isProbingMazda,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonYellow),
                        ) {
                            Text("DID DISCOVERY", fontFamily = FontFamily.Monospace, fontSize = 9.sp)
                        }
                        Button(
                            onClick = { viewModel.startScan() },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan.copy(alpha = 0.85f)),
                        ) {
                            Text("SCAN AGAIN", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = BackgroundDeep)
                        }
                    }
                    }
                }
            }
        }
    }
}

private fun buildDeepScanReport(state: DeepScanViewModel.ScanState): String {
    val lines = mutableListOf(
        "OBDII TOOLS — DEEP SCAN REPORT",
        "Status: ${state.phase.ifBlank { if (state.isScanning) "Scanning" else "No scan status" }}",
        "ECUs reported: ${state.results.size}",
        "Supported PID entries: ${state.results.sumOf { it.totalPids }}",
        "",
    ).apply {
        state.vehicleVin?.let { add("Vehicle VIN: $it") }
        state.vehicleVinSource?.let { add("VIN source: $it") }
        if (state.vehicleVin != null) add("")
    )
    state.mazdaProbeStatus?.let { status ->
        lines += "MAZDA TCM PROBE — 0x7E1"
        state.mazdaProbeCapturedAtMs?.let { lines += "Captured at: ${java.time.Instant.ofEpochMilli(it)}" }
        lines += "Requests: 22${com.obdiitools.obd.MazdaCx5Protocol.GEAR_DID} and 22${com.obdiitools.obd.MazdaCx5Protocol.TRANSMISSION_TEMPERATURE_DID}"
        lines += "Expected TCM response address: 0x7E9 (adapter response text does not expose CAN frame IDs)"
        lines += status
        state.mazdaTransmission?.let { data ->
            data.gear?.let { lines += "Decoded gear: $it" }
            data.transmissionTemperatureC?.let {
                lines += "Transmission fluid temperature: %.2f °C / %.2f °F".format(it, it * 9.0 / 5.0 + 32.0)
            }
            data.rawGearResponse?.takeIf { it.isNotBlank() }?.let { lines += "DID 1E12 raw: $it" }
            data.rawTemperatureResponse?.takeIf { it.isNotBlank() }?.let { lines += "DID 1E1C raw: $it" }
        }
        lines += ""
    }
    if (state.mazdaDidResults.isNotEmpty()) {
        lines += "MAZDA TCM DID DISCOVERY — request address 0x7E1"
        lines += "Expected response address: 0x7E9"
        lines += "Requests use read-only UDS ReadDataByIdentifier (service 0x22)."
        lines += "Raw values are adapter-returned response text, not captured CAN frames."
        state.mazdaDidResults.forEach { result ->
            lines += "DID ${result.did} — ${result.description}"
            lines += "Captured at: ${java.time.Instant.ofEpochMilli(result.capturedAtMs)}"
            lines += "Request: 0x${result.requestAddress} ${result.requestCommand}"
            lines += "Expected response address: 0x${result.expectedResponseAddress}"
            lines += "Status: ${result.status}"
            lines += "Adapter response: ${result.rawResponse}"
        }
        lines += ""
    }
    state.results.forEach { ecu ->
        lines += "0x${ecu.address} — ${ecu.addressHint ?: "Unidentified ECU"}"
        ecu.ecuVin?.let { lines += "VIN: $it" }
        ecu.identityStatus?.let { lines += "Identity: $it" }
        lines += "Mode 01 advertised PIDs (${ecu.mode01Pids.size}):"
        ecu.mode01Pids.forEach { lines += "  ${it.code} — ${it.name}" }
        lines += "Mode 01 verified decodable (two reads): ${ecu.validatedCount}"
        ecu.validatedMode01Pids.forEach { lines += "  ${it.code} — ${it.name}" }
        lines += "Mode 09 PIDs (${ecu.mode09Pids.size}):"
        ecu.mode09Pids.forEach { lines += "  ${it.code} — ${it.name}" }
        if (ecu.rawMode01Candidates.isNotEmpty()) {
            lines += "RAW MODE 01 CANDIDATES — advertised, positive twice, decoder not implemented:"
            ecu.rawMode01Candidates.forEach { candidate ->
                lines += "  ${candidate.code} — ${candidate.responseDataBytes} data bytes"
                lines += "    ${candidate.rawResponse}"
            }
        }
        lines += ""
    }
    return lines.joinToString("\n").trim()
}

@Composable
private fun DeepScanInfoPanel(
    onStart: () -> Unit,
    onProbeMazda: () -> Unit,
    isProbingMazda: Boolean,
    onDiscoverMazdaDids: () -> Unit,
    isDiscoveringMazdaDids: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "HOW IT WORKS",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                color = TextSecondary,
                letterSpacing = 2.sp,
            )
            InfoLine("1", "Scans 136 candidate 11-bit request addresses (0x700–0x77F and 0x7E0–0x7E7) for responding modules.")
            InfoLine("2", "Reads each ECU’s Mode 01 support bitmask, then validates advertised PIDs with two real responses and successful decoding. The report separates advertised from verified readings.")
            InfoLine("3", "Reuses the VIN retrieved during Bluetooth connection (standard Mode 09 PID 02, with UDS F190 fallback), then independently probes F190 on responding ECUs. Unsupported identifiers are reported, not guessed.")
            InfoLine("4", "Also samples up to 12 advertised Mode 01 PIDs per ECU that have no decoder yet. Two positive responses with matching payload lengths are saved as raw discovery leads.")
            Text(
                "This takes 2–5 minutes depending on how many ECUs respond.",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = TextSecondary.copy(alpha = 0.6f),
            )
        }
        OutlinedButton(
            onClick = onProbeMazda,
            enabled = !isProbingMazda,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonOrange),
        ) {
            Text(
                if (isProbingMazda) "PROBING MAZDA TCM…" else "PROBE 2016 MAZDA CX-5 TCM",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
            )
        }
        OutlinedButton(
            onClick = onDiscoverMazdaDids,
            enabled = !isDiscoveringMazdaDids,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonYellow),
        ) {
            Text(
                if (isDiscoveringMazdaDids) "DISCOVERING MAZDA TCM DIDs…" else "DISCOVER MAZDA TCM DATA IDs",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
            )
        }
        Button(
            onClick = onStart,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan.copy(alpha = 0.85f)),
        ) {
            Text(
                "START DEEP SCAN",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = BackgroundDeep,
                letterSpacing = 1.sp,
            )
        }
    }
}

@Composable
private fun MazdaDidDiscoveryCard(
    results: List<DeepScanViewModel.MazdaDidResult>,
    progress: Pair<Int, Int>?,
) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .border(1.dp, NeonYellow.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .background(NeonYellow.copy(alpha = 0.05f)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("MAZDA TCM DID DISCOVERY · 0x7E1", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 10.sp, color = NeonYellow)
        progress?.let { (done, total) ->
            Text("PROBING $done / $total", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = NeonCyan)
            LinearProgressIndicator(
                progress = { if (total > 0) done.toFloat() / total else 0f },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = NeonCyan,
                trackColor = SurfaceBorder,
            )
        }
        results.forEach { result ->
            Text("DID ${result.did} · ${result.description}", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 10.sp, color = TextPrimary)
            Text(result.status, fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = if (result.status.startsWith("POSITIVE")) NeonGreen else TextSecondary)
            Text("REQ 0x${result.requestAddress} ${result.requestCommand}  →  expected ECU 0x${result.expectedResponseAddress}", fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = TextSecondary)
            Text("TIME: ${java.time.Instant.ofEpochMilli(result.capturedAtMs)}", fontFamily = FontFamily.Monospace, fontSize = 8.sp, color = TextSecondary)
            Text("ADAPTER RESPONSE: ${result.rawResponse}", fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = TextSecondary)
        }
        Text("Only 1E12 and 1E1C are vehicle-verified. Other entries are catalogue leads. Adapter text is not a raw CAN-frame capture.", fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = TextSecondary)
    }
}

@Composable
private fun MazdaTransmissionProbeCard(
    status: String,
    data: com.obdiitools.obd.MazdaCx5TransmissionData?,
) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .border(1.dp, NeonOrange.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .background(NeonOrange.copy(alpha = 0.06f)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("MAZDA 6-SPEED AUTOMATIC · TCM 0x7E1", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 10.sp, color = NeonOrange)
        Text(status, fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = TextSecondary)
        data?.gear?.let { Text("Selected gear: $it", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = TextPrimary) }
        data?.transmissionTemperatureC?.let {
            Text("Transmission fluid temperature: %.1f °C / %.1f °F".format(it, it * 9.0 / 5.0 + 32.0), fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = TextPrimary)
        }
        data?.rawGearResponse?.takeIf { it.isNotBlank() }?.let { raw ->
            Text("DID 1E12 raw: " + raw.replace("\n", " / ").take(180), fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = TextSecondary)
        }
        data?.rawTemperatureResponse?.takeIf { it.isNotBlank() }?.let { raw ->
            Text("DID 1E1C raw: " + raw.replace("\n", " / ").take(180), fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = TextSecondary)
        }
        if (data?.gear == null && data?.transmissionTemperatureC == null) {
            Text("No decoded values. The TCM may not support these identifiers or may not be reachable through this adapter.", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = TextSecondary)
        }
    }
}

@Composable
private fun InfoLine(number: String, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(number, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = NeonCyan)
        Text(text, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = TextSecondary)
    }
}

@Composable
private fun EcuResultCard(result: DeepScanViewModel.EcuResult) {
    val accentColor = when {
        result.address == "7DF"                      -> NeonCyan
        result.address.uppercase() == "7E0"          -> NeonGreen
        result.address.uppercase() == "7E1"          -> NeonOrange
        result.address.uppercase() in setOf("7B0", "748") -> NeonPurple
        else                                         -> NeonYellow
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, accentColor.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .background(accentColor.copy(alpha = 0.05f)),
    ) {
        // ECU header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    "0x${result.address}",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = accentColor,
                )
                Text(
                    result.addressHint?.let { "Address hint · $it" } ?: "ECU function not identified",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = if (result.addressHint != null) TextPrimary else TextSecondary,
                )
                result.ecuVin?.let { vin ->
                    Text(
                        "VIN  $vin",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = NeonGreen,
                    )
                }
                result.identityStatus?.let { status ->
                    Text(
                        status,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        color = TextSecondary,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${result.totalPids} PIDs",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = accentColor,
                )
                if (result.mode09Pids.isNotEmpty()) {
                    Text(
                        "+${result.mode09Pids.size} info",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = TextSecondary,
                    )
                }
            }
        }

        // The support bitmap is an advertisement; the verified list is based
        // on two successful decoded reads from this ECU.
        if (result.mode01Pids.isNotEmpty()) {
            Text(
                "VERIFIED LIVE READS: ${result.validatedCount} / ${result.mode01Pids.size}",
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (result.validatedCount > 0) NeonGreen else NeonOrange,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
            )
            ModeSection("MODE 01 — ADVERTISED SUPPORT", result.mode01Pids, accentColor)
            if (result.validatedMode01Pids.isNotEmpty()) {
                ModeSection("MODE 01 — VERIFIED DECODABLE", result.validatedMode01Pids, NeonGreen)
            }
        }

        // Mode 09 PIDs
        if (result.mode09Pids.isNotEmpty()) {
            ModeSection("MODE 09 — VEHICLE INFO", result.mode09Pids, NeonPurple)
        }

        // Advertised Mode 01 PIDs without a decoder are sampled raw twice.
        // These are discovery leads, not dashboard-ready signals yet.
        if (result.rawMode01Candidates.isNotEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    "RAW DISCOVERY CANDIDATES — DECODER NEEDED",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp,
                    color = NeonYellow,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
                result.rawMode01Candidates.forEach { candidate ->
                    Text(
                        "${candidate.code} · ${candidate.responseDataBytes} data bytes · positive twice",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = TextPrimary,
                    )
                    Text(
                        candidate.rawResponse,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        color = TextSecondary,
                    )
                }
            }
        }

        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun ModeSection(
    title: String,
    pids: List<DeepScanViewModel.SupportedPid>,
    accentColor: androidx.compose.ui.graphics.Color,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp),
    ) {
        Text(
            title,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 9.sp,
            color = accentColor.copy(alpha = 0.7f),
            letterSpacing = 1.5.sp,
            modifier = Modifier.padding(vertical = 6.dp),
        )
        pids.forEach { pid ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(12.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(accentColor.copy(alpha = 0.5f)),
                )
                Text(
                    pid.code,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = accentColor,
                    modifier = Modifier.width(42.dp),
                )
                Text(
                    pid.name,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = TextSecondary,
                )
            }
        }
    }
}
