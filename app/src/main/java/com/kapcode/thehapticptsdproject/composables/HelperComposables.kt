package com.kapcode.thehapticptsdproject.composables

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.kapcode.thehapticptsdproject.HapticManager
import com.kapcode.thehapticptsdproject.R

@Composable
fun SectionCard(title: String, actions: @Composable RowScope.() -> Unit = {}, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                Row { actions() }
            }
            content()
        }
    }
}

@Composable
fun FolderItem(folderUri: android.net.Uri, isExpanded: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(if (isExpanded) Icons.Default.FolderOpen else Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.primary)
        Text(folderUri.path?.substringAfterLast(':') ?: "Folder", modifier = Modifier
            .weight(1f)
            .padding(start = 16.dp))
        Icon(if (isExpanded) Icons.Default.ArrowDropDown else Icons.Default.ArrowRight, null)
    }
}

@Composable
fun FileItem(name: String, isAnalyzed: Boolean, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp, horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.MusicNote, null, tint = if (isSelected) MaterialTheme.colorScheme.primary else if (isAnalyzed) Color(0xFF4CAF50) else Color.Red, modifier = Modifier.size(20.dp))
        Text(name, modifier = Modifier.padding(start = 16.dp), color = if (isSelected) MaterialTheme.colorScheme.primary else if (isAnalyzed) Color(0xFF4CAF50) else Color.Red)
    }
}

@Composable
fun InPlayerHapticVisualizer() {
    val hState by HapticManager.state.collectAsState()
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        VisualizerIcon(R.drawable.phone_left_on, R.drawable.phone_left_off, hState.phoneLeftIntensity)
        VisualizerIcon(R.drawable.controller_left_on, R.drawable.controller_left_off, hState.controllerLeftTopIntensity)
        VisualizerIcon(R.drawable.controller_left_on, R.drawable.controller_left_off, hState.controllerLeftBottomIntensity)
        VisualizerIcon(R.drawable.controller_right_on, R.drawable.controller_right_off, hState.controllerRightTopIntensity)
        VisualizerIcon(R.drawable.controller_right_on, R.drawable.controller_right_off, hState.controllerRightBottomIntensity)
        VisualizerIcon(R.drawable.phone_right_on, R.drawable.phone_right_off, hState.phoneRightIntensity)
    }
}

@Composable
fun VisualizerIcon(onRes: Int, offRes: Int, intensity: Float, label: String? = null, size: androidx.compose.ui.unit.Dp = 32.dp) {
    val alpha = 0.2f + (intensity * 0.8f)
    val color = if (intensity > 0.01f) MaterialTheme.colorScheme.primary else Color.Gray
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Image(
            painter = painterResource(id = if (intensity > 0.01f) onRes else offRes),
            contentDescription = label,
            modifier = Modifier.size(size),
            alpha = alpha,
            colorFilter = ColorFilter.tint(color)
        )
        if (label != null) Text(label, style = MaterialTheme.typography.labelSmall)
    }
}
