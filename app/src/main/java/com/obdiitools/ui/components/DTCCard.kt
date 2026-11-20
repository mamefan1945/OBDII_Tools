package com.obdiitools.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.obdiitools.obd.DTC
import com.obdiitools.obd.DTCDatabase
import com.obdiitools.ui.theme.NeonOrange
import com.obdiitools.ui.theme.NeonRed
import com.obdiitools.ui.theme.NeonYellow
import com.obdiitools.ui.theme.TextPrimary
import com.obdiitools.ui.theme.TextSecondary

@Composable
fun DTCCard(dtc: DTC, modifier: Modifier = Modifier) {
    val severity = DTCDatabase.getSeverity(dtc.code)
    val severityColor = when (severity) {
        DTCDatabase.Severity.HIGH -> NeonRed
        DTCDatabase.Severity.MEDIUM -> NeonOrange
        DTCDatabase.Severity.LOW -> NeonYellow
    }
    val details = dtc.details
    val title = details?.title?.takeIf { it.isNotBlank() } ?: dtc.description

    GlassCard(
        modifier = modifier.fillMaxWidth(),
        accentColor = severityColor,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = severityColor,
                modifier = Modifier.size(20.dp).padding(top = 2.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = dtc.code,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = severityColor,
                    )
                    if (dtc.isPending) {
                        Text(
                            text = "PENDING",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium,
                            fontSize = 9.sp,
                            color = NeonYellow,
                        )
                    }
                }
                if (title.isNotBlank()) {
                    Text(title, fontSize = 13.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
                }
                if (details != null && details.description.isNotBlank()) {
                    Text(details.description, fontSize = 12.sp, color = TextSecondary)
                } else if (details == null && dtc.description == "Unknown fault code") {
                    Text(
                        if (dtc.code.length == 5 && dtc.code[1] == '1')
                            "Manufacturer-specific code. A verified make/model/ECU-specific definition is needed."
                        else
                            "No detailed definition is available in the generic code database.",
                        fontSize = 12.sp,
                        color = TextSecondary,
                    )
                }
                Text(
                    text = when (severity) {
                        DTCDatabase.Severity.HIGH -> "High severity"
                        DTCDatabase.Severity.MEDIUM -> "Medium severity"
                        DTCDatabase.Severity.LOW -> "Severity not established"
                    },
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = TextSecondary,
                )
                if (details != null && details.commonCauses.isNotEmpty()) {
                    Text(
                        "POSSIBLE CAUSES — NOT A CONFIRMED DIAGNOSIS",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp,
                        color = NeonOrange,
                    )
                    details.commonCauses.take(5).forEach { cause ->
                        Text(
                            "• ${cause.label} (${cause.likelihood})",
                            fontSize = 12.sp,
                            color = TextPrimary,
                        )
                    }
                }
                if (details != null && details.symptoms.isNotEmpty()) {
                    Text(
                        "COMMON SYMPTOMS",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp,
                        color = TextSecondary,
                    )
                    details.symptoms.take(4).forEach { symptom ->
                        Text("• $symptom", fontSize = 12.sp, color = TextPrimary)
                    }
                }
                if (details != null && (details.repairDifficulty != null || details.diyPossible != null)) {
                    val repairText = listOfNotNull(
                        details.repairDifficulty?.let { "Repair difficulty: $it" },
                        details.diyPossible?.let { if (it) "DIY may be possible" else "Professional service may be required" },
                    ).joinToString(" · ")
                    Text(repairText, fontSize = 11.sp, color = TextSecondary)
                }
                if (details != null && details.reviewed != null) {
                    Text("Reference reviewed: ${details.reviewed}", fontSize = 10.sp, color = TextSecondary)
                }
                if (details != null) {
                    Text(
                        "Generic reference: OBDex (CC0-1.0). ${details.sourceCount} source references. " +
                            "Actual cause must be confirmed with vehicle-specific diagnosis.",
                        fontSize = 10.sp,
                        color = TextSecondary,
                    )
                }
            }
        }
    }
}
