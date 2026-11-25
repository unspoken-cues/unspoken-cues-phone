package com.example.unspokenqueues.data.chat

import android.os.Bundle
import android.os.SystemClock
import android.util.Base64
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.unspokenqueues.ui.screens.EventChatScreen
import com.example.unspokenqueues.ui.theme.UnspokenQueuesTheme
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Opt-in real relay test. Uses the same two account credentials as the local chat lab. */
@RunWith(AndroidJUnit4::class)
class EventChatBenchmarkTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val event = "60000000-0000-4000-8000-000000000011"
    private val alex = "60000000-0000-4000-8000-000000000001"
    private val blair = "60000000-0000-4000-8000-000000000002"
    private val endpoint = "http://127.0.0.1:18764"

    @Test fun deliversOnceAfterReopeningAndMeasuresForegroundDelivery() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("chatBench") == "true")
        val tokens = File(context.noBackupFilesDir, "event-chat-lab-tokens").readLines()
        require(tokens.size == 2)
        val senderClient = client(tokens[0])
        val recipientClient = client(tokens[1])
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        try {
            val sender = repository(senderClient, alex)
            val thread = runBlocking {
                val recipient = repository(recipientClient, blair)
                recipient.threads(event)
                check(blair in sender.eligiblePeers(event)) { "Benchmark event needs both eligible attendees." }
                sender.request(event, blair)
                val incoming = recipient.threads(event).single { it.peerId == alex }
                if (incoming.state == "pending") recipient.respond(incoming.id, true)
                sender.threads(event).single { it.peerId == blair && it.state == "accepted" }
            }

            val offlineId = UUID.randomUUID().toString()
            val offlineMarker = "Reconnect $offlineId"
            val start = SystemClock.elapsedRealtime()
            runBlocking {
                sender.send(event, thread, offlineId, offlineMarker)
                sender.send(event, thread, offlineId, offlineMarker)
            }
            // The recipient is now a new repository/crypto instance, with only device-persisted identity.
            val reopened = repository(recipientClient, blair)
            scenario.onActivity { activity ->
                activity.setContent {
                    UnspokenQueuesTheme { EventChatScreen(event, "Benchmark meetup", reopened, {}, alex, "Alex") }
                }
            }
            val reopenMs = awaitDisplayed(offlineMarker, start)

            val liveId = UUID.randomUUID().toString()
            val liveMarker = "Foreground $liveId"
            val liveStart = SystemClock.elapsedRealtime()
            runBlocking { sender.send(event, thread, liveId, liveMarker) }
            val liveMs = awaitDisplayed(liveMarker, liveStart)

            val stored = runBlocking {
                senderClient.postgrest.rpc("chat_messages", buildJsonObject {
                    put("p_expected_user", alex)
                    put("p_thread", thread.id)
                }).decodeList<JsonObject>()
            }
            for ((id, marker) in listOf(offlineId to offlineMarker, liveId to liveMarker)) {
                val rows = stored.filter { it["message_id"]?.jsonPrimitive?.content == id }
                assertEquals("One relay row for the client-generated message ID", 1, rows.size)
                val body = rows.single().getValue("body").jsonPrimitive.content
                assertTrue(body.startsWith("m1."))
                assertFalse(body.contains(marker))
                for (field in body.split('.').drop(1)) {
                    assertFalse(Base64.decode(field, Base64.URL_SAFE).toString(Charsets.UTF_8).contains(marker))
                }
            }
            Log.i("EventChatBenchmark", "reopen_to_display_ms=$reopenMs foreground_to_display_ms=$liveMs target_ms=$targetMs")
            instrumentation.sendStatus(0, Bundle().apply {
                putString("chatBenchmark", "reopen_to_display_ms=$reopenMs foreground_to_display_ms=$liveMs target_ms=$targetMs")
            })
            assertTrue("Reopen send-to-display took ${reopenMs}ms", reopenMs < targetMs)
            assertTrue("Foreground send-to-display took ${liveMs}ms", liveMs < targetMs)
        } finally {
            scenario.close()
            runBlocking {
                senderClient.close()
                recipientClient.close()
            }
        }
    }

    private fun repository(client: SupabaseClient, user: String) = ChatRepository(context, user, { client }, { user })

    private fun client(token: String) = createSupabaseClient(endpoint, token) {
        install(Postgrest) { customUrl = endpoint; jwtToken = token }
    }

    private fun awaitDisplayed(marker: String, since: Long): Long {
        // Query the rendered Android accessibility tree under the real activity and frame clock.
        // A Compose test clock can fast-forward the production inbox polling interval.
        while (SystemClock.elapsedRealtime() - since <= 15_000) {
            val root = instrumentation.uiAutomation.rootInActiveWindow
            if (root != null && hasVisibleText(root, marker)) return SystemClock.elapsedRealtime() - since
            SystemClock.sleep(75)
        }
        throw AssertionError("The recipient screen did not display the delivered message within 15 seconds.")
    }

    private fun hasVisibleText(node: AccessibilityNodeInfo, marker: String): Boolean {
        if (node.isVisibleToUser && node.text?.contains(marker) == true) return true
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            if (hasVisibleText(child, marker)) return true
        }
        return false
    }

    private val targetMs = 10_000L
}
