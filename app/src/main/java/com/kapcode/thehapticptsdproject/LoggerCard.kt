package com.kapcode.thehapticptsdproject

import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kapcode.thehapticptsdproject.composables.AnimatedSwitch
import com.kapcode.thehapticptsdproject.composables.SectionCard

@Composable
fun LoggerCard(vm: LoggerViewModel = viewModel()) {
    val logHistory by vm.logHistory.collectAsState()
    var selectedLogLevel by remember { mutableStateOf(LogLevel.DEBUG) }
    var logToLogcat by remember { mutableStateOf(Logger.logToLogcat) }
    val context = LocalContext.current
    val isDeveloperMode = remember {
        Settings.Secure.getInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1
    }

    val filteredLogs = remember(logHistory, selectedLogLevel) {
        logHistory.filter { it.level.ordinal >= selectedLogLevel.ordinal }
    }

    SectionCard(
        title = "Logging",
        actions = {
            Row {
                LogLevel.entries.forEach { level ->
                    TextButton(
                        onClick = { selectedLogLevel = level },
                        colors = ButtonDefaults.buttonColors(
                            contentColor = if (selectedLogLevel == level) MaterialTheme.colorScheme.primary else Color.Gray,
                            containerColor = Color.Transparent
                        )
                    ) {
                        Text(level.name, fontSize = 10.sp)
                    }
                }
            }
        }
    ) {
        Column {
            LazyColumn(modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)) {
                items(filteredLogs.reversed()) { log ->
                    val color = when (log.level) {
                        LogLevel.ERROR -> Color.Red
                        LogLevel.DEBUG -> Color.Gray
                        else -> Color.Unspecified
                    }
                    Text(log.toString(), style = MaterialTheme.typography.bodySmall, color = color)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isDeveloperMode) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Logcat", style = MaterialTheme.typography.bodySmall)
                        AnimatedSwitch(
                            checked = logToLogcat,
                            onCheckedChange = {
                                logToLogcat = it
                                vm.onLogcatToggle(it)
                            },
                            modifier = Modifier.scale(0.7f)
                        )
                    }
                }
                Button(onClick = { vm.onClearLogs() }) { Text("Clear Logs") }
            }
        }
    }
}
