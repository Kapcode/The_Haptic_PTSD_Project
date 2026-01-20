package com.kapcode.thehapticptsdproject

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.ui.graphics.vector.ImageVector

sealed class PTSDMode(
    val id: String,
    val name: String,
    val description: String,
    val icon: ImageVector
) {
    object SqueezeHeartbeat : PTSDMode(
        id = "SQUEEZE_HEARTBEAT",
        name = "Squeeze Heartbeat",
        description = "Squeeze your phone to feel a comforting heartbeat sensation.",
        icon = Icons.Default.Favorite
    )

    object SleepAssistance : PTSDMode(
        id = "SLEEP_ASSISTANCE",
        name = "Sleep Assistance",
        description = "Gentle haptic pulses to help you drift off to sleep.",
        icon = Icons.Default.NightsStay
    )

    object GroundingMode : PTSDMode(
        id = "GROUNDING_MODE",
        name = "Grounding Mode",
        description = "Rhythmic patterns to help center you during anxiety or panic.",
        icon = Icons.Default.SelfImprovement
    )
}

data class ModeState(
    val activeModes: Set<PTSDMode> = emptySet()
)
