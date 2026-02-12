@file:Suppress("unused")

package com.kapcode.thehapticptsdproject

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt

fun Modifier.shake(enabled: Boolean, animatable: Animatable<Float, *>) = composed {
    val modifier = if (enabled) {
        LaunchedEffect(Unit) {
            animatable.animateTo(
                targetValue = 0f,
                animationSpec = spring(stiffness = 1000f)
            )
        }
        this.offset {
            IntOffset(
                x = (animatable.value * 10f).roundToInt(),
                y = 0
            )
        }
    } else {
        this
    }
    modifier
}

val standardAnimationSpec: AnimationSpec<Float> = tween(durationMillis = 300)

val bouncyAnimationSpec: AnimationSpec<Float> = spring(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMedium
)

@Composable
fun animateWobble(
    initialValue: Float = -10f,
    targetValue: Float = 10f,
    durationMillis: Int = 150
): State<Float> {
    val infiniteTransition = rememberInfiniteTransition(label = "wobble")
    return infiniteTransition.animateFloat(
        initialValue = initialValue,
        targetValue = targetValue,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wobbleAngle"
    )
}
