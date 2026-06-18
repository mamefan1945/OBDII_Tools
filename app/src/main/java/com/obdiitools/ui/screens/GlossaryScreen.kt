package com.obdiitools.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.obdiitools.ui.theme.BackgroundCard
import com.obdiitools.ui.theme.BackgroundDeep
import com.obdiitools.ui.theme.NeonCyan
import com.obdiitools.ui.theme.NeonGreen
import com.obdiitools.ui.theme.NeonOrange
import com.obdiitools.ui.theme.NeonPurple
import com.obdiitools.ui.theme.NeonRed
import com.obdiitools.ui.theme.NeonYellow
import com.obdiitools.ui.theme.SurfaceBorder
import com.obdiitools.ui.theme.TextPrimary
import com.obdiitools.ui.theme.TextSecondary

private data class GlossaryEntry(
    val term: String,
    val expansion: String? = null,
    val description: String,
    val appNote: String? = null,
)

private data class GlossaryCategory(
    val title: String,
    val color: Color,
    val entries: List<GlossaryEntry>,
)

private val GLOSSARY: List<GlossaryCategory> = listOf(

    GlossaryCategory("STANDARDS & INTERFACES", NeonCyan, listOf(
        GlossaryEntry(
            term = "OBD-II",
            expansion = "On-Board Diagnostics II",
            description = "The mandatory diagnostic system built into every car sold in the US after 1996 (and most cars worldwide after 2001). It defines a standard connector, standard commands, and standard fault codes so any compatible tool can talk to any compliant vehicle.",
            appNote = "This entire app communicates via OBD-II.",
        ),
        GlossaryEntry(
            term = "SAE J1979",
            expansion = "Society of Automotive Engineers Standard",
            description = "The technical document that defines exactly what OBD-II commands exist, what data they return, and how to parse it. Think of it as the rulebook OBD-II follows.",
        ),
        GlossaryEntry(
            term = "ISO 15031",
            description = "The international version of SAE J1979. Nearly identical — the two standards were harmonized so the same tool works on American and European vehicles.",
        ),
        GlossaryEntry(
            term = "UDS",
            expansion = "Unified Diagnostic Services (ISO 14229)",
            description = "An advanced diagnostic protocol layered on top of CAN Bus that gives access to manufacturer-specific data beyond standard OBD-II. Uses service codes and Data Identifiers (DIDs) instead of PIDs.",
            appNote = "Used by the UDS Reader screen and the Deep Scan for ECU-specific data.",
        ),
    )),

    GlossaryCategory("HARDWARE", NeonGreen, listOf(
        GlossaryEntry(
            term = "ELM327",
            description = "A microcontroller chip designed by Elm Electronics that acts as a translator between your car's OBD-II port and a digital interface (Bluetooth, Wi-Fi, or USB). Your adapter has one of these chips inside. It handles the low-level protocol details so the app only needs to send simple text commands.",
            appNote = "The app communicates with the ELM327 chip using AT commands (ATSP0, ATH0, etc.).",
        ),
        GlossaryEntry(
            term = "OBD-II Port",
            description = "A standardized 16-pin trapezoidal connector located under the dashboard, usually near the steering column. Mandated by law in all US vehicles since 1996. Your adapter plugs directly into this port.",
        ),
        GlossaryEntry(
            term = "Adapter",
            description = "Your ELM327-based Bluetooth dongle. It draws power from the OBD-II port and creates a wireless link between the car's data bus and your phone.",
        ),
    )),

    GlossaryCategory("WIRELESS PROTOCOLS", NeonOrange, listOf(
        GlossaryEntry(
            term = "Bluetooth SPP",
            expansion = "Serial Port Profile",
            description = "Classic Bluetooth mode that emulates a serial cable. Your phone and the adapter agree to pretend they're connected by a wire, sending raw bytes back and forth. Most ELM327 adapters use SPP.",
            appNote = "The app connects automatically using SPP when you tap a paired device.",
        ),
        GlossaryEntry(
            term = "BLE",
            expansion = "Bluetooth Low Energy",
            description = "A newer Bluetooth mode designed for low power consumption. BLE adapters (like VLINK, OBDLink CX) use a notification-based system instead of a direct serial stream, which requires different connection handling.",
            appNote = "The app supports both SPP and BLE adapters. BLE devices appear in the Bluetooth scan results.",
        ),
    )),

    GlossaryCategory("VEHICLE DATA BUS PROTOCOLS", NeonPurple, listOf(
        GlossaryEntry(
            term = "CAN Bus",
            expansion = "Controller Area Network",
            description = "The internal network that all computers in your car use to communicate with each other. Introduced in the 1990s and mandatory in US vehicles since 2008. Think of it as the car's ethernet — hundreds of messages per second flow between the engine, transmission, ABS, airbags, and every other module.",
        ),
        GlossaryEntry(
            term = "ISO-TP",
            expansion = "ISO Transport Protocol (ISO 15765-2)",
            description = "A protocol that runs on top of CAN Bus to handle messages larger than 8 bytes by splitting them into multiple CAN frames. The ELM327 chip handles ISO-TP reassembly automatically — the app just sees complete responses.",
        ),
        GlossaryEntry(
            term = "ISO 9141-2",
            description = "An older OBD-II serial protocol used by many European and Asian vehicles before CAN Bus became dominant. Recognizable by its slower, single-wire communication. Used in vehicles roughly pre-2008.",
        ),
        GlossaryEntry(
            term = "KWP2000",
            expansion = "Keyword Protocol 2000 (ISO 14230)",
            description = "Another older OBD-II protocol common in European vehicles. More capable than ISO 9141 but still single-wire. Also being replaced by CAN Bus in newer vehicles.",
        ),
        GlossaryEntry(
            term = "ATSP",
            expansion = "AT Set Protocol",
            description = "An ELM327 command that tells the adapter which OBD-II protocol to use. ATSP0 (auto-detect) lets the adapter try each protocol until one works — the right choice for most vehicles.",
            appNote = "The app sends ATSP0 during initialization so it works with any vehicle.",
        ),
    )),

    GlossaryCategory("VEHICLE COMPUTERS (ECUs)", NeonYellow, listOf(
        GlossaryEntry(
            term = "ECU",
            expansion = "Electronic Control Unit",
            description = "A generic name for any computer module in your car. Modern vehicles have 50–100 ECUs. They communicate over the CAN bus and each controls a specific system.",
        ),
        GlossaryEntry(
            term = "PCM / ECM",
            expansion = "Powertrain Control Module / Engine Control Module",
            description = "The main engine computer — controls fuel injection, ignition timing, idle speed, emissions systems, and often the transmission. This is the ECU that responds to standard OBD-II requests.",
            appNote = "Default OBD-II address 0x7E0. The source of most live data in this app.",
        ),
        GlossaryEntry(
            term = "TCM",
            expansion = "Transmission Control Module",
            description = "Controls the automatic transmission — shifting points, torque converter lockup, and on CVTs, the belt/pulley ratio. Usually communicates on the same CAN bus as the PCM.",
            appNote = "Address 0x7E1. The CVT Monitor screen attempts to read TCM data.",
        ),
        GlossaryEntry(
            term = "BCM",
            expansion = "Body Control Module",
            description = "Handles body electronics — power windows, door locks, interior lighting, key fobs, and horn. Not usually accessible via standard OBD-II but may respond to UDS requests.",
        ),
        GlossaryEntry(
            term = "ABS Module",
            expansion = "Anti-lock Braking System",
            description = "Monitors individual wheel speed sensors and modulates brake pressure to prevent wheel lockup during hard braking. Also provides wheel speed data used by the stability control system.",
        ),
        GlossaryEntry(
            term = "SRS Module",
            expansion = "Supplemental Restraint System",
            description = "Controls airbags and seatbelt pretensioners. Has its own DTC system separate from OBD-II powertrain codes.",
        ),
    )),

    GlossaryCategory("OBD-II DIAGNOSTIC CONCEPTS", NeonRed, listOf(
        GlossaryEntry(
            term = "PID",
            expansion = "Parameter ID",
            description = "A numeric code that identifies a specific piece of real-time data you can request from the car. For example, PID 0x0C = Engine RPM, 0x05 = Coolant Temperature, 0x11 = Throttle Position. Standard PIDs are defined by SAE J1979; manufacturers add their own non-standard ones.",
            appNote = "The Dashboard and All Parameters screens poll supported PIDs continuously.",
        ),
        GlossaryEntry(
            term = "Mode",
            description = "OBD-II divides requests into numbered service modes:\n" +
                "• Mode 01 — Current live sensor data\n" +
                "• Mode 02 — Freeze frame data\n" +
                "• Mode 03 — Read stored DTCs\n" +
                "• Mode 04 — Clear DTCs\n" +
                "• Mode 06 — On-board monitor test results\n" +
                "• Mode 07 — Pending DTCs\n" +
                "• Mode 09 — Vehicle information (VIN, calibration IDs)\n" +
                "• Mode 22 — UDS manufacturer-specific data (non-standard)",
            appNote = "This app uses Modes 01, 02, 03, 04, 09, and 22 (UDS).",
        ),
        GlossaryEntry(
            term = "DTC",
            expansion = "Diagnostic Trouble Code",
            description = "A 5-character code (e.g., P0300) stored by the car when it detects a fault. The first character indicates the system: P = Powertrain, B = Body, C = Chassis, U = Network. The second character (0 = generic, 1 = manufacturer) indicates if it's standardized. The remaining 3 digits identify the specific fault.",
            appNote = "The Faults tab scans for, displays, and clears DTCs.",
        ),
        GlossaryEntry(
            term = "MIL",
            expansion = "Malfunction Indicator Light",
            description = "The \"Check Engine\" light on your dashboard. It illuminates when at least one confirmed (non-pending) DTC is stored. Clearing DTCs turns it off, but it will return if the underlying fault is still present.",
        ),
        GlossaryEntry(
            term = "Pending DTC",
            description = "A fault detected during the current or last drive cycle but not yet confirmed. Pending codes don't turn on the MIL. If the fault occurs again in a subsequent drive cycle, it becomes a stored (confirmed) DTC and the MIL activates.",
        ),
        GlossaryEntry(
            term = "Readiness Monitors",
            description = "Self-tests the car's computer runs to verify each emissions system is working correctly. Must run to completion (become \"ready\" or \"complete\") before the car can pass a smog/emissions inspection. Examples: Catalyst Monitor, O2 Sensor Monitor, Evap System Monitor.",
            appNote = "The Readiness screen shows which monitors are complete (green) or incomplete (orange).",
        ),
        GlossaryEntry(
            term = "Freeze Frame",
            description = "A snapshot of sensor values captured at the exact moment a DTC was set. Contains the RPM, speed, coolant temp, fuel trim, and other parameters present when the fault occurred — invaluable for diagnosing intermittent problems.",
            appNote = "The Freeze Frame screen shows this data for each stored DTC.",
        ),
        GlossaryEntry(
            term = "DID",
            expansion = "Data Identifier",
            description = "A 4-hex-digit code used in UDS (Mode 22) to request specific data from an ECU. Unlike OBD-II PIDs, DIDs are manufacturer-defined. Standard DIDs include F190 (VIN), F18C (ECU serial number), F197 (system name).",
            appNote = "Entered in the UDS Reader screen. Common DIDs are listed as quick-select chips.",
        ),
        GlossaryEntry(
            term = "NRC",
            expansion = "Negative Response Code",
            description = "A hex code returned by the ECU when a UDS request fails, explaining why. Common codes:\n" +
                "• 0x11 — Service Not Supported\n" +
                "• 0x12 — Sub-function Not Supported\n" +
                "• 0x22 — Conditions Not Correct (e.g., engine must be running)\n" +
                "• 0x31 — Request Out of Range\n" +
                "• 0x33 — Security Access Denied",
            appNote = "Shown in red in UDS Reader results.",
        ),
    )),

    GlossaryCategory("ENGINE & SENSOR ABBREVIATIONS", NeonCyan, listOf(
        GlossaryEntry(
            term = "RPM",
            expansion = "Revolutions Per Minute",
            description = "Engine speed — how many times the crankshaft completes a full rotation each minute. Idle is typically 600–900 RPM; redline is usually 6,000–8,000 RPM.",
        ),
        GlossaryEntry(
            term = "MAF",
            expansion = "Mass Air Flow",
            description = "Sensor measuring the mass of air entering the engine (grams per second). The ECU uses this value to calculate exactly how much fuel to inject for the correct air/fuel ratio.",
            appNote = "Shown in g/s or lb/min depending on your unit preference.",
        ),
        GlossaryEntry(
            term = "MAP",
            expansion = "Manifold Absolute Pressure",
            description = "Measures the pressure inside the intake manifold (kPa). Some engines use MAP instead of — or in addition to — MAF to estimate air intake. Lower pressure = more vacuum = less load.",
        ),
        GlossaryEntry(
            term = "IAT",
            expansion = "Intake Air Temperature",
            description = "The temperature of air entering the engine. Cold air is denser and contains more oxygen, so the ECU adjusts fuel injection when IAT is unusually high or low.",
        ),
        GlossaryEntry(
            term = "ECT",
            expansion = "Engine Coolant Temperature",
            description = "Temperature of the coolant circulating through the engine block. The ECU enriches the fuel mixture when cold (below ~80°C) and uses this to control the electric cooling fan.",
            appNote = "Shown on the Dashboard. An alert fires if it exceeds your configured threshold.",
        ),
        GlossaryEntry(
            term = "TPS",
            expansion = "Throttle Position Sensor",
            description = "Reports the throttle plate opening as a percentage (0% = fully closed/idle, 100% = wide open throttle). Directly corresponds to how far you press the accelerator.",
        ),
        GlossaryEntry(
            term = "O2 Sensor",
            expansion = "Oxygen Sensor (Lambda Sensor)",
            description = "Measures oxygen content in the exhaust to determine if combustion was rich (too much fuel) or lean (too little fuel). Upstream sensors (before the catalytic converter) are used for fuel control; downstream sensors (after) verify catalyst efficiency.",
            appNote = "Bank 1 = side with cylinder 1. Bank 2 = opposite side (V-engines only).",
        ),
        GlossaryEntry(
            term = "STFT",
            expansion = "Short-Term Fuel Trim",
            description = "The ECU's immediate, moment-to-moment adjustment to the fuel mixture, expressed as a percentage (±25%). Positive = adding fuel (running lean), negative = removing fuel (running rich). Should hover near 0% at steady cruise.",
            appNote = "Shown in the All Parameters screen under Fuel category.",
        ),
        GlossaryEntry(
            term = "LTFT",
            expansion = "Long-Term Fuel Trim",
            description = "The ECU's learned, persistent adjustment built up over many drive cycles. If STFT corrections are consistently positive, the LTFT absorbs that bias. Values consistently beyond ±10% suggest a fuel system issue (vacuum leak, injector, O2 sensor).",
        ),
        GlossaryEntry(
            term = "EGR",
            expansion = "Exhaust Gas Recirculation",
            description = "A system that routes a portion of exhaust gas back into the intake manifold. Diluting the fresh charge reduces peak combustion temperatures, which lowers NOx (nitrogen oxide) emissions.",
        ),
        GlossaryEntry(
            term = "VIN",
            expansion = "Vehicle Identification Number",
            description = "A unique 17-character code that permanently identifies your vehicle. Encodes the country of manufacture, manufacturer, vehicle type, check digit, model year, assembly plant, and production sequence number.",
            appNote = "Read automatically on connect via OBD Mode 09 (PID 0x02) with NHTSA decode for make/model/year.",
        ),
    )),

    GlossaryCategory("TRANSMISSION", NeonOrange, listOf(
        GlossaryEntry(
            term = "CVT",
            expansion = "Continuously Variable Transmission",
            description = "A type of automatic transmission that uses a steel belt running between two variable-diameter pulleys instead of fixed gear ratios. The ratio changes continuously and smoothly, keeping the engine at the most efficient RPM for any given speed.",
            appNote = "The CVT Monitor screen infers ratio, slip, and lockup state from RPM/speed data.",
        ),
        GlossaryEntry(
            term = "Torque Converter",
            expansion = "Used in conventional automatics",
            description = "A fluid coupling between the engine and transmission that allows the engine to keep running when the car is stopped. At cruising speed, a lockup clutch engages to create a direct mechanical connection for efficiency.",
        ),
        GlossaryEntry(
            term = "Drive Ratio",
            description = "The relationship between engine RPM and output shaft RPM. In a CVT, this is calculated in real time by the TCM and varies continuously. A high ratio (e.g., 2.5:1) means the engine spins 2.5× faster than the output — used for acceleration. A low ratio (e.g., 0.5:1) is used at highway cruising speed for efficiency.",
        ),
    )),

    GlossaryCategory("IN THIS APP", NeonGreen, listOf(
        GlossaryEntry(
            term = "Dashboard",
            description = "The main instrumentation screen showing animated gauges for the most critical live data — RPM, speed, coolant temperature, and more. Values update every second while connected.",
        ),
        GlossaryEntry(
            term = "All Parameters",
            description = "A complete list of all standard OBD-II Mode 01 PIDs. On first open, runs a one-time discovery scan to find which PIDs your car actually supports, then continuously updates those values. Unsupported PIDs are shown as N/A.",
            appNote = "Use the filter icon to hide unsupported PIDs after discovery.",
        ),
        GlossaryEntry(
            term = "Deep Scan",
            description = "A comprehensive discovery tool that finds all responding ECUs on the CAN bus, then uses the OBD-II PID support bitmask to enumerate exactly which PIDs each ECU supports. More thorough than the standard All Parameters scan because it checks per-ECU, not just broadcast.",
        ),
        GlossaryEntry(
            term = "Sessions",
            description = "Recorded drive logs. The app captures sensor snapshots every few seconds while connected and saves them. You can review past sessions and view line graphs of RPM, speed, coolant temperature, and other values over time.",
        ),
        GlossaryEntry(
            term = "Readiness Screen",
            description = "Displays the status of OBD-II readiness monitors — the self-tests the car must complete before it can pass an emissions inspection. Green = complete/ready, orange = incomplete/not ready.",
        ),
        GlossaryEntry(
            term = "Freeze Frame Screen",
            description = "Shows the sensor snapshot captured when each DTC was set. Helps answer \"what was the car doing when this fault happened?\"",
        ),
        GlossaryEntry(
            term = "CAN Monitor",
            description = "A raw frame sniffer that uses the ELM327's ATMA (Monitor All) command to display every CAN Bus message in real time. Useful for advanced reverse-engineering of proprietary data.",
        ),
        GlossaryEntry(
            term = "UDS Reader",
            description = "Sends UDS Mode 22 (ReadDataByIdentifier) requests to specific ECUs. Enter an ECU address and a 4-digit DID to read manufacturer-specific data not available through standard OBD-II.",
        ),
        GlossaryEntry(
            term = "Custom PIDs",
            description = "Define your own OBD-II Mode 01 PIDs with a hex command and parse formula. Useful for manufacturer-specific or extended PIDs not included in the standard set. Custom PIDs are polled alongside the built-in slow PIDs.",
        ),
    )),
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun GlossaryScreen(onNavigateBack: () -> Unit) {
    Scaffold(
        containerColor = BackgroundDeep,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "GLOSSARY",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = TextPrimary,
                        letterSpacing = 1.sp,
                    )
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
        ) {
            item {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Terminology reference for abbreviations and concepts used in this app and in automotive diagnostics generally.",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
            }

            GLOSSARY.forEach { category ->
                stickyHeader(key = "cat_${category.title}") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(BackgroundDeep.copy(alpha = 0.97f))
                            .padding(vertical = 10.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height(16.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(category.color),
                            )
                            Text(
                                category.title,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                color = category.color,
                                letterSpacing = 2.sp,
                            )
                        }
                    }
                }

                items(category.entries, key = { "${category.title}_${it.term}" }) { entry ->
                    GlossaryEntryCard(entry, category.color)
                    Spacer(Modifier.height(8.dp))
                }

                item(key = "spacer_${category.title}") { Spacer(Modifier.height(8.dp)) }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun GlossaryEntryCard(entry: GlossaryEntry, accentColor: Color) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(accentColor.copy(alpha = 0.06f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                entry.term,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = accentColor,
                modifier = Modifier.weight(1f),
            )
        }
        if (entry.expansion != null) {
            Text(
                entry.expansion,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = TextSecondary,
                letterSpacing = 0.3.sp,
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            entry.description,
            fontFamily = FontFamily.Default,
            fontSize = 13.sp,
            color = TextPrimary.copy(alpha = 0.85f),
        )
        if (entry.appNote != null) {
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(SurfaceBorder.copy(alpha = 0.4f))
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    "IN APP",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 8.sp,
                    color = accentColor,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(top = 1.dp),
                )
                Text(
                    entry.appNote,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = TextSecondary,
                )
            }
        }
    }
}
