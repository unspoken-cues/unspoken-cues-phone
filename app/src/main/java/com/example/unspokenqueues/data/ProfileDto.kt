package com.example.unspokenqueues.data

import com.example.unspokenqueues.model.CueStatus
import com.example.unspokenqueues.model.Profile
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
)

/** Payload used when inserting/updating a profile. Excludes server-managed columns. */
@Serializable
data class ProfileUpsert(
    val id: String,
    @SerialName("display_name") val displayName: String,
    val bio: String,
    val preferences: List<String>,
    val boundaries: List<String>,
    @SerialName("is_public") val isPublic: Boolean,
    val status: String,
)

// ---------- Mappers ----------

fun ProfileRow.toProfile(): Profile = Profile(
    displayName = displayName,
    bio = bio,
    preferences = preferences,
    boundaries = boundaries,
    isPublic = isPublic,
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
)
