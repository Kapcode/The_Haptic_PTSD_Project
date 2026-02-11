package com.kapcode.thehapticptsdproject.composables

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Switch
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kapcode.thehapticptsdproject.AnimationSettingsViewModel
import com.kapcode.thehapticptsdproject.Logger
import com.kapcode.thehapticptsdproject.ApplicationHapticEffects

@Composable
fun AnimatedSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val animVm: AnimationSettingsViewModel = viewModel()
    var isPressed by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "switchDither")
    
    // Continuous pulse while pressed
    val alpha by if (isPressed && animVm.isDitheringEnabled) {
        infiniteTransition.animateFloat(
            initialValue = 1f - animVm.ditheringIntensity,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = (1000 / (animVm.ditheringSpeed * 10f + 1f)).toInt(),
                    easing = LinearEasing
                ),
                repeatMode = RepeatMode.Reverse
            ),
            label = "switchAlphaPulse"
        )
    } else {
        animateFloatAsState(targetValue = 1f, label = "switchAlphaIdle")
    }

    Box(
        modifier = modifier.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    val isAnyPressed = event.changes.any { it.pressed }
                    if (isPressed != isAnyPressed) {
                        isPressed = isAnyPressed
                        Logger.debug("AnimatedSwitch manual isPressed: $isPressed")
                    }
                }
            }
        }
    ) {
        Switch(
            checked = checked,
            onCheckedChange = {
                onCheckedChange?.invoke(it)
                ApplicationHapticEffects.onSwitchToggle(it)
            },
            modifier = Modifier.alpha(alpha),
            enabled = enabled
        )
    }
}
