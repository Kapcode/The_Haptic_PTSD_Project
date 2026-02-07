package com.kapcode.thehapticptsdproject

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class LogLevel {
    DEBUG, INFO, ERROR
}

data class LogEntry(
    val message: String,
    val level: LogLevel,
    val timestamp: String = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
) {
    override fun toString(): String {
        return "$timestamp [${level.name}] $message"
    }
}

object Logger {
    private const val TAG = "HapticPTSDProject"
    private val _logHistory = MutableStateFlow<List<LogEntry>>(emptyList())
    val logHistory = _logHistory.asStateFlow()

    var logToLogcat: Boolean
        get() = SettingsManager.logToLogcat
        set(value) {
            SettingsManager.logToLogcat = value
        }

    fun log(message: String, level: LogLevel = LogLevel.INFO) {
        _logHistory.value = (_logHistory.value + LogEntry(message, level)).takeLast(1000)

        if (logToLogcat) {
            when (level) {
                LogLevel.DEBUG -> Log.d(TAG, message)
                LogLevel.INFO -> Log.i(TAG, message)
                LogLevel.ERROR -> Log.e(TAG, message)
            }
        }
    }

    fun info(message: String) {
        log(message, LogLevel.INFO)
    }

    fun debug(message: String) {
        log(message, LogLevel.DEBUG)
    }

    fun error(message: String) {
        log(message, LogLevel.ERROR)
    }

    fun clear() {
        _logHistory.value = emptyList()
    }
}
