@file:Suppress("unused")

package com.kapcode.thehapticptsdproject.composables

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kapcode.thehapticptsdproject.AnimationSettingsViewModel
import com.kapcode.thehapticptsdproject.Logger

@Composable
fun DitheringBox(
    modifier: Modifier = Modifier,
    isDitheringEnabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val animVm: AnimationSettingsViewModel = viewModel()

    LaunchedEffect(isDitheringEnabled, animVm.isDitheringEnabled) {
        Logger.debug("DitheringBox: isDitheringEnabled: $isDitheringEnabled, animVm.isDitheringEnabled: ${animVm.isDitheringEnabled}")
    }

    val alpha by if (isDitheringEnabled && animVm.isDitheringEnabled) {
        val infiniteTransition = rememberInfiniteTransition(label = "dithering")
        infiniteTransition.animateFloat(
            initialValue = 1f - animVm.ditheringIntensity,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = (2000 / (animVm.ditheringSpeed * 4f)).toInt(),
                    easing = LinearEasing
                ),
                repeatMode = RepeatMode.Reverse
            ),
            label = "ditheringAlpha"
        )
    } else {
        remember { mutableFloatStateOf(1f) }
    }

    Box(
        modifier = modifier.alpha(alpha)
    ) {
        content()
    }
}
