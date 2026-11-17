package com.example.unspokenqueues.model

import androidx.compose.ui.graphics.Color
import com.example.unspokenqueues.ui.theme.CueGreen
import com.example.unspokenqueues.ui.theme.CueOnDark
import com.example.unspokenqueues.ui.theme.CueOnLight
import com.example.unspokenqueues.ui.theme.CuePurple
import com.example.unspokenqueues.ui.theme.CueRed
import com.example.unspokenqueues.ui.theme.CueYellow
import java.time.Instant
import kotlin.random.Random

// [onColor] is the text color that meets AA contrast when laid directly on [color].
enum class CueStatus(val label: String, val meaning: String, val color: Color, val onColor: Color) {
    GREEN("Green", "Open to connect", CueGreen, CueOnLight),
    YELLOW("Yellow", "Proceed with care", CueYellow, CueOnLight),
    RED("Red", "Please give me space", CueRed, CueOnDark),
    PURPLE("Purple", "Maybe open to connect", CuePurple, CueOnDark),
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
    // The on-device photo that was just uploaded to [avatarUrl]. Shown in its place so the new
    // photo appears at once instead of waiting for the download. Never saved to the server.
    val localAvatar: Any? = null,
    // Public URL of the picture the person put on their S.W.A.P. card; blank means none, and the
    // card falls back to the profile photo.
    val cardImageUrl: String = "",
    // The on-device copy of [cardImageUrl], shown at once after an upload. Never saved.
    val localCardImage: Any? = null,
    // Hides the profile from everyone, swap partners included, and pauses swaps both ways.
    val privateMode: Boolean = false,
    // Which fields people the user hasn't swapped with may see.
    val privacy: ProfilePrivacy = ProfilePrivacy(),
) {
    /** What to show as the profile photo: the local copy if there is one, else the URL, else null. */
    val avatarImage: Any? get() = localAvatar ?: avatarUrl.takeIf { it.isNotBlank() }

    /** The picture the person chose for their card: the local copy if there is one, else the URL, else null. */
    val cardImage: Any? get() = localCardImage ?: cardImageUrl.takeIf { it.isNotBlank() }

    /** What the card and the binder show as the person's picture: their card image, else their profile photo. */
    val cardPicture: Any? get() = cardImage ?: avatarImage
}

/**
 * Which profile fields people the user hasn't swapped with may see. Off hides the field from
 * everyone but active swap partners; the name always shows. [showPhoto] covers the profile photo
 * and the card image. The server enforces these, so a hidden field arrives blank.
 */
data class ProfilePrivacy(
    val showPhoto: Boolean = true,
    val showBio: Boolean = true,
    val showPreferences: Boolean = true,
    val showBoundaries: Boolean = true,
    val showStatus: Boolean = true,
)

// Someone who has joined an event. userId is their account id. status is null when they hide
// their cue color from the viewer. The server leaves out anyone the viewer may not see.
data class Attendee(val userId: String, val profile: Profile, val status: CueStatus?) {
    val name get() = profile.displayName
}

// An event hosted by one user; others join it with the join code, or RSVP from the Discover feed
// when it is public. Everything after [active] has a default so an event made before event pages
// existed (no time, place, cover or tags) still reads as an event.
data class Event(
    val id: String,
    val hostId: String,
    val name: String,
    val details: String,
    val joinCode: String,
    val active: Boolean,
    val startsAt: Instant? = null,
    val endsAt: Instant? = null,
    val location: String = "",
    val coverUrl: String = "",
    val tags: List<String> = emptyList(),
    val isPublic: Boolean = false,
    val hostName: String = "",
    val goingCount: Int = 0,
)

/** How someone has answered an event. Joining with the host's code counts as [GOING]. */
enum class Rsvp(val wire: String, val label: String) {
    GOING("going", "Going"),
    MAYBE("maybe", "Maybe");

    companion object {
        fun fromWire(value: String?): Rsvp? = entries.firstOrNull { it.wire == value }
    }
}

const val JOIN_CODE_MIN_LENGTH = 4
const val JOIN_CODE_MAX_LENGTH = 8

/** Length of the join codes the app generates for new events. */
const val JOIN_CODE_LENGTH = 6

// Capital letters and digits, minus the ones people mix up when reading a code aloud or off a
// screen (0/O, 1/I/L).
const val JOIN_CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"

/** A random join code for a new event. Pass a seeded [random] to get repeatable codes in tests. */
fun generateJoinCode(random: Random = Random.Default): String =
    buildString(JOIN_CODE_LENGTH) {
        repeat(JOIN_CODE_LENGTH) { append(JOIN_CODE_ALPHABET[random.nextInt(JOIN_CODE_ALPHABET.length)]) }
    }

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
// expiresAt is when a temporary swap ends; null means it is kept.
data class CollectedCard(
    val userId: String,
    val profile: Profile,
    val status: CueStatus,
    val expiresAt: Instant? = null,
) {
    val name get() = profile.displayName
}

/** Why someone reports another user. [wire] is the value the `reports.reason` column accepts. */
enum class ReportReason(val wire: String, val label: String) {
    HARASSMENT("harassment", "Harassment"),
    UNSAFE("unsafe", "Made me feel unsafe"),
    SPAM("spam", "Spam"),
    FAKE("fake", "Fake profile"),
    OTHER("other", "Something else"),
}

/** Someone the signed-in user has blocked, as listed for unblocking. */
data class BlockedUser(val userId: String, val name: String)

/** How an attempt to collect someone's card from a scan or a link ended. */
sealed interface SwapResult {
    data class Swapped(val card: CollectedCard) : SwapResult
    data class Failed(val message: String) : SwapResult
}

// Card links use the app's own scheme, so they open the app without needing a website.
const val CARD_LINK_PREFIX = "unspokencues://card/"

/** The link behind a user's QR code and share sheet. [token] is their secret card token. */
fun cardLink(token: String): String = CARD_LINK_PREFIX + token

// A card token is 32 hex digits. It may arrive as a card link sitting anywhere inside pasted
// text (e.g. a whole chat message), or as the bare token.
private val cardLinkPattern = Regex(Regex.escape(CARD_LINK_PREFIX) + "([0-9a-f]{32})(?![0-9a-f])", RegexOption.IGNORE_CASE)
private val bareTokenPattern = Regex("[0-9a-f]{32}", RegexOption.IGNORE_CASE)

/**
 * The card token inside scanned or pasted [text], lower-cased as the server stores it, or null
 * if the text doesn't hold one.
 */
fun cardTokenFrom(text: String): String? {
    val token = cardLinkPattern.find(text)?.groupValues?.get(1)
        ?: text.trim().takeIf { bareTokenPattern.matches(it) }
    return token?.lowercase()
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
}
