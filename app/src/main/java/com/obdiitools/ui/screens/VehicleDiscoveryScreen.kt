package com.obdiitools.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.obdiitools.data.VehicleDiscoveryProfile
import com.obdiitools.data.VehicleDiscoveryItem
import com.obdiitools.ui.theme.*
import com.obdiitools.viewmodel.VehicleDiscoveryViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleDiscoveryScreen(
    onBack: () -> Unit,
    viewModel: VehicleDiscoveryViewModel = hiltViewModel(),
) {
    val profiles by viewModel.profiles.collectAsState()
    Scaffold(
        containerColor = BackgroundDeep,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "SAVED VEHICLE PROFILES",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = TextPrimary,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = NeonCyan)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = BackgroundCard),
            )
        },
    ) { padding ->
        if (profiles.isEmpty()) {
            Box(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "No saved vehicle profiles. Run Deep Scan while connected to a vehicle to build its profile.",
                    color = TextSecondary,
                    fontFamily = FontFamily.Monospace,
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding).padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 14.dp),
            ) {
                item {
                    Text(
                        "VIN-identified vehicles are grouped together. Scans without a VIN remain separate. " +
                            "Open a profile to inspect its stored vehicle and ECU details.",
                        color = TextSecondary,
                        fontSize = 11.sp,
                    )
                }
                items(profiles, key = { it.id }) { profile ->
                    ProfileCard(
                        profile = profile,
                        onDelete = { viewModel.deleteProfile(profile.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileCard(
    profile: VehicleDiscoveryProfile,
    onDelete: () -> Unit,
) {
    var detailsVisible by remember(profile.id) { mutableStateOf(false) }
    var confirmDelete by remember(profile.id) { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US) }
    val vehicleName = listOf(profile.year, profile.make, profile.model)
        .filter { it.isNotBlank() }
        .joinToString(" ")
        .ifBlank { "Unidentified vehicle" }

    Card(
        colors = CardDefaults.cardColors(containerColor = BackgroundCard),
        border = BorderStroke(1.dp, SurfaceBorder),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Text(
                vehicleName,
                color = TextPrimary,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
            )
            Text(
                if (profile.vin.isNotBlank()) "VIN: ${profile.vin}" else "VIN unavailable — scan stored separately",
                color = TextSecondary,
                fontSize = 10.sp,
            )
            Text(
                "Last scan: ${dateFormat.format(Date(profile.lastSeenMs))} • ${profile.ecuCount} ECUs",
                color = TextSecondary,
                fontSize = 10.sp,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Count("KNOWN", profile.knownCount, NeonGreen, Modifier.weight(1f))
                Count("UNKNOWN", profile.unknownCount, NeonOrange, Modifier.weight(1f))
                Count("TOTAL", profile.items.size, NeonCyan, Modifier.weight(1f))
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Default.Delete, null, tint = NeonRed, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("DELETE PROFILE", color = NeonRed, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                }
                TextButton(onClick = { detailsVisible = !detailsVisible }) {
                    Text(
                        if (detailsVisible) "HIDE DETAILS" else "VIEW DETAILS",
                        color = NeonCyan,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                    )
                }
            }
            if (detailsVisible) {
                HorizontalDivider(color = SurfaceBorder)
                DetailRow("PROFILE ID", profile.id)
                DetailRow("YEAR", profile.year.ifBlank { "Not identified" })
                DetailRow("MAKE", profile.make.ifBlank { "Not identified" })
                DetailRow("MODEL", profile.model.ifBlank { "Not identified" })
                DetailRow("VIN", profile.vin.ifBlank { "Unavailable" })
                DetailRow("LAST UPDATED", dateFormat.format(Date(profile.lastSeenMs)))
                DetailRow("ECUs DISCOVERED", profile.ecuCount.toString())
                DetailRow("KNOWN ITEMS", profile.knownCount.toString())
                DetailRow("UNKNOWN ITEMS", profile.unknownCount.toString())
                HorizontalDivider(color = SurfaceBorder)
                Text(
                    "DISCOVERED ITEMS",
                    color = NeonCyan,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                )
                if (profile.items.isEmpty()) {
                    Text("No individual items stored in this profile.", color = TextSecondary, fontSize = 10.sp)
                } else {
                    profile.items.sortedWith(
                        compareBy<VehicleDiscoveryItem> { !it.known }
                            .thenBy { it.category }
                            .thenBy { it.key },
                    ).forEach { item ->
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    item.name.ifBlank { item.key },
                                    color = TextPrimary,
                                    fontSize = 11.sp,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    if (item.known) "KNOWN" else "UNKNOWN",
                                    color = if (item.known) NeonGreen else NeonOrange,
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace,
                                )
                            }
                            Text(
                                "${item.category} • ECU 0x${item.ecuAddress} • ${item.status}",
                                color = TextSecondary,
                                fontSize = 9.sp,
                            )
                            if (item.rawResponse.isNotBlank()) {
                                Text(
                                    "Raw: ${item.rawResponse}",
                                    color = TextSecondary,
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace,
                                )
                            }
                            Text(
                                "Updated: ${dateFormat.format(Date(item.updatedAtMs))}",
                                color = TextSecondary.copy(alpha = 0.75f),
                                fontSize = 8.sp,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete saved profile?") },
            text = {
                Text(
                    "This permanently removes the saved discovery profile for $vehicleName. " +
                        "Recorded session history and exported session files are not deleted.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDelete()
                    },
                ) {
                    Text("DELETE", color = NeonRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text("CANCEL", color = NeonCyan)
                }
            },
            containerColor = BackgroundCard,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(label, color = TextSecondary, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
        Spacer(Modifier.width(12.dp))
        Text(
            value,
            color = TextPrimary,
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
        )
    }
}

@Composable
private fun Count(label: String, count: Int, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier.background(BackgroundDeep, RoundedCornerShape(8.dp)).padding(9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "$count",
            color = color,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
        )
        Text(label, color = TextSecondary, fontFamily = FontFamily.Monospace, fontSize = 8.sp)
    }
}
