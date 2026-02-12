@file:Suppress("ControlFlowWithEmptyBody")

package com.kapcode.thehapticptsdproject.composables

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kapcode.thehapticptsdproject.AnimationSettingsViewModel
import com.kapcode.thehapticptsdproject.ApplicationHapticEffects
import com.kapcode.thehapticptsdproject.Logger
import com.kapcode.thehapticptsdproject.SettingsManager
import kotlin.math.abs

@Composable
fun AnimatedSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    onValueChangeFinished: (() -> Unit)? = null,
    defaultValue: Float? = null
) {
    val animVm: AnimationSettingsViewModel = viewModel()
    var isPressed by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "sliderDither")
    
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
            label = "sliderAlphaPulse"
        )
    } else {
        animateFloatAsState(targetValue = 1f, label = "sliderAlphaIdle")
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val isAnyPressed = event.changes.any { it.pressed }
                        if (isPressed != isAnyPressed) {
                            isPressed = isAnyPressed
                            Logger.debug("AnimatedSlider manual isPressed: $isPressed")
                        }
                    }
                }
            }
    ) {
        Slider(
            value = value,
            onValueChange = { newValue ->
                var finalValue = newValue
                var snapped = false
                defaultValue?.let { default ->
                    val range = valueRange.endInclusive - valueRange.start
                    val threshold = range * SettingsManager.snapDefaultValue
                    if (abs(newValue - default) < threshold) {
                        finalValue = default
                        if (value != default) snapped = true
                    }
                }
                
                if (snapped) {
                    ApplicationHapticEffects.onSliderSnap()
                } else if (abs(newValue - value) > (valueRange.endInclusive - valueRange.start) / 20f) {
                   // Optional: haptic for movement
                }

                onValueChange(finalValue)
            },
            modifier = Modifier
                .fillMaxWidth()
                .alpha(alpha),
            enabled = enabled,
            valueRange = valueRange,
            onValueChangeFinished = onValueChangeFinished
        )
    }
}
