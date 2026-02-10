package com.kapcode.thehapticptsdproject.composables

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kapcode.thehapticptsdproject.BeatProfile
import com.kapcode.thehapticptsdproject.HapticDevice
import com.kapcode.thehapticptsdproject.HapticManager
import com.kapcode.thehapticptsdproject.R
import com.kapcode.thehapticptsdproject.SettingsManager
import com.kapcode.thehapticptsdproject.VisualizerType
import com.kapcode.thehapticptsdproject.getColor
import com.kapcode.thehapticptsdproject.getIcon
import kotlinx.coroutines.delay

@Composable
fun ExperimentalTag(modifier: Modifier = Modifier) {
    Surface(
        color = Color(0xFFFFD700).copy(alpha = 0.15f), // Suttle Yellow
        contentColor = Color(0xFFDAA520), // Darker Goldenrod for text
        shape = RoundedCornerShape(4.dp),
        modifier = modifier.padding(start = 8.dp)
    ) {
        Text(
            text = "EXPERIMENTAL",
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Visible,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
        )
    }
}

@Composable
fun SectionCard(
    title: String,
    isExperimental: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isExperimental) {
                        ExperimentalTag()
                    }
                }
                Row { actions() }
            }
            Spacer(modifier = Modifier.height(12.dp))
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
        Icon(if (isExpanded) Icons.Default.ArrowDropDown else Icons.AutoMirrored.Filled.ArrowRight, null)
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
fun InPlayerHapticVisualizer(selectedProfile: BeatProfile? = null) {
    val hState by HapticManager.state.collectAsState()
    var deviceToAssign by remember { mutableStateOf<HapticDevice?>(null) }

    if (deviceToAssign != null) {
        DeviceAssignmentDialog(
            device = deviceToAssign!!,
            onDismiss = { deviceToAssign = null }
        )
    }
    
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        // --- Device Assignment Row ---
        Text("Tap device to assign profiles", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        Spacer(Modifier.height(4.dp))
        
        // --- Vibration Visualizer ---
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val containerWidth = maxWidth
            val scaledWidth = containerWidth * SettingsManager.scaleVibrationVisualizerX
            val baseHeight = 80.dp
            val scaledHeight = baseHeight * SettingsManager.scaleVibrationVisualizerY
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier
                        .width(scaledWidth)
                        .height(scaledHeight)
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    VisualizerIcon(R.drawable.phone_left_on, R.drawable.phone_left_off, hState.phoneLeftIntensity, hState.phoneLeftColor, "Phone L", device = HapticDevice.PHONE_LEFT) { deviceToAssign = HapticDevice.PHONE_LEFT }
                    VisualizerIcon(R.drawable.controller_left_on, R.drawable.controller_left_off, hState.controllerLeftTopIntensity, hState.controllerLeftTopColor, "Ctrl LT", device = HapticDevice.CTRL_LEFT_TOP) { deviceToAssign = HapticDevice.CTRL_LEFT_TOP }
                    VisualizerIcon(R.drawable.controller_left_on, R.drawable.controller_left_off, hState.controllerLeftBottomIntensity, hState.controllerLeftBottomColor, "Ctrl LB", device = HapticDevice.CTRL_LEFT_BOTTOM) { deviceToAssign = HapticDevice.CTRL_LEFT_BOTTOM }
                    VisualizerIcon(R.drawable.controller_right_on, R.drawable.controller_right_off, hState.controllerRightTopIntensity, hState.controllerRightTopColor, "Ctrl RT", device = HapticDevice.CTRL_RIGHT_TOP) { deviceToAssign = HapticDevice.CTRL_RIGHT_TOP }
                    VisualizerIcon(R.drawable.controller_right_on, R.drawable.controller_right_off, hState.controllerRightBottomIntensity, hState.controllerRightBottomColor, "Ctrl RB", device = HapticDevice.CTRL_RIGHT_BOTTOM) { deviceToAssign = HapticDevice.CTRL_RIGHT_BOTTOM }
                    VisualizerIcon(R.drawable.phone_right_on, R.drawable.phone_right_off, hState.phoneRightIntensity, hState.phoneRightColor, "Phone R", device = HapticDevice.PHONE_RIGHT) { deviceToAssign = HapticDevice.PHONE_RIGHT }
                }
            }
        }
    }
}

@Composable
fun InPlayerAudioVisualizer(selectedProfile: BeatProfile? = null) {
    val hState by HapticManager.state.collectAsState()
    
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val containerWidth = maxWidth
        val scaledWidth = containerWidth * SettingsManager.scaleAudioVisualizerX
        val baseHeight = 120.dp
        val scaledHeight = baseHeight * SettingsManager.scaleAudioVisualizerY
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            Box(
                modifier = Modifier
                    .width(scaledWidth)
                    .height(scaledHeight)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.4f))
                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                    .padding(8.dp)
            ) {
                val overallIntensity = if (hState.visualizerData.isNotEmpty()) hState.visualizerData.average().toFloat() else 0f

                // 1. Waveform / Background Glow
                if (SettingsManager.isWaveformEnabled) {
                    val animatedWaveAlpha by animateFloatAsState(
                        targetValue = 0.05f + (overallIntensity * 0.4f),
                        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
                        label = "waveAlpha"
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.5f)
                            .align(Alignment.Center)
                            .alpha(animatedWaveAlpha)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(50))
                    )
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .align(Alignment.Center)
                            .alpha(0.3f + overallIntensity * 0.7f)
                            .background(Color.White.copy(alpha = 0.5f))
                    )
                }

                // 2. Bars and Indicators
                Row(modifier = Modifier.fillMaxSize()) {
                    if (SettingsManager.isBarsEnabled) {
                        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            // The Bars
                            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                // Threshold Lines Background
                                Row(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    repeat(32) { index ->
                                        val thresholds = getThresholdsForIndex(index)
                                        val threshold = thresholds.first
                                        val triggerThreshold = thresholds.second
                                        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                            if (threshold != null) {
                                                Box(modifier = Modifier.fillMaxWidth().fillMaxHeight(threshold).align(Alignment.BottomCenter).border(0.5.dp, Color.White.copy(alpha = 0.1f)))
                                                if (triggerThreshold != null) {
                                                    Box(modifier = Modifier.fillMaxWidth().fillMaxHeight(triggerThreshold).align(Alignment.BottomCenter).border(0.5.dp, Color.Red.copy(alpha = 0.2f)))
                                                }
                                            }
                                        }
                                    }
                                }
                                
                                // Live Bars
                                Row(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    verticalAlignment = Alignment.Bottom
                                ) {
                                    hState.visualizerData.forEachIndexed { index, intensity ->
                                        val profile = getProfileForIndex(index)
                                        val thresholds = getThresholdsForIndex(index)
                                        val threshold = thresholds.first ?: 1.0f
                                        val isAboveThreshold = intensity > threshold
                                        val baseColor = profile?.getColor() ?: MaterialTheme.colorScheme.primary
                                        
                                        // Dim the bar if NOT triggered, or if trigger dimming is on.
                                        val displayAlpha = if (isAboveThreshold) 1.0f else SettingsManager.visualizerTriggeredAlpha.coerceAtLeast(0.3f)
                                        
                                        VisualizerBar(
                                            intensity = intensity,
                                            color = baseColor,
                                            alpha = displayAlpha,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                            
                            // Bottom Icon Indicators
                            Row(
                                modifier = Modifier.fillMaxWidth().height(24.dp).padding(top = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                val data = hState.visualizerData
                                val isAmplitudeTriggered = data.getOrNull(0)?.let { it > SettingsManager.triggerThresholdAmplitude } ?: false
                                val isBassTriggered = data.size > 2 && (data[1] > SettingsManager.triggerThresholdBass || data[2] > SettingsManager.triggerThresholdBass)
                                val isDrumTriggered = data.size > 5 && (data[3] > SettingsManager.triggerThresholdDrum || data[4] > SettingsManager.triggerThresholdDrum || data[5] > SettingsManager.triggerThresholdDrum)
                                val isGuitarTriggered = data.size > 12 && (6..12).any { data[it] > SettingsManager.triggerThresholdGuitar }

                                ProfileIndicatorIcon(BeatProfile.AMPLITUDE, isAmplitudeTriggered, selectedProfile == BeatProfile.AMPLITUDE, Modifier.weight(1f))
                                ProfileIndicatorIcon(BeatProfile.BASS, isBassTriggered, selectedProfile == BeatProfile.BASS, Modifier.weight(2f))
                                ProfileIndicatorIcon(BeatProfile.DRUM, isDrumTriggered, selectedProfile == BeatProfile.DRUM, Modifier.weight(3f))
                                ProfileIndicatorIcon(BeatProfile.GUITAR, isGuitarTriggered, selectedProfile == BeatProfile.GUITAR, Modifier.weight(7f))
                                Spacer(modifier = Modifier.weight(19f))
                            }
                        }
                    }

                    if (SettingsManager.isChannelIntensityEnabled) {
                        Column(
                            modifier = Modifier.width(if (SettingsManager.isBarsEnabled) 80.dp else 240.dp).fillMaxHeight().padding(start = 8.dp),
                            verticalArrangement = Arrangement.SpaceEvenly
                        ) {
                            val leftIntensity = if (hState.visualizerData.isNotEmpty()) hState.visualizerData[0] else 0f
                            val rightIntensity = if (hState.visualizerData.size > 1) hState.visualizerData[1] else leftIntensity
                            
                            VisualizerProgressBar("LEFT", leftIntensity)
                            VisualizerProgressBar("RIGHT", rightIntensity)
                        }
                    }
                }
            }
        }
    }
}

private fun getProfileForIndex(index: Int): BeatProfile? = when {
    index == 0 -> BeatProfile.AMPLITUDE
    index in 1..2 -> BeatProfile.BASS
    index in 3..5 -> BeatProfile.DRUM
    index in 6..12 -> BeatProfile.GUITAR
    else -> null
}

private fun getThresholdsForIndex(index: Int): Pair<Float?, Float?> = when {
    index == 0 -> 0.4f to SettingsManager.triggerThresholdAmplitude
    index in 1..2 -> 0.5f to SettingsManager.triggerThresholdBass
    index in 3..5 -> 0.5f to SettingsManager.triggerThresholdDrum
    index in 6..12 -> 0.5f to SettingsManager.triggerThresholdGuitar
    else -> null to null
}

@Composable
fun ProfileIndicatorIcon(profile: BeatProfile, isVisualizerTriggered: Boolean, isSelected: Boolean, modifier: Modifier = Modifier) {
    val hState by HapticManager.state.collectAsState()
    
    // STRICT HAPTIC EVENT CHECK: Only wobble if the profile is actively causing vibration
    val isCurrentlyVibrating = profile in hState.activeProfiles

    val targetAlpha = if (isCurrentlyVibrating) 1.0f else SettingsManager.minIconAlpha.coerceAtLeast(0.2f)
    val animatedAlpha by animateFloatAsState(targetValue = targetAlpha, label = "iconAlpha")

    // Wobble animation
    val infiniteTransition = rememberInfiniteTransition(label = "wobble")
    val wobbleAngle by infiniteTransition.animateFloat(
        initialValue = -10f,
        targetValue = 10f,
        animationSpec = infiniteRepeatable(
            animation = tween(150, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wobbleAngle"
    )

    Box(modifier = modifier.fillMaxHeight().background(profile.getColor().copy(alpha = 0.05f), RoundedCornerShape(4.dp)), contentAlignment = Alignment.Center) {
        Icon(
            imageVector = profile.getIcon(),
            contentDescription = null,
            tint = profile.getColor(),
            modifier = Modifier
                .size(16.dp)
                .alpha(animatedAlpha)
                .graphicsLayer {
                    if (isCurrentlyVibrating) {
                        rotationZ = wobbleAngle
                        scaleX = 1.2f
                        scaleY = 1.2f
                    }
                }
        )
        if (isSelected) {
            Box(Modifier.fillMaxSize().border(1.dp, profile.getColor().copy(alpha = 0.5f), RoundedCornerShape(4.dp)))
        }
    }
}

@Composable
fun VisualizerBar(intensity: Float, color: Color, alpha: Float, modifier: Modifier = Modifier) {
    val minHeight = 0.05f
    val animatedHeight by animateFloatAsState(
        targetValue = intensity.coerceIn(minHeight, 1f),
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
        label = "barHeight"
    )
    val animatedAlpha by animateFloatAsState(
        targetValue = alpha,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
        label = "barAlpha"
    )
    Box(
        modifier = modifier
            .fillMaxHeight(animatedHeight)
            .background(color.copy(alpha = animatedAlpha), RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
    )
}

@Composable
fun VisualizerProgressBar(label: String, intensity: Float) {
    Column(modifier = Modifier.fillMaxWidth()) {
        val themePrimary = MaterialTheme.colorScheme.primary
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f), fontSize = 8.sp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.White.copy(alpha = 0.1f))
        ) {
            val animatedIntensity by animateFloatAsState(targetValue = intensity.coerceIn(0f, 1f), label = "progress")
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedIntensity)
                    .fillMaxHeight()
                    .background(themePrimary)
            )
        }
    }
}

@Composable
fun VisualizerIcon(onRes: Int, offRes: Int, intensity: Float, activeColor: Color, label: String, size: androidx.compose.ui.unit.Dp = 40.dp, device: HapticDevice? = null, onClick: () -> Unit) {
    val hState by HapticManager.state.collectAsState()
    val animatedIntensity by animateFloatAsState(targetValue = intensity, label = "iconIntensity")
    val isActive = animatedIntensity > 0.01f
    val alpha = (SettingsManager.minIconAlpha + (animatedIntensity * (1.0f - SettingsManager.minIconAlpha))).coerceIn(0f, 1f)
    
    val themePrimary = MaterialTheme.colorScheme.primary
    val color = if (isActive) activeColor else Color.Gray
    
    // DEVICE WOBBLE Logic: Wobble if any profile assigned to this device is actively vibrating
    val isDeviceVibrating = remember(hState.activeProfiles, device) {
        if (device == null) false
        else {
            val assignedProfiles = SettingsManager.deviceAssignments[device] ?: emptySet()
            assignedProfiles.any { it in hState.activeProfiles }
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "deviceWobble")
    val wobbleAngle by infiniteTransition.animateFloat(
        initialValue = -8f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(
            animation = tween(120, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "deviceWobbleAngle"
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(60.dp).clickable { onClick() }) {
        Box(
            modifier = Modifier
                .size(size)
                .graphicsLayer {
                    if (isDeviceVibrating) {
                        rotationZ = wobbleAngle
                        scaleX = 1.15f
                        scaleY = 1.15f
                    }
                }
                .clip(CircleShape)
                .background(if (isActive) color.copy(alpha = 0.15f) else Color.Transparent)
                .border(1.dp, color.copy(alpha = if (isActive) 0.5f else 0.2f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = if (isActive) onRes else offRes),
                contentDescription = label,
                modifier = Modifier.size(size * 0.7f),
                alpha = alpha,
                colorFilter = ColorFilter.tint(color)
            )
        }
        Text(label, style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, color = color.copy(alpha = 0.8f), maxLines = 1)
    }
}

@Composable
fun DeviceAssignmentDialog(device: HapticDevice, onDismiss: () -> Unit) {
    var assignedProfiles by remember { mutableStateOf(SettingsManager.deviceAssignments[device] ?: emptySet()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Assign Profiles to ${device.label}") },
        text = {
            Column {
                BeatProfile.entries.forEach { profile ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                assignedProfiles = if (profile in assignedProfiles) {
                                    assignedProfiles - profile
                                } else {
                                    assignedProfiles + profile
                                }
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = profile in assignedProfiles, onCheckedChange = null)
                        Spacer(Modifier.width(8.dp))
                        Icon(profile.getIcon(), null, tint = profile.getColor())
                        Spacer(Modifier.width(8.dp))
                        Text(profile.name, color = profile.getColor())
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val currentAssignments = SettingsManager.deviceAssignments.toMutableMap()
                currentAssignments[device] = assignedProfiles
                SettingsManager.deviceAssignments = currentAssignments
                SettingsManager.save()
                onDismiss()
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
