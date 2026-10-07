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
    PURPLE("Purple", "Maybe open to connect", CuePurple),
}

enum class WatchConnection(val label: String) {
    CONNECTED("Watch connected"),
    SYNCING("Syncing…"),
    DISCONNECTED("Watch not connected"),
}

enum class ThemeMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark"),
}

data class Profile(
    val displayName: String,
    val bio: String,
    val preferences: List<String>,
    val boundaries: List<String>,
    val isPublic: Boolean,
    // Public URL of the profile photo; blank means none (initials are shown instead).
    val avatarUrl: String = "",
)

// Someone who has joined an event. userId is their account id.
data class Attendee(val userId: String, val profile: Profile, val status: CueStatus) {
    val name get() = profile.displayName
}

// An event hosted by one user; others join it with the join code.
data class Event(
    val id: String,
    val hostId: String,
    val name: String,
    val details: String,
    val joinCode: String,
    val active: Boolean,
)

// Statuses an attendee is willing to show other attendees. Red and purple stay private to the host.
private val statusesVisibleToAttendees = setOf(CueStatus.GREEN, CueStatus.YELLOW)

/**
 * The attendees [viewerId] may see at an event hosted by [hostId]. The host sees everyone; any
 * other viewer sees only people who are green or yellow, plus themselves whatever their status.
 */
fun visibleAttendees(viewerId: String, hostId: String, attendees: List<Attendee>): List<Attendee> =
    if (viewerId == hostId) {
        attendees
    } else {
        attendees.filter { it.userId == viewerId || it.status in statusesVisibleToAttendees }
    }

const val JOIN_CODE_MIN_LENGTH = 4
const val JOIN_CODE_MAX_LENGTH = 8

/**
 * Whether [code] looks like an event join code: 4-8 letters or digits (A-Z, 0-9, any case).
 * Surrounding whitespace is ignored, matching how the server looks codes up.
 */
fun isValidJoinCode(code: String): Boolean {
    val trimmed = code.trim()
    return trimmed.length in JOIN_CODE_MIN_LENGTH..JOIN_CODE_MAX_LENGTH &&
        trimmed.all { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' }
}

// A card in the binder. userId is the other person's account id, used to remove the swap.
data class CollectedCard(val userId: String, val profile: Profile, val status: CueStatus) {
    val name get() = profile.displayName
}

// Mock data until the backend decision is made (Sprint 1).
object MockData {
    val profile = Profile(
        displayName = "Alex Rivera",
        bio = "Designer. Coffee first, conversation second.",
        preferences = listOf("Text over calls", "Small groups", "Direct feedback"),
        boundaries = listOf("No hugs without asking", "No photos"),
        isPublic = true,
    )

    // Opaque ID only; the QR resolves to a public profile route, never raw profile data.
    const val qrId = "uc_7f3a9c21"

    // Placeholder link: the domain and resolver don't exist yet.
    const val shareLink = "https://unspokencues.app/c/$qrId"
}
