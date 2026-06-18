package com.obdiitools.ui.screens

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.obdiitools.data.CustomPidDefinition
import com.obdiitools.data.FormulaType
import com.obdiitools.ui.theme.BackgroundDeep
import com.obdiitools.ui.theme.NeonCyan
import com.obdiitools.ui.theme.NeonGreen
import com.obdiitools.ui.theme.NeonRed
import com.obdiitools.ui.theme.SurfaceBorder
import com.obdiitools.ui.theme.SurfaceElevated
import com.obdiitools.ui.theme.TextPrimary
import com.obdiitools.ui.theme.TextSecondary
import com.obdiitools.viewmodel.CustomPidViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomPidScreen(
    onBack: () -> Unit,
    viewModel: CustomPidViewModel = hiltViewModel(),
) {
    val pids by viewModel.customPids.collectAsState()
    val liveValues by viewModel.liveValues.collectAsState()
    val showDialog by viewModel.showAddDialog.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDeep),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "CUSTOM PIDs",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = NeonCyan,
                            letterSpacing = 3.sp,
                        )
                        Text(
                            "User Defined",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = TextPrimary,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = TextSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundDeep),
            )

            HorizontalDivider(color = SurfaceBorder, modifier = Modifier.padding(horizontal = 20.dp))
            Spacer(Modifier.height(12.dp))

            if (pids.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Tune, null, tint = SurfaceBorder, modifier = Modifier.size(64.dp))
                        Spacer(Modifier.height(16.dp))
                        Text("No custom PIDs", color = TextSecondary, fontFamily = FontFamily.Monospace, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text("Tap + to add a PID", color = TextSecondary, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(pids, key = { it.id }) { pid ->
                        CustomPidRow(pid, liveValues[pid.id], onDelete = { viewModel.removePid(pid.id) })
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }

        FloatingActionButton(
            onClick = { viewModel.showAddDialog.value = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp),
            containerColor = NeonCyan,
            shape = CircleShape,
        ) {
            Icon(Icons.Default.Add, null)
        }
    }

    if (showDialog) {
        AddPidDialog(
            onDismiss = { viewModel.showAddDialog.value = false },
            onAdd = { name, command, unit, formula -> viewModel.addPid(name, command, unit, formula) },
        )
    }
}

@Composable
private fun CustomPidRow(pid: CustomPidDefinition, liveValue: Float?, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceElevated.copy(alpha = 0.5f))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(pid.name, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextPrimary)
            Text("CMD: ${pid.command}  ·  ${pid.formula.label}", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = TextSecondary)
        }
        Text(
            liveValue?.let { "${"%.2f".format(it)} ${pid.unit}" } ?: "--",
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = if (liveValue != null) NeonGreen else TextSecondary,
        )
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, null, tint = NeonRed.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddPidDialog(onDismiss: () -> Unit, onAdd: (String, String, String, FormulaType) -> Unit) {
    var name by remember { mutableStateOf("") }
    var nameError by remember { mutableStateOf(false) }
    var command by remember { mutableStateOf("") }
    var commandError by remember { mutableStateOf(false) }
    var unit by remember { mutableStateOf("") }
    var formula by remember { mutableStateOf(FormulaType.RAW_A) }
    var formulaExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Add Custom PID", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = NeonCyan)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; nameError = false },
                    label = { Text("Display Name", fontFamily = FontFamily.Monospace, fontSize = 11.sp) },
                    isError = nameError,
                    supportingText = if (nameError) { { Text("Required", fontFamily = FontFamily.Monospace, fontSize = 10.sp) } } else null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan, focusedLabelColor = NeonCyan),
                )
                OutlinedTextField(
                    value = command,
                    onValueChange = { command = it.uppercase(); commandError = false },
                    label = { Text("PID Command (e.g. 0115)", fontFamily = FontFamily.Monospace, fontSize = 11.sp) },
                    isError = commandError,
                    supportingText = if (commandError) { { Text("Required", fontFamily = FontFamily.Monospace, fontSize = 10.sp) } } else null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan, focusedLabelColor = NeonCyan),
                )
                OutlinedTextField(
                    value = unit,
                    onValueChange = { unit = it },
                    label = { Text("Unit (e.g. V, kPa, %)", fontFamily = FontFamily.Monospace, fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan, focusedLabelColor = NeonCyan),
                )
                ExposedDropdownMenuBox(
                    expanded = formulaExpanded,
                    onExpandedChange = { formulaExpanded = it },
                ) {
                    OutlinedTextField(
                        value = formula.label,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Formula", fontFamily = FontFamily.Monospace, fontSize = 11.sp) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(formulaExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan, focusedLabelColor = NeonCyan),
                    )
                    ExposedDropdownMenu(
                        expanded = formulaExpanded,
                        onDismissRequest = { formulaExpanded = false },
                    ) {
                        FormulaType.entries.forEach { f ->
                            DropdownMenuItem(
                                text = { Text(f.label, fontFamily = FontFamily.Monospace, fontSize = 12.sp) },
                                onClick = { formula = f; formulaExpanded = false },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    nameError = name.isBlank()
                    commandError = command.isBlank()
                    if (!nameError && !commandError) {
                        onAdd(name, command, unit, formula)
                    }
                },
            ) {
                Text("ADD", fontFamily = FontFamily.Monospace, color = NeonGreen, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL", fontFamily = FontFamily.Monospace, color = TextSecondary)
            }
        },
        containerColor = SurfaceElevated,
    )
}
