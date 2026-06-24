package com.obdiitools.util

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppLogger {
    private const val MAX_LINES = 600
    private val lines = ArrayDeque<String>(MAX_LINES + 10)
    private val _logFlow = MutableStateFlow<List<String>>(emptyList())
    val logFlow: StateFlow<List<String>> = _logFlow

    fun d(tag: String, msg: String) { Log.d(tag, msg); append("D/$tag", msg) }
    fun i(tag: String, msg: String) { Log.i(tag, msg); append("I/$tag", msg) }
    fun w(tag: String, msg: String) { Log.w(tag, msg); append("W/$tag", msg) }
    fun e(tag: String, msg: String) { Log.e(tag, msg); append("E/$tag", msg) }
    fun v(tag: String, msg: String) { Log.v(tag, msg); append("V/$tag", msg) }

    fun clear() {
        synchronized(lines) { lines.clear() }
        _logFlow.value = emptyList()
    }

    private val fmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    private fun append(level: String, msg: String) {
        val entry = "[${fmt.format(Date())}] $level: $msg"
        synchronized(lines) {
            lines.addLast(entry)
            if (lines.size > MAX_LINES) lines.removeFirst()
            _logFlow.value = lines.toList()
        }
    }
}
