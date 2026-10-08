package com.example.unspokenqueues.model

// The rules for the profile part of onboarding. Plain Kotlin on purpose (no Compose or Android
// imports) so the wizard's Next/Finish buttons and the unit tests share one definition.

/** Whether the name step is filled in: the display name is more than whitespace. */
fun hasDisplayName(profile: Profile): Boolean = profile.displayName.isNotBlank()

/**
 * Whether the profile says at least one thing about how to approach this person: a preference
 * or a boundary. Entries that are empty or only whitespace don't count.
 */
fun hasPreferenceOrBoundary(profile: Profile): Boolean =
    profile.preferences.any { it.isNotBlank() } || profile.boundaries.any { it.isNotBlank() }

/** Whether profile setup can be finished: a display name plus at least one preference or boundary. */
fun canFinishProfileSetup(profile: Profile): Boolean =
    hasDisplayName(profile) && hasPreferenceOrBoundary(profile)
