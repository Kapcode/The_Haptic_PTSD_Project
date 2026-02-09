package com.kapcode.thehapticptsdproject.composables

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.kapcode.thehapticptsdproject.BeatProfile
import com.kapcode.thehapticptsdproject.HapticManager
import com.kapcode.thehapticptsdproject.R
import com.kapcode.thehapticptsdproject.SettingsManager
import com.kapcode.thehapticptsdproject.VisualizerType
import com.kapcode.thehapticptsdproject.getColor
import com.kapcode.thehapticptsdproject.getIcon
import kotlinx.coroutines.delay

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
    
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(84.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = 0.2f))
                .padding(4.dp)
        ) {
            val overallIntensity = if (hState.visualizerData.isNotEmpty()) hState.visualizerData.average().toFloat() else 0f

            // 1. Static/Pulsing Line in the background
            if (SettingsManager.isWaveformEnabled) {
                val animatedLineAlpha by animateFloatAsState(
                    targetValue = 0.3f + (overallIntensity * 0.4f),
                    animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow),
                    label = "lineAlpha"
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .align(Alignment.Center)
                        .alpha(animatedLineAlpha)
                        .background(Color.White.copy(alpha = 0.5f))
                )

                // 2. Audio Wave (Pulsing area) in the middle
                val animatedWaveAlpha by animateFloatAsState(
                    targetValue = 0.1f + (overallIntensity * 0.6f),
                    animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow),
                    label = "waveAlpha"
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.6f)
                        .align(Alignment.Center)
                        .alpha(animatedWaveAlpha)
                        .background(Color.Cyan.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                )
            }

            // 3. Bars in the foreground
            Row(modifier = Modifier.fillMaxSize()) {
                if (SettingsManager.isBarsEnabled) {
                    Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        Row(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            hState.visualizerData.forEachIndexed { index, intensity ->
                                val profile = when {
                                    index == 0 -> BeatProfile.AMPLITUDE
                                    index in 1..2 -> BeatProfile.BASS
                                    index in 3..5 -> BeatProfile.DRUM
                                    index in 6..12 -> BeatProfile.GUITAR
                                    else -> null
                                }
                                
                                val threshold = when(profile) {
                                    BeatProfile.AMPLITUDE -> 0.4f
                                    BeatProfile.BASS -> 0.5f
                                    BeatProfile.DRUM -> 0.5f
                                    BeatProfile.GUITAR -> 0.5f
                                    else -> 1.0f
                                }

                                val isAboveThreshold = intensity > threshold
                                val baseColor = profile?.getColor() ?: MaterialTheme.colorScheme.primary
                                val targetAlpha = if (isAboveThreshold && profile != null) SettingsManager.visualizerTriggeredAlpha else 1.0f
                                
                                VisualizerBar(
                                    intensity = intensity, 
                                    color = baseColor, 
                                    alpha = targetAlpha,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        
                        Row(
                            modifier = Modifier.fillMaxWidth().height(20.dp).padding(top = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            val data = hState.visualizerData
                            val isAmplitudeTriggered = data.getOrNull(0)?.let { it > 0.4f } ?: false
                            val isBassTriggered = data.size > 2 && (data[1] > 0.5f || data[2] > 0.5f)
                            val isDrumTriggered = data.size > 5 && (data[3] > 0.5f || data[4] > 0.5f || data[5] > 0.5f)
                            val isGuitarTriggered = data.size > 12 && (6..12).any { data[it] > 0.5f }

                            ProfileIndicatorIcon(BeatProfile.AMPLITUDE, isAmplitudeTriggered, 1, Modifier.weight(1f))
                            ProfileIndicatorIcon(BeatProfile.BASS, isBassTriggered, 2, Modifier.weight(2f))
                            ProfileIndicatorIcon(BeatProfile.DRUM, isDrumTriggered, 3, Modifier.weight(3f))
                            ProfileIndicatorIcon(BeatProfile.GUITAR, isGuitarTriggered, 7, Modifier.weight(7f))
                            Spacer(modifier = Modifier.weight(19f))
                        }
                    }
                }

                if (SettingsManager.isChannelIntensityEnabled) {
                    Column(
                        modifier = Modifier.width(if (SettingsManager.isBarsEnabled) 60.dp else 200.dp).fillMaxHeight().padding(start = 8.dp),
                        verticalArrangement = Arrangement.SpaceEvenly
                    ) {
                        val leftIntensity = if (hState.visualizerData.isNotEmpty()) hState.visualizerData[0] else 0f
                        val rightIntensity = if (hState.visualizerData.size > 1) hState.visualizerData[1] else leftIntensity
                        
                        VisualizerProgressBar("L", leftIntensity)
                        VisualizerProgressBar("R", rightIntensity)
                    }
                }
            }
        }
    }
}

@Composable
fun ProfileIndicatorIcon(profile: BeatProfile, isTriggered: Boolean, weight: Int, modifier: Modifier = Modifier) {
    var latchTriggered by remember { mutableStateOf(false) }
    
    LaunchedEffect(isTriggered) {
        if (isTriggered) {
            latchTriggered = true
            delay(200)
            latchTriggered = false
        }
    }

    val targetAlpha = if (latchTriggered) SettingsManager.visualizerTriggeredAlpha.coerceAtLeast(SettingsManager.minIconAlpha) else 1.0f

    val animatedAlpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow),
        label = "iconAlpha"
    )
    Box(modifier = modifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
        Icon(
            imageVector = profile.getIcon(),
            contentDescription = null,
            tint = profile.getColor(),
            modifier = Modifier.size(15.dp).alpha(animatedAlpha)
        )
    }
}

@Composable
fun VisualizerBar(intensity: Float, color: Color, alpha: Float, modifier: Modifier = Modifier) {
    val minHeight = 0.04f
    val animatedHeight by animateFloatAsState(
        targetValue = intensity.coerceIn(minHeight, 1f),
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow),
        label = "barHeight"
    )
    val animatedAlpha by animateFloatAsState(
        targetValue = alpha,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow),
        label = "barAlpha"
    )
    Box(
        modifier = modifier
            .fillMaxHeight(animatedHeight)
            .background(color.copy(alpha = animatedAlpha), RoundedCornerShape(topStart = 1.dp, topEnd = 1.dp))
    )
}

@Composable
fun VisualizerProgressBar(label: String, intensity: Float) {
    val minWidth = 0.02f
    val animatedIntensity by animateFloatAsState(
        targetValue = intensity.coerceIn(minWidth, 1f),
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow),
        label = "progressIntensity"
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White, modifier = Modifier.width(12.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color.Black.copy(alpha = 0.3f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedIntensity)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}

@Composable
fun VisualizerIcon(onRes: Int, offRes: Int, intensity: Float, label: String? = null, size: androidx.compose.ui.unit.Dp = 32.dp) {
    val animatedIntensity by animateFloatAsState(
        targetValue = intensity,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow),
        label = "iconIntensity"
    )
    val alpha = SettingsManager.minIconAlpha + (animatedIntensity * (1.0f - SettingsManager.minIconAlpha))
    val color = if (animatedIntensity > 0.01f) MaterialTheme.colorScheme.primary else Color.Gray
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Image(
            painter = painterResource(id = if (animatedIntensity > 0.01f) onRes else offRes),
            contentDescription = label,
            modifier = Modifier.size(size),
            alpha = alpha,
            colorFilter = ColorFilter.tint(color)
        )
        if (label != null) Text(label, style = MaterialTheme.typography.labelSmall)
    }
}
