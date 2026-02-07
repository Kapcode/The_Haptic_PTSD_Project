package com.kapcode.thehapticptsdproject

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.ui.graphics.vector.ImageVector

sealed class PTSDMode(
    val id: String,
    val name: String,
    val description: String,
    val icon: ImageVector
) {
    object ActiveHeartbeat : PTSDMode(
        id = "ACTIVE_HEARTBEAT",
        name = "Active Heartbeat",
        description = "Trigger on squeeze or shake, a heartbeat pulsing lasting X seconds to help soothe.",
        icon = Icons.Default.Favorite
    )

    object BBPlayer : PTSDMode(
        id = "BB_PLAYER",
        name = "BB Player",
        description = "Trigger on squeeze or snap to play synchronized bilateral beats.",
        icon = Icons.Default.MusicNote
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
