package com.example.unspokenqueues.data

import io.github.jan.supabase.postgrest.PropertyConversionMethod
import io.github.jan.supabase.postgrest.query.filter.PostgrestFilterBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.Instant

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

    // ---------- notOverAt ----------

    @Test
    fun notOverAt_buildsTheDiscoverFilter() {
        val filter = PostgrestFilterBuilder(PropertyConversionMethod.NONE)
            .apply { notOverAt(Instant.parse("2026-11-17T12:00:00Z")) }

        // Exactly what PostgREST receives as the `or` query parameter (values with ':' are quoted).
        assertEquals(
            mapOf(
                "or" to listOf(
                    "(ends_at.gt.\"2026-11-17T12:00:00Z\"," +
                        "and(ends_at.is.null,starts_at.gt.\"2026-11-17T09:00:00Z\")," +
                        "starts_at.is.null)",
                ),
            ),
            filter.params,
        )
    }
}
