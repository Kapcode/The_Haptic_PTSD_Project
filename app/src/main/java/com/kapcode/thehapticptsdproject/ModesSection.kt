package com.kapcode.thehapticptsdproject

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kapcode.thehapticptsdproject.composables.SectionCard

@Composable
fun ModesSection(viewModel: ModesViewModel = viewModel()) {
    val modes = listOf(
        PTSDMode.ActiveHeartbeat,
        PTSDMode.BBPlayer,
        PTSDMode.SleepAssistance,
        PTSDMode.GroundingMode
    )
    SectionCard(title = "Modes", isInitiallyExpanded = true) {
        Column {
            modes.forEach { mode ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { viewModel.onModeToggled(mode) },
                    colors = CardDefaults.cardColors(containerColor = if (mode in viewModel.modeState.value.activeModes) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            mode.icon,
                            null,
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(modifier = Modifier.padding(start = 12.dp)) {
                            Text(mode.name, style = MaterialTheme.typography.titleMedium)
                            Text(mode.description, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
