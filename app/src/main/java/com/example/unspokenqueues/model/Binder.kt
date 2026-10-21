package com.example.unspokenqueues.model

// Pure rules for the binder's counts, search and cue filter. Kept free of Android types so they
// run as plain JVM unit tests.

/** How many collected cards each cue has; every cue is present, with 0 when nobody holds it. */
fun binderCounts(collection: List<CollectedCard>): Map<CueStatus, Int> {
    val counted = collection.groupingBy { it.status }.eachCount()
    return CueStatus.entries.associateWith { counted[it] ?: 0 }
}

/**
 * The cards matching [query] (case-insensitive, in a name, bio, preference or boundary) and, when
 * [cue] is set, holding that cue. A blank query matches every card. Order is kept.
 */
fun filterBinder(collection: List<CollectedCard>, query: String, cue: CueStatus?): List<CollectedCard> {
    val needle = query.trim().lowercase()
    return collection.filter { card ->
        (cue == null || card.status == cue) &&
            (needle.isEmpty() || card.matches(needle))
    }
}

private fun CollectedCard.matches(needle: String): Boolean =
    name.lowercase().contains(needle) ||
        profile.bio.lowercase().contains(needle) ||
        profile.preferences.any { it.lowercase().contains(needle) } ||
        profile.boundaries.any { it.lowercase().contains(needle) }
