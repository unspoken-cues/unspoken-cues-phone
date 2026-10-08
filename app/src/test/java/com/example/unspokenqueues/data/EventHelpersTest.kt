package com.example.unspokenqueues.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class EventHelpersTest {

    // ---------- joinFailureMessage ----------

    @Test
    fun unknownCode_getsItsOwnMessage() {
        assertNotEquals(joinFailureMessage(null), joinFailureMessage(EventRepository.NO_SUCH_EVENT))
    }

    @Test
    fun anyOtherFailure_isTreatedAsAConnectionProblem() {
        val generic = joinFailureMessage(null)
        assertEquals(generic, joinFailureMessage("28000"))
        assertEquals(generic, joinFailureMessage("PGRST202"))
        assertEquals(generic, joinFailureMessage(""))
    }
}
