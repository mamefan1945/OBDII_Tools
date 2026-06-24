package com.obdiitools.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.obdiitools.ui.theme.BackgroundDeep
import com.obdiitools.ui.theme.NeonCyan
import com.obdiitools.ui.theme.NeonOrange
import com.obdiitools.ui.theme.NeonRed
import com.obdiitools.ui.theme.TextPrimary
import com.obdiitools.ui.theme.TextSecondary
import com.obdiitools.util.AppLogger
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(onBack: () -> Unit) {
    val logLines by AppLogger.logFlow.collectAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(logLines.size) {
        if (logLines.isNotEmpty()) {
            scope.launch { listState.animateScrollToItem(logLines.size - 1) }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDeep),
    ) {
        TopAppBar(
            title = {
                Column {
                    Text(
                        "CONNECTION LOG",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = NeonCyan,
                        letterSpacing = 2.sp,
                    )
                    Text(
                        "Diagnostics",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 18.sp,
                        color = TextPrimary,
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                }
            },
            actions = {
                IconButton(onClick = {
                    val text = logLines.joinToString("\n")
                    val clip = ClipData.newPlainText("OBDII Log", text)
                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                        .setPrimaryClip(clip)
                }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = NeonCyan)
                }
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = { AppLogger.clear() }) {
                    Icon(Icons.Default.Delete, contentDescription = "Clear", tint = NeonOrange)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundDeep),
        )

        if (logLines.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "No log entries yet.",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        color = TextSecondary,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Connect via BLE to populate this log.",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = TextSecondary.copy(alpha = 0.6f),
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "${logLines.size} entries",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = TextSecondary,
                )
                Text(
                    "D=grey  W=orange  E=red",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = TextSecondary.copy(alpha = 0.5f),
                )
            }
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
            ) {
                items(logLines) { line ->
                    val color = when {
                        line.contains("] E/") -> NeonRed
                        line.contains("] W/") -> NeonOrange
                        line.contains("] V/") -> TextSecondary.copy(alpha = 0.5f)
                        else -> TextSecondary
                    }
                    Text(
                        text = line,
                        color = color,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                        modifier = Modifier.padding(vertical = 1.dp),
                    )
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}
