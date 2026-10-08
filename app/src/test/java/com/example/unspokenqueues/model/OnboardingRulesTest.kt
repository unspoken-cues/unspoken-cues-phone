package com.example.unspokenqueues.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingRulesTest {

    private fun profile(
        name: String = "Sam",
        preferences: List<String> = emptyList(),
        boundaries: List<String> = emptyList(),
    ) = Profile(name, "", preferences, boundaries, true)

    // ---------- canFinishProfileSetup ----------

    @Test
    fun blankName_cannotFinish() {
        assertFalse(canFinishProfileSetup(profile(name = "", preferences = listOf("Text first"))))
        assertFalse(canFinishProfileSetup(profile(name = "   ", preferences = listOf("Text first"), boundaries = listOf("No photos"))))
    }

    @Test
    fun nameOnly_cannotFinish() {
        assertFalse(canFinishProfileSetup(profile()))
    }

    @Test
    fun nameAndOnePreference_canFinish() {
        assertTrue(canFinishProfileSetup(profile(preferences = listOf("Text first"))))
    }

    @Test
    fun nameAndOneBoundary_canFinish() {
        assertTrue(canFinishProfileSetup(profile(boundaries = listOf("No photos"))))
    }

    @Test
    fun nameWithBoth_canFinish() {
        assertTrue(canFinishProfileSetup(profile(preferences = listOf("Text first"), boundaries = listOf("No photos"))))
    }

    @Test
    fun whitespaceOnlyEntries_areIgnored() {
        assertFalse(canFinishProfileSetup(profile(preferences = listOf("", "   "), boundaries = listOf("\t", "\n"))))
        assertTrue(canFinishProfileSetup(profile(preferences = listOf("  ", "Text first"))))
        assertTrue(canFinishProfileSetup(profile(preferences = listOf(" "), boundaries = listOf("No photos"))))
    }

    @Test
    fun nameWithSurroundingSpaces_stillCounts() {
        assertTrue(canFinishProfileSetup(profile(name = "  Sam  ", preferences = listOf("Text first"))))
    }

    @Test
    fun otherProfileFields_doNotMatter() {
        val bare = Profile("Sam", "", listOf("Text first"), emptyList(), isPublic = false)
        val full = Profile("Sam", "Designer", listOf("Text first"), emptyList(), isPublic = true, avatarUrl = "https://x.test/a.jpg")
        assertTrue(canFinishProfileSetup(bare))
        assertTrue(canFinishProfileSetup(full))
        assertFalse(canFinishProfileSetup(full.copy(preferences = emptyList())))
    }

    // ---------- per-step checks ----------

    @Test
    fun hasDisplayName_looksOnlyAtTheName() {
        assertTrue(hasDisplayName(profile(name = "Sam")))
        assertFalse(hasDisplayName(profile(name = "", preferences = listOf("Text first"))))
        assertFalse(hasDisplayName(profile(name = " \t ")))
    }

    @Test
    fun hasPreferenceOrBoundary_looksOnlyAtTheLists() {
        assertTrue(hasPreferenceOrBoundary(profile(name = "", preferences = listOf("Text first"))))
        assertTrue(hasPreferenceOrBoundary(profile(name = "", boundaries = listOf("No photos"))))
        assertFalse(hasPreferenceOrBoundary(profile(name = "Sam")))
        assertFalse(hasPreferenceOrBoundary(profile(name = "Sam", preferences = listOf(" "), boundaries = listOf(""))))
    }
}
