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
)

data class Attendee(val profile: Profile, val status: CueStatus) {
    val name get() = profile.displayName
}

data class Event(val name: String, val details: String, val attendees: List<Attendee>)

data class CollectedCard(val profile: Profile, val status: CueStatus) {
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

    val collection = listOf(
        CollectedCard(Profile("Sam", "Runner. Always down for a walk-and-talk.", listOf("Texting", "One-on-one"), listOf("No early calls"), true), CueStatus.GREEN),
        CollectedCard(Profile("Jordan", "New in town, still finding my people.", listOf("Small groups"), listOf("Ask before tagging me"), true), CueStatus.YELLOW),
        CollectedCard(Profile("Taylor", "Music, books, quiet corners.", listOf("Low-key hangouts", "Voice notes"), listOf("No surprise visits"), true), CueStatus.PURPLE),
        CollectedCard(Profile("Morgan", "Ask me about plants.", listOf("Direct feedback"), emptyList(), true), CueStatus.GREEN),
        CollectedCard(Profile("Casey", "Recharging this week.", listOf("Email over chat"), listOf("No hugs", "No photos"), true), CueStatus.RED),
    )

    // Mock event. Real attendee lists will come from the backend (Sprint 1 decision).
    val event = Event(
        name = "FIU Tech Mixer",
        details = "Tonight · 7 PM · GC Ballroom",
        attendees = listOf(
            Attendee(Profile("Riley", "CS junior. Happy to talk hackathons.", listOf("Small groups", "Texting"), listOf("No photos"), true), CueStatus.GREEN),
            Attendee(Profile("Priya", "First mixer, a little nervous.", listOf("One-on-one"), listOf("Ask before hugs"), true), CueStatus.PURPLE),
            Attendee(Profile("Marcus", "Here for the free pizza, honestly.", listOf("Direct feedback"), emptyList(), true), CueStatus.GREEN),
            Attendee(Profile("Dana", "Long week. Just listening tonight.", listOf("Low-key chats"), listOf("No surprise intros"), true), CueStatus.YELLOW),
            Attendee(Profile("Leo", "Recharging in the corner.", emptyList(), listOf("Please give me space"), true), CueStatus.RED),
            Attendee(Profile("Sam", "Runner. Always down for a walk-and-talk.", listOf("Texting", "One-on-one"), listOf("No early calls"), true), CueStatus.GREEN),
        ),
    )

    // Opaque ID only; the QR resolves to a public profile route, never raw profile data.
    const val qrId = "uc_7f3a9c21"

    // Placeholder link: the domain and resolver don't exist yet.
    const val shareLink = "https://unspokencues.app/c/$qrId"
}
