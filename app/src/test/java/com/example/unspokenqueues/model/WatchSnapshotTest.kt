package com.example.unspokenqueues.model

import org.junit.Assert.*
import org.junit.Test

class WatchSnapshotTest {
    @Test fun signedOutPayloadCannotCarryThePreviousUsersIdentity() {
        val payload = watchSnapshot(false, CueStatus.RED, "Old account", "a".repeat(32), true)
        assertFalse(payload.active)
        assertEquals("", payload.name)
        assertEquals("", payload.token)
        assertEquals("", payload.status)
    }
    @Test fun privateProfileWithholdsItsSharingToken() {
        val payload = watchSnapshot(true, CueStatus.PURPLE, "Alex", "a".repeat(32), false)
        assertEquals("PURPLE", payload.status)
        assertEquals("", payload.token)
        assertFalse(payload.isPublic)
    }
    @Test fun publicProfilePublishesOnlyAValidCardToken() {
        assertEquals("a".repeat(32), watchSnapshot(true, CueStatus.GREEN, "Alex", "A".repeat(32), true).token)
        assertEquals("", watchSnapshot(true, CueStatus.GREEN, "Alex", "bad", true).token)
    }
    @Test fun confirmedDifferentAccountClearsTheOldWatchIdentityWhileLoading() {
        val transition = watchSnapshotWhileLoading("new-account", "old-account")
        assertNotNull(transition)
        assertFalse(transition!!.active)
        assertEquals("", transition.token)
    }
    @Test fun loadingTheSameAccountDoesNotEraseItsOfflineWatchCache() {
        assertNull(watchSnapshotWhileLoading("same-account", "same-account"))
        assertNull(watchSnapshotWhileLoading(null, "old-account"))
    }
}
