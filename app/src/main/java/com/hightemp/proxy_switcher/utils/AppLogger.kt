package com.hightemp.proxy_switcher.utils

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppLogger {
    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun log(tag: String, message: String) {
        Log.d(tag, message)
        val timestamp = dateFormat.format(Date())
        val logEntry = "[$timestamp] $tag: $message"
        _logs.update { currentLogs ->
            (currentLogs + logEntry).takeLast(1000) // Keep last 1000 logs
        }
    }

    fun error(tag: String, message: String, throwable: Throwable? = null) {
        Log.e(tag, message, throwable)
        val timestamp = dateFormat.format(Date())
        val logEntry = "[$timestamp] E/$tag: $message ${throwable?.message ?: ""}"
        _logs.update { currentLogs ->
            (currentLogs + logEntry).takeLast(1000)
        }
    }

    fun clear() {
        _logs.value = emptyList()
    }
}