package com.kapcode.thehapticptsdproject.composables

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SliderWithTick(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    defaultValue: Float,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        AnimatedSlider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            defaultValue = defaultValue,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled
        )

        val thumbRadius = 10.dp // Default thumb radius for Material3 Slider
        val trackWidth = maxWidth - (thumbRadius * 2)
        val tickPositionRatio = (defaultValue - valueRange.start) / (valueRange.endInclusive - valueRange.start)
        val tickOffset = (trackWidth * tickPositionRatio) + thumbRadius

        Divider(
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier
                .height(12.dp)
                .width(2.dp)
                .align(Alignment.CenterStart)
                .offset(x = tickOffset)
        )
    }
}
