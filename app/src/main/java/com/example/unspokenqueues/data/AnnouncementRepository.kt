package com.example.unspokenqueues.data

import com.example.unspokenqueues.model.Announcement
import com.example.unspokenqueues.model.ANNOUNCEMENT_FEED_LIMIT
import com.example.unspokenqueues.model.validAnnouncement
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
data class AnnouncementRow(
    val id: String,
    @SerialName("event_id") val eventId: String,
    val body: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("request_id") val requestId: String,
)

fun AnnouncementRow.toAnnouncement(): Announcement = Announcement(
    id, eventId, body,
    requireNotNull(createdAt.toInstantOrNull()) { "Invalid announcement timestamp" },
)

/** RLS protects reads; the RPC owns identity, timing, validation and idempotent writes. */
open class AnnouncementRepository(client: io.github.jan.supabase.SupabaseClient? = null) {
    private val postgrest by lazy { (client ?: SupabaseClientProvider.client).postgrest }

    open suspend fun announcements(eventId: String): List<Announcement> =
        postgrest.from("event_announcements").select {
            filter { eq("event_id", eventId) }
            order("created_at", Order.DESCENDING)
            order("id", Order.DESCENDING)
            limit(ANNOUNCEMENT_FEED_LIMIT.toLong())
        }.decodeList<AnnouncementRow>().map { it.toAnnouncement() }

    /** Reuse requestId when retrying the same draft after an uncertain network response. */
    open suspend fun post(eventId: String, body: String, requestId: String): Announcement {
        require(validAnnouncement(body)) { "Announcement must contain 1–1000 characters" }
        return postgrest.rpc("post_event_announcement", buildJsonObject {
            put("p_event_id", eventId)
            put("p_body", body.trim())
            put("p_request_id", requestId)
        }).decodeAs<AnnouncementRow>().toAnnouncement()
    }
}
