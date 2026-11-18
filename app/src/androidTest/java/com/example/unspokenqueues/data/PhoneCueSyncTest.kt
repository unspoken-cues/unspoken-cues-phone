package com.example.unspokenqueues.data

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.example.unspokenqueues.model.*
import com.example.unspokenqueues.ui.screens.SettingsScreen
import com.example.unspokenqueues.ui.theme.UnspokenQueuesTheme
import com.google.android.gms.wearable.DataMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class PhoneCueSyncTest {
    @get:Rule val compose = createComposeRule()

    private val prefs = InstrumentationRegistry.getInstrumentation().targetContext
        .getSharedPreferences("cue-sync-test-" + UUID.randomUUID(), Context.MODE_PRIVATE)
    private val writer = UUID.randomUUID().toString()
    private val owner = "account-a"
    private var signedIn: String? = owner
    private var row = ProfileRow(owner, status = "GREEN", updatedAt = "0")
    private var version = 0L
    private var delayNextPatch = false
    private var delayedPatch: (() -> ProfileRow?)? = null
    private val writes = mutableListOf<CueStatus>()
    private var offline = false
    private var beforeWrite: suspend () -> Unit = {}
    private val backend = object : CueBackend {
        override fun userId() = signedIn
        override suspend fun profile(id: String): ProfileRow = row.also { check(it.id == id) }
        override suspend fun write(id: String, cue: CueStatus, expectedUpdatedAt: String): ProfileRow? {
            beforeWrite()
            check(!offline && id == signedIn && id == row.id)
            val patch = {
                if (row.updatedAt != expectedUpdatedAt) null else {
                    writes += cue
                    row = row.copy(status = cue.name, updatedAt = (++version).toString())
                    row
                }
            }
            if (delayNextPatch) {
                delayNextPatch = false
                delayedPatch = patch
                throw java.io.IOException("Response lost before the server completed the request")
            }
            return patch()
        }
    }
    private var pending: List<WatchCueRequest> = emptyList()
    private var published = DataMap()
    private val transport = object : CueTransport {
        override suspend fun requests() = pending
        override suspend fun publish(state: PhoneCueState, force: Boolean) {
            published = companionRequest(prefs, state, force).dataMap
        }
    }
    private fun restart() = PhoneCueSync(prefs, backend, transport)
    private var sync = restart()
    private val shared = watchSnapshot(true, CueStatus.GREEN, "Alex", "a".repeat(32), true)

    private suspend fun start() {
        sync.loadProfile(owner)
        sync.sharing(owner, shared)
        sync.reconcile()
    }

    private fun request(cue: CueStatus, sequence: Long, base: Long = published.getLong("cueRevision")): WatchCueRequest {
        val map = DataMap().apply {
            putInt("schema", 1)
            putString("generation", checkNotNull(published.getString("generation")))
            putLong("baseRevision", base)
            putString("operation", UUID.randomUUID().toString())
            putString("writer", writer)
            putLong("sequence", sequence)
            putString("status", cue.name)
        }
        return checkNotNull(readCueRequest(CUE_REQUEST_PATH, map))
    }

    @Test fun eachWatchCueCommitsThenPublishesAndAPhoneEditReachesTheWatch() = runBlocking {
        start()
        CueStatus.entries.forEachIndexed { index, cue ->
            val op = request(cue, index + 1L)
            pending = listOf(op)
            sync.reconcile()
            assertEquals(cue.name, row.status)
            assertEquals(cue, sync.state.value.status)
            assertEquals(op.operation, published.getString("receipt"))
            assertEquals(cue.name, published.getString("status"))
            assertEquals(index + 1, writes.size)
            sync.reconcile()
            assertEquals(index + 1, writes.size)
        }
        sync.phoneChoice(owner, CueStatus.RED)
        sync.reconcile()
        assertEquals("RED", published.getString("status"))
        assertEquals("", published.getString("receipt"))
        assertEquals(CueStatus.entries.size + 1, writes.size)
    }

    @Test fun failedWriteSurvivesRestartAndALostResponseIsRecoveredFromTheServer() = runBlocking {
        start()
        val edit = request(CueStatus.RED, 1)
        pending = listOf(edit)
        offline = true
        assertTrue(runCatching { sync.reconcile() }.isFailure)
        assertEquals("", published.getString("receipt"))
        assertEquals("GREEN", published.getString("status"))
        assertTrue(sync.feedback.value.contains("pending"))
        sync = restart()
        assertEquals(edit, sync.state.value.inFlight)
        offline = false
        // Model a committed write whose HTTP response never reached the phone.
        row = row.copy(status = "RED")
        sync.reconcile()
        assertEquals(edit.operation, published.getString("receipt"))
        assertEquals("RED", published.getString("status"))
        assertTrue(writes.isEmpty())
    }

    @Test fun supersedingOfflineChoicesAndConcurrentPhoneReselectionDoNotRollBack() = runBlocking {
        start()
        val base = published.getLong("cueRevision")
        val first = request(CueStatus.RED, 1, base)
        val older = request(CueStatus.GREEN, 2, base)
        val latest = request(CueStatus.PURPLE, 3, base)
        pending = listOf(first)
        sync.reconcile()
        pending = listOf(latest)
        val writing = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        beforeWrite = { writing.complete(Unit); release.await() }
        val watchWrite = async { sync.reconcile() }
        writing.await()
        val phoneWrite = async { sync.phoneChoice(owner, CueStatus.PURPLE) }
        release.complete(Unit)
        watchWrite.await()
        phoneWrite.await()
        pending = listOf(older, latest)
        sync.reconcile(true)
        assertEquals(listOf(CueStatus.RED, CueStatus.PURPLE, CueStatus.PURPLE), writes)
        assertEquals("", published.getString("receipt"))
        assertEquals("PURPLE", row.status)
    }

    @Test fun delayedServerPatchCannotUndoANewerSameValuePhoneChoiceAfterRestart() = runBlocking {
        start()
        pending = listOf(request(CueStatus.RED, 1))
        delayNextPatch = true
        assertTrue(runCatching { sync.reconcile() }.isFailure)
        sync = restart()
        sync.phoneChoice(owner, CueStatus.GREEN)
        assertNull(checkNotNull(delayedPatch).invoke())
        sync.reconcile(true)
        assertEquals(listOf(CueStatus.GREEN), writes)
        assertEquals("GREEN", published.getString("status"))
        assertEquals("", published.getString("receipt"))
    }

    @Test fun accountReplacementAndPrivateServerProfileCannotReuseTheOldTokenOrReceipt() = runBlocking {
        start()
        val oldGeneration = published.getString("generation")
        pending = listOf(request(CueStatus.PURPLE, 1))
        row = row.copy(privateMode = true)
        sync.reconcile(verifyProfile = true)
        assertEquals("", published.getString("token"))
        assertFalse(published.getBoolean("public"))
        sync.phoneChoice(owner, CueStatus.GREEN)
        signedIn = "account-b"
        row = ProfileRow("account-b", status = "GREEN", updatedAt = "0")
        sync.loadProfile("account-b")
        sync.sharing("account-b", shared)
        sync.reconcile()
        assertNotEquals(oldGeneration, published.getString("generation"))
        assertEquals("GREEN", row.status)
        val accountB = published.getString("generation")
        signedIn = null
        sync.signedOut()
        sync.reconcile()
        assertFalse(published.getBoolean("active"))
        assertEquals("", published.getString("name"))
        assertEquals("", published.getString("token"))
        signedIn = "account-b"
        sync.loadProfile("account-b")
        sync.sharing("account-b", shared)
        sync.reconcile()
        assertNotEquals(accountB, published.getString("generation"))
        assertFalse(published.keySet().contains("owner"))
    }

    @Test fun incompleteLegacyAndMalformedRequestsCannotWrite() {
        val malformed = DataMap().apply {
            putInt("schema", 1)
            putString("status", "RED")
        }
        assertNull(readCueRequest(CUE_REQUEST_PATH, malformed))
        malformed.putString("generation", writer)
        malformed.putString("writer", writer)
        malformed.putString("operation", writer)
        malformed.putLong("sequence", 1)
        malformed.putLong("baseRevision", 1)
        assertNull(readCueRequest("/unspoken-cues/state", malformed))
        malformed.putString("status", "blue")
        assertNull(readCueRequest(CUE_REQUEST_PATH, malformed))
        malformed.putString("status", "RED")
        malformed.putString("writer", "invalid")
        assertNull(readCueRequest(CUE_REQUEST_PATH, malformed))
    }

    @Test fun settingsSyncNowReadsARetainedRequestEvenWhenTheCueIsUnchanged() {
        runBlocking { start() }
        val initial = published.getLong("revision")
        compose.setContent {
            var retry by remember { mutableIntStateOf(0) }
            var watch by remember { mutableStateOf(WatchConnection.DISCONNECTED) }
            val feedback by sync.feedback.collectAsState()
            UnspokenQueuesTheme {
                WatchSync(sync, shared, owner, retry, { watch = it }, {})
                SettingsScreen(safety = SafetyRepository(), account = AuthRepository(), watch = watch,
                    syncFeedback = feedback, themeMode = ThemeMode.SYSTEM, onThemeModeChange = {},
                    onReconnect = { retry++ }, onSignOut = {}, onAccountDeleted = {}, onBack = {})
            }
        }
        compose.runOnIdle { pending = listOf(request(CueStatus.GREEN, 1)) }
        compose.onNodeWithText("Sync now").performScrollTo().performClick()
        compose.waitUntil(5000) { published.getString("receipt") == pending.single().operation }
        assertEquals(listOf(CueStatus.GREEN), writes)
        assertTrue(published.getLong("revision") > initial)
        compose.onNodeWithText("Profile cue confirmed.").assertExists()
    }
}
