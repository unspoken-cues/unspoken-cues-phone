package com.example.unspokenqueues.model

import androidx.compose.ui.graphics.Color
import com.example.unspokenqueues.ui.theme.CueGreen
import com.example.unspokenqueues.ui.theme.CuePurple
import com.example.unspokenqueues.ui.theme.CueRed
import com.example.unspokenqueues.ui.theme.CueYellow

enum class CueStatus(val label: String, val meaning: String, val color: Color) {
    GREEN("Green", "Open to connect", CueGreen),
    YELLOW("Yellow", "Proceed with care", CueYellow),
    RED("Red", "Please give me space", CueRed),
    PURPLE("Purple", "Need support", CuePurple),
}

enum class WatchConnection(val label: String) {
    CONNECTED("Watch connected"),
    SYNCING("Syncing…"),
    DISCONNECTED("Watch not connected"),
}

data class Profile(
    val displayName: String,
    val bio: String,
    val preferences: List<String>,
    val boundaries: List<String>,
    val isPublic: Boolean,
)

data class CollectedCard(val name: String, val status: CueStatus)

// Mock data until the backend decision is made (Sprint 1).
object MockData {
    val profile = Profile(
        displayName = "Alex Rivera",
        bio = "Designer. Coffee first, conversation second.",
        preferences = listOf("Text over calls", "Small groups", "Direct feedback"),
        boundaries = listOf("No hugs without asking", "No photos"),
        isPublic = true,
    )

    val collection = listOf(
        CollectedCard("Sam", CueStatus.GREEN),
        CollectedCard("Jordan", CueStatus.YELLOW),
        CollectedCard("Taylor", CueStatus.PURPLE),
        CollectedCard("Morgan", CueStatus.GREEN),
        CollectedCard("Casey", CueStatus.RED),
    )

    // Opaque ID only; the QR resolves to a public profile route, never raw profile data.
    const val qrId = "uc_7f3a9c21"
}
