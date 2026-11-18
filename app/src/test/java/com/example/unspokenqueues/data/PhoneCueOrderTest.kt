package com.example.unspokenqueues.data

import com.example.unspokenqueues.model.CueStatus
import com.example.unspokenqueues.model.watchSnapshot
import org.junit.Assert.*
import org.junit.Test

class PhoneCueOrderTest {
    private val snapshot = watchSnapshot(true, CueStatus.RED, "", null, false)
    private val initial = PhoneCueState(owner = "a", generation = "generation", revision = 1,
        status = CueStatus.RED, sharing = snapshot)
    private val request = WatchCueRequest("generation", 1, "edit", "watch", 5, CueStatus.YELLOW)

    @Test fun onlyNewerOperationsFromTheSameLineageMayUseAnOldBase() {
        assertTrue(initial.accepts(request))
        val committed = initial.confirm(request.status, request)
        assertFalse(committed.accepts(request))
        assertFalse(committed.accepts(request.copy(sequence = 6, baseRevision = committed.revision)))
        val latest = request.copy(sequence = 8, operation = "latest")
        assertTrue(committed.accepts(latest))
        assertFalse(committed.accepts(latest.copy(writer = "another watch")))
        assertFalse(committed.accepts(latest.copy(generation = "expired")))
        assertFalse(committed.confirm(CueStatus.PURPLE).accepts(latest))
        assertFalse(initial.copy(sharing = null).accepts(request))
        assertFalse(initial.copy(inFlight = latest).accepts(request))
    }
}
