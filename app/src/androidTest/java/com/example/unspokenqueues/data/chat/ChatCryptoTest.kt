package com.example.unspokenqueues.data.chat

import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChatCryptoTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val aliceId = UUID.randomUUID().toString()
    private val bobId = UUID.randomUUID().toString()
    private val otherId = UUID.randomUUID().toString()
    private val alice by lazy { ChatCrypto(context, aliceId) }
    private val bob by lazy { ChatCrypto(context, bobId) }
    private val other by lazy { ChatCrypto(context, otherId) }
    private val event = UUID.randomUUID().toString()
    private val thread = UUID.randomUUID().toString()
    private val message = UUID.randomUUID().toString()
    private val marker = "This private chat mentions a unique café " + UUID.randomUUID()

    @After fun removeTestIdentities() {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        for (account in listOf(aliceId, bobId, otherId)) {
            val hash = MessageDigest.getInstance("SHA-256").digest(account.toByteArray()).joinToString("") { "%02x".format(it) }
            store.deleteEntry("UnspokenCues.EventChat.v1.$hash")
            File(context.noBackupFilesDir, "event-chat-v1/$hash").deleteRecursively()
        }
    }

    @Test fun bothPhonesReadOwnAndReceivedHistoryAfterReopening() {
        val body = alice.seal(event, thread, message, bobId, bob.publicKey, marker)
        val again = alice.seal(event, thread, message, bobId, bob.publicKey, marker)
        assertNotEquals(body, again)
        assertNotEquals(alice.publicKey, bob.publicKey)
        val reopenedAlice = ChatCrypto(context, aliceId)
        val reopenedBob = ChatCrypto(context, bobId)
        assertEquals(alice.publicKey, reopenedAlice.publicKey)
        assertEquals(bob.publicKey, reopenedBob.publicKey)
        assertEquals(marker, reopenedAlice.open(event, thread, message, aliceId, bobId, bob.publicKey, body))
        assertEquals(marker, reopenedBob.open(event, thread, message, aliceId, aliceId, alice.publicKey, body))
        assertEquals(alice.fingerprint(alice.publicKey), bob.fingerprint(alice.publicKey))
        assertFalse(body.contains(marker))
        for (field in body.split('.').drop(1)) {
            assertFalse(Base64.decode(field, Base64.URL_SAFE).toString(Charsets.UTF_8).contains(marker))
        }
    }

    @Test fun rejectsTamperingRecipientSubstitutionAndContextChanges() {
        val body = alice.seal(event, thread, message, bobId, bob.publicKey, marker)
        fun read(eventId: String = event, threadId: String = thread, messageId: String = message,
                 sender: String = aliceId, envelope: String = body) =
            bob.open(eventId, threadId, messageId, sender, aliceId, alice.publicKey, envelope)
        read()
        rejected { read(eventId = thread) }
        rejected { read(threadId = message) }
        rejected { read(messageId = event) }
        rejected { read(sender = bobId) }
        rejected { other.open(event, thread, message, aliceId, aliceId, alice.publicKey, body) }
        rejected { bob.open(event, thread, message, otherId, otherId, other.publicKey, body) }
        for (i in 1..3) {
            val parts = body.split('.').toMutableList()
            val first = if (parts[i][0] == 'A') 'B' else 'A'
            parts[i] = first + parts[i].drop(1)
            rejected { read(envelope = parts.joinToString(".")) }
        }
    }

    @Test fun pinnedIdentityChangeFailsClosedAcrossSessions() {
        alice.trust(bobId, bob.publicKey)
        val reopenedAlice = ChatCrypto(context, aliceId)
        rejected { reopenedAlice.trust(bobId, other.publicKey) }
        rejected { reopenedAlice.seal(event, thread, message, bobId, other.publicKey, marker) }
        val impostorBody = other.seal(event, thread, message, aliceId, alice.publicKey, "forged")
        rejected { reopenedAlice.open(event, thread, message, bobId, bobId, other.publicKey, impostorBody) }
        assertEquals(marker, bob.open(event, thread, message, aliceId, aliceId, alice.publicKey,
            reopenedAlice.seal(event, thread, message, bobId, bob.publicKey, marker)))
    }

    @Test fun losingTheKeystoreWrappingKeyCannotSilentlyReplaceAnIdentity() {
        val initial = alice.publicKey
        val hash = MessageDigest.getInstance("SHA-256").digest(aliceId.toByteArray()).joinToString("") { "%02x".format(it) }
        val file = File(context.noBackupFilesDir, "event-chat-v1/$hash/identity")
        val wrappedIdentity = file.readText()
        assertTrue(wrappedIdentity.startsWith("d1."))
        assertFalse(wrappedIdentity.contains(initial))
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry("UnspokenCues.EventChat.v1.$hash")
        assertTrue(runCatching { ChatCrypto(context, aliceId) }.isFailure)
        assertEquals(wrappedIdentity, file.readText())
    }

    @Test fun plaintextLengthBoundUsesUtf8Bytes() {
        val limit = "é".repeat(ChatCrypto.MAX_TEXT_BYTES / 2)
        val body = alice.seal(event, thread, message, bobId, bob.publicKey, limit)
        assertEquals(limit, bob.open(event, thread, message, aliceId, aliceId, alice.publicKey, body))
        rejected { alice.seal(event, thread, message, bobId, bob.publicKey, limit + "a") }
        assertTrue(body.length < 16_384)
    }

    private fun rejected(block: () -> Unit) {
        val error = runCatching(block).exceptionOrNull()
        assertTrue("Expected authentication or input validation to reject the operation",
            error is java.security.GeneralSecurityException || error is IllegalArgumentException)
    }
}
