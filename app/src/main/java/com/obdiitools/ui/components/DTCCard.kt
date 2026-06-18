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
import androidx.compose.ui.graphics.Color
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
        DTCDatabase.Severity.HIGH   -> NeonRed
        DTCDatabase.Severity.MEDIUM -> NeonOrange
        DTCDatabase.Severity.LOW    -> NeonYellow
    }

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
                modifier = Modifier
                    .size(20.dp)
                    .padding(top = 2.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                Text(
                    text = dtc.description.ifBlank { "No description available" },
                    fontFamily = FontFamily.Default,
                    fontWeight = FontWeight.Normal,
                    fontSize = 13.sp,
                    color = TextPrimary,
                )
                Text(
                    text = when (severity) {
                        DTCDatabase.Severity.HIGH   -> "High severity"
                        DTCDatabase.Severity.MEDIUM -> "Medium severity"
                        DTCDatabase.Severity.LOW    -> "Low severity"
                    },
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = TextSecondary,
                )
            }
        }
    }
}
