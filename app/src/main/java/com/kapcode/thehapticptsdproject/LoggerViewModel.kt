package com.kapcode.thehapticptsdproject

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.StateFlow


class LoggerViewModel : ViewModel() {
    val logHistory: StateFlow<List<LogEntry>> = Logger.logHistory

    @Suppress("unused")
    fun onLogLevelSelected(logLevel: LogLevel) {
        // This is a UI-only concern, handled in the composable
    }

    fun onLogcatToggle(enabled: Boolean) {
        Logger.logToLogcat = enabled
    }

    fun onClearLogs() {
        Logger.clear()
    }
}
