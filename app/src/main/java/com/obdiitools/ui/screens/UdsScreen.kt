package com.obdiitools.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.obdiitools.obd.COMMON_DIDS
import com.obdiitools.obd.ConnectionState
import com.obdiitools.obd.EcuScanResult
import com.obdiitools.obd.KnownEcu
import com.obdiitools.obd.UdsResponse
import com.obdiitools.ui.theme.BackgroundDeep
import com.obdiitools.ui.theme.NeonCyan
import com.obdiitools.ui.theme.NeonGreen
import com.obdiitools.ui.theme.NeonOrange
import com.obdiitools.ui.theme.NeonRed
import com.obdiitools.ui.theme.SurfaceBorder
import com.obdiitools.ui.theme.TextPrimary
import com.obdiitools.ui.theme.TextSecondary
import com.obdiitools.viewmodel.UdsViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun UdsScreen(
    onNavigateBack: () -> Unit,
    viewModel: UdsViewModel = hiltViewModel(),
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val isConnected = connectionState is ConnectionState.Connected
    val selectedEcu by viewModel.selectedEcu.collectAsState()
    val customAddress by viewModel.customAddress.collectAsState()
    val didInput by viewModel.didInput.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val responses by viewModel.responses.collectAsState()
    val scanResults by viewModel.scanResults.collectAsState()
    val scanProgress by viewModel.scanProgress.collectAsState()
    val isScanning = scanProgress != null

    Scaffold(
        containerColor = BackgroundDeep,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "UDS READER",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = TextPrimary,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = BackgroundDeep),
            )
        },
    ) { innerPadding ->
        if (!isConnected) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text("Not connected", color = TextSecondary, fontFamily = FontFamily.Monospace)
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Spacer(Modifier.height(4.dp))
                Text(
                    "SELECT ECU",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    color = TextSecondary,
                    letterSpacing = 2.sp,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    KnownEcu.entries.forEach { ecu ->
                        FilterChip(
                            selected = selectedEcu == ecu,
                            onClick = { viewModel.selectEcu(ecu) },
                            label = {
                                Text(
                                    ecu.label,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NeonCyan.copy(alpha = 0.15f),
                                selectedLabelColor = NeonCyan,
                            ),
                        )
                    }
                }

                if (selectedEcu == KnownEcu.CUSTOM) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = customAddress,
                        onValueChange = { viewModel.setCustomAddress(it) },
                        label = { Text("ECU Address (hex)", fontFamily = FontFamily.Monospace, fontSize = 11.sp) },
                        placeholder = { Text("e.g. 7E0", fontFamily = FontFamily.Monospace, fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp,
                            color = TextPrimary,
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = SurfaceBorder,
                            focusedLabelColor = NeonCyan,
                        ),
                    )
                }
            }

            // ── ECU Scanner ──────────────────────────────────────────────────
            item {
                HorizontalDivider(color = SurfaceBorder)
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            "ECU SCANNER",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = TextSecondary,
                            letterSpacing = 2.sp,
                        )
                        Text(
                            "Discover all modules on the CAN bus",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = TextSecondary.copy(alpha = 0.5f),
                        )
                    }
                    Button(
                        onClick = { viewModel.startEcuScan() },
                        enabled = !isScanning && !isLoading,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonGreen.copy(alpha = 0.15f),
                            disabledContainerColor = SurfaceBorder.copy(alpha = 0.2f),
                        ),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                    ) {
                        Text(
                            if (isScanning) "SCANNING…" else "SCAN ECUs",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = if (isScanning) TextSecondary else NeonGreen,
                        )
                    }
                }
                if (isScanning) {
                    val (done, total) = scanProgress ?: (0 to 128)
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { if (total > 0) done.toFloat() / total else 0f },
                        modifier = Modifier.fillMaxWidth(),
                        color = NeonGreen,
                        trackColor = SurfaceBorder.copy(alpha = 0.3f),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "$done / $total addresses",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = TextSecondary.copy(alpha = 0.6f),
                    )
                }
                if (scanResults.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "${scanResults.size} MODULE${if (scanResults.size != 1) "S" else ""} FOUND — tap to select",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        color = NeonGreen,
                        letterSpacing = 1.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }

            items(scanResults) { result ->
                EcuScanResultRow(
                    result = result,
                    isSelected = selectedEcu == KnownEcu.CUSTOM && customAddress == result.address,
                    onClick = { viewModel.selectScanResult(result) },
                )
            }

            if (scanResults.isNotEmpty()) {
                item { Spacer(Modifier.height(4.dp)) }
            }

            // ── DID section ───────────────────────────────────────────────────
            item {
                HorizontalDivider(color = SurfaceBorder)
                Spacer(Modifier.height(4.dp))
                Text(
                    "DATA IDENTIFIER (DID)",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    color = TextSecondary,
                    letterSpacing = 2.sp,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = didInput,
                    onValueChange = { viewModel.setDid(it) },
                    label = { Text("DID (4 hex digits)", fontFamily = FontFamily.Monospace, fontSize = 11.sp) },
                    placeholder = { Text("e.g. F190", fontFamily = FontFamily.Monospace, fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        color = TextPrimary,
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = SurfaceBorder,
                        focusedLabelColor = NeonCyan,
                    ),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "COMMON DIDs",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    color = TextSecondary,
                    letterSpacing = 2.sp,
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    COMMON_DIDS.forEach { (did, label) ->
                        val shortLabel = label.substringBefore(" (")
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (didInput == did) NeonCyan.copy(alpha = 0.15f)
                                    else SurfaceBorder.copy(alpha = 0.3f)
                                )
                                .clickable { viewModel.setDid(did) }
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                        ) {
                            Column {
                                Text(
                                    did,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = if (didInput == did) NeonCyan else TextPrimary,
                                )
                                Text(
                                    shortLabel,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 9.sp,
                                    color = TextSecondary,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { viewModel.sendRequest() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading && didInput.length == 4,
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan.copy(alpha = 0.8f)),
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .height(16.dp),
                            strokeWidth = 2.dp,
                            color = BackgroundDeep,
                        )
                    }
                    Text(
                        if (isLoading) "READING…" else "READ DID",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = BackgroundDeep,
                    )
                }
            }

            if (responses.isNotEmpty()) {
                item {
                    HorizontalDivider(color = SurfaceBorder)
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "RESULTS",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = TextSecondary,
                            letterSpacing = 2.sp,
                        )
                        Text(
                            "Clear",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = NeonCyan,
                            modifier = Modifier.clickable { viewModel.clearHistory() },
                        )
                    }
                }
                items(responses) { response ->
                    UdsResponseCard(response)
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun EcuScanResultRow(
    result: EcuScanResult,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val accent = if (result.hasPositiveResponse) NeonGreen else NeonOrange
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
            .background(if (isSelected) accent.copy(alpha = 0.12f) else SurfaceBorder.copy(alpha = 0.15f))
            .border(
                1.dp,
                if (isSelected) accent.copy(alpha = 0.5f) else SurfaceBorder.copy(alpha = 0.3f),
                androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "0x${result.address}",
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = accent,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                result.moduleName ?: "Unknown Module",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = if (result.moduleName != null) TextPrimary else TextSecondary,
            )
            Text(
                result.responseLabel,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = accent.copy(alpha = 0.7f),
            )
        }
        if (isSelected) {
            Text(
                "SELECTED",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp,
                color = accent,
                letterSpacing = 1.sp,
            )
        }
    }
}

@Composable
private fun UdsResponseCard(response: UdsResponse) {
    val borderColor = if (response.isSuccess) NeonGreen else NeonRed
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceBorder.copy(alpha = 0.2f))
            .padding(start = 3.dp)
            .background(BackgroundDeep)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    response.ecuAddress,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = NeonCyan,
                )
                Text(
                    "DID ${response.did}",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    color = TextSecondary,
                )
            }
            Text(
                if (response.isSuccess) "OK" else response.errorCode ?: "ERR",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = borderColor,
            )
        }
        if (response.isSuccess) {
            Text(
                response.displayHex,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = NeonOrange,
            )
            if (response.asAscii.isNotBlank()) {
                Text(
                    "\"${response.asAscii}\"",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = TextSecondary,
                )
            }
        } else {
            Text(
                response.errorDescription ?: "Unknown error",
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = NeonRed.copy(alpha = 0.8f),
            )
        }
    }
}
