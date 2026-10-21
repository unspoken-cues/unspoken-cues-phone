package com.example.unspokenqueues.model

import org.junit.Assert.assertEquals
import org.junit.Test

class BinderTest {

    private fun card(name: String, status: CueStatus, bio: String = "", prefs: List<String> = emptyList(), bounds: List<String> = emptyList()) =
        CollectedCard("id-$name", Profile(name, bio, prefs, bounds, true), status)

    private val alex = card("Alex Rivera", CueStatus.GREEN, bio = "Designer", prefs = listOf("Small groups"))
    private val maya = card("Maya Chen", CueStatus.YELLOW, bounds = listOf("No photos"))
    private val sam = card("Sam Ortiz", CueStatus.GREEN)
    private val all = listOf(alex, maya, sam)

    @Test
    fun binderCounts_includesEveryCue_evenWithNone() {
        assertEquals(
            mapOf(CueStatus.GREEN to 2, CueStatus.YELLOW to 1, CueStatus.RED to 0, CueStatus.PURPLE to 0),
            binderCounts(all),
        )
        assertEquals(CueStatus.entries.associateWith { 0 }, binderCounts(emptyList()))
    }

    @Test
    fun filterBinder_withNothingSet_returnsEveryCardInOrder() {
        assertEquals(all, filterBinder(all, "  ", null))
    }

    @Test
    fun filterBinder_byCue() {
        assertEquals(listOf(alex, sam), filterBinder(all, "", CueStatus.GREEN))
        assertEquals(emptyList<CollectedCard>(), filterBinder(all, "", CueStatus.RED))
    }

    @Test
    fun filterBinder_searchIsCaseInsensitive_andReachesBioPreferencesAndBoundaries() {
        assertEquals(listOf(maya), filterBinder(all, "MAYA", null))
        assertEquals(listOf(alex), filterBinder(all, "designer", null))
        assertEquals(listOf(alex), filterBinder(all, "small", null))
        assertEquals(listOf(maya), filterBinder(all, "photos", null))
    }

    @Test
    fun filterBinder_combinesSearchAndCue() {
        assertEquals(listOf(sam), filterBinder(all, "sam", CueStatus.GREEN))
        assertEquals(emptyList<CollectedCard>(), filterBinder(all, "maya", CueStatus.GREEN))
    }
}
