package com.obdiitools.ui.screens

import android.os.Process as AndroidProcess
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.obdiitools.ui.theme.BackgroundDeep
import com.obdiitools.ui.theme.NeonCyan
import com.obdiitools.ui.theme.NeonGreen
import com.obdiitools.ui.theme.NeonOrange
import com.obdiitools.ui.theme.NeonRed
import com.obdiitools.ui.theme.TextPrimary
import com.obdiitools.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogcatScreen(onBack: () -> Unit) {
    val lines = remember { mutableStateListOf<LogLine>() }
    var filterText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val processRef = remember { mutableStateOf<java.lang.Process?>(null) }
    val clipboard = LocalClipboardManager.current

    DisposableEffect(Unit) {
        onDispose { processRef.value?.destroy() }
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val process = Runtime.getRuntime().exec(
                    arrayOf("logcat", "-v", "brief", "--pid=${AndroidProcess.myPid()}")
                )
                withContext(Dispatchers.Main) { processRef.value = process }
                process.inputStream.bufferedReader().use { reader ->
                    var raw: String?
                    while (reader.readLine().also { raw = it } != null) {
                        val line = parseLogLine(raw!!)
                        withContext(Dispatchers.Main) {
                            lines.add(line)
                            if (lines.size > 3000) lines.removeAt(0)
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    val query = filterText.trim().lowercase()
    val filtered = remember(lines.size, query) {
        if (query.isEmpty()) lines.toList()
        else lines.filter {
            it.tag.lowercase().contains(query) || it.message.lowercase().contains(query)
        }
    }

    LaunchedEffect(filtered.size) {
        if (filtered.isNotEmpty()) listState.animateScrollToItem(filtered.size - 1)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(BackgroundDeep),
    ) {
        TopAppBar(
            title = {
                Column {
                    Text("LOGCAT", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = NeonCyan, letterSpacing = 2.sp)
                    Text("Live log viewer", fontFamily = FontFamily.Monospace, fontSize = 18.sp, color = TextPrimary)
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                }
            },
            actions = {
                IconButton(onClick = {
                    clipboard.setText(AnnotatedString(filtered.joinToString("\n") { it.raw }))
                }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy to clipboard", tint = TextPrimary)
                }
                IconButton(onClick = { lines.clear() }) {
                    Icon(Icons.Default.Clear, contentDescription = "Clear", tint = NeonRed)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundDeep),
        )

        OutlinedTextField(
            value = filterText,
            onValueChange = { filterText = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            placeholder = {
                Text("Filter by tag or message…", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = TextSecondary)
            },
            singleLine = true,
            textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = TextPrimary),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor   = NeonCyan,
                unfocusedBorderColor = TextSecondary.copy(alpha = 0.3f),
                cursorColor          = NeonCyan,
            ),
        )

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            itemsIndexed(filtered) { _, line ->
                val levelColor = when (line.level) {
                    'E' -> NeonRed
                    'W' -> NeonOrange
                    'I' -> NeonGreen
                    'D' -> NeonCyan
                    else -> TextSecondary
                }
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = levelColor, fontWeight = FontWeight.Bold)) {
                            append("${line.level}")
                            if (line.tag.isNotEmpty()) append("/${line.tag}: ")
                            else append(" ")
                        }
                        withStyle(SpanStyle(color = TextPrimary.copy(alpha = 0.9f))) {
                            append(line.message)
                        }
                    },
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 1.dp),
                )
            }
        }
    }
}

private data class LogLine(val level: Char, val tag: String, val message: String, val raw: String)

private fun parseLogLine(raw: String): LogLine {
    // Brief format: "D/SpeedLimit(12345): message"
    val match = Regex("""^([VDIWEF])/([^(]+)\(\s*\d+\):\s*(.*)""").find(raw.trim())
    return if (match != null) {
        LogLine(
            level   = match.groupValues[1][0],
            tag     = match.groupValues[2].trim(),
            message = match.groupValues[3],
            raw     = raw,
        )
    } else {
        LogLine(level = 'V', tag = "", message = raw, raw = raw)
    }
}
