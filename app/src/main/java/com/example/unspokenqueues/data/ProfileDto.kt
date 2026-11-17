package com.example.unspokenqueues.data

import com.example.unspokenqueues.model.CueStatus
import com.example.unspokenqueues.model.Profile
import com.example.unspokenqueues.model.ProfilePrivacy
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Row shape of the `public.profiles` table. One row per authenticated user, keyed by the
 * Supabase Auth user id. Column names use snake_case to match Postgres conventions; the
 * [SerialName] annotations bridge them to Kotlin camelCase.
 */
@Serializable
data class ProfileRow(
    val id: String,
    @SerialName("display_name") val displayName: String = "",
    val bio: String = "",
    val preferences: List<String> = emptyList(),
    val boundaries: List<String> = emptyList(),
    @SerialName("is_public") val isPublic: Boolean = true,
    // Stored as the enum name (GREEN/YELLOW/RED/PURPLE).
    val status: String = CueStatus.GREEN.name,
    @SerialName("avatar_url") val avatarUrl: String = "",
    // The picture on the user's S.W.A.P. card. Read here so swap partners see it; written only by
    // [ProfileRepository.updateCardImage], never by the profile upsert.
    @SerialName("card_image_url") val cardImageUrl: String = "",
    // Whether the user has finished the first-run walkthrough.
    @SerialName("onboarding_complete") val onboardingComplete: Boolean = false,
    // Privacy settings. Like onboarding_complete, written only by their own ProfileRepository
    // calls, never by the profile upsert, so saving a profile can't reset them.
    @SerialName("private_mode") val privateMode: Boolean = false,
    @SerialName("show_photo") val showPhoto: Boolean = true,
    @SerialName("show_bio") val showBio: Boolean = true,
    @SerialName("show_preferences") val showPreferences: Boolean = true,
    @SerialName("show_boundaries") val showBoundaries: Boolean = true,
    @SerialName("show_status") val showStatus: Boolean = true,
)

/**
 * Payload used when inserting/updating a profile. Excludes server-managed columns.
 *
 * `onboarding_complete` is left out on purpose. This payload is sent on every profile edit, so
 * carrying the flag would reset it each time; without it an insert takes the column's default
 * (false) and an update leaves the stored value alone. Only
 * [ProfileRepository.markOnboardingComplete] writes it. The privacy columns are left out for the
 * same reason; [ProfileRepository.updatePrivacy] and [ProfileRepository.updatePrivateMode] write them.
 */
@Serializable
data class ProfileUpsert(
    val id: String,
    @SerialName("display_name") val displayName: String,
    val bio: String,
    val preferences: List<String>,
    val boundaries: List<String>,
    @SerialName("is_public") val isPublic: Boolean,
    val status: String,
    @SerialName("avatar_url") val avatarUrl: String,
)

// ---------- Mappers ----------

fun ProfileRow.toProfile(): Profile = Profile(
    displayName = displayName,
    bio = bio,
    preferences = preferences,
    boundaries = boundaries,
    isPublic = isPublic,
    avatarUrl = avatarUrl,
    cardImageUrl = cardImageUrl,
    privateMode = privateMode,
    privacy = ProfilePrivacy(showPhoto, showBio, showPreferences, showBoundaries, showStatus),
)

/** Parse the stored status string back into the enum, defaulting to GREEN on bad data. */
fun ProfileRow.toCueStatus(): CueStatus =
    runCatching { CueStatus.valueOf(status) }.getOrDefault(CueStatus.GREEN)

fun Profile.toUpsert(userId: String, status: CueStatus): ProfileUpsert = ProfileUpsert(
    id = userId,
    displayName = displayName,
    bio = bio,
    preferences = preferences,
    boundaries = boundaries,
    isPublic = isPublic,
    status = status.name,
    avatarUrl = avatarUrl,
)
