package com.example.unspokenqueues.data.chat

import android.content.Context
import android.util.AtomicFile
import android.util.Base64
import com.google.crypto.tink.HybridDecrypt
import com.google.crypto.tink.HybridEncrypt
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.PublicKeySign
import com.google.crypto.tink.PublicKeyVerify
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.TinkProtoKeysetFormat
import com.google.crypto.tink.hybrid.HybridConfig
import com.google.crypto.tink.hybrid.PredefinedHybridParameters
import com.google.crypto.tink.integration.android.AndroidKeystoreKmsClient
import com.google.crypto.tink.signature.PredefinedSignatureParameters
import com.google.crypto.tink.signature.SignatureConfig
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.MessageDigest

/** Blocking device-bound crypto. The caller must run disk, keystore and cryptographic work off the main thread. */
class ChatCrypto(context: Context, private val userId: String) {
    private val directory = File(context.noBackupFilesDir, "event-chat-v1/" + digest(userId))
    private val identity = synchronized(diskLock) {
        require(userId.isNotBlank())
        check(directory.isDirectory || directory.mkdirs())
        loadIdentity()
    }
    private val publicIdentity = identity.publicIdentity()
    val publicKey: String = publicIdentity.serialize()
    private var lastPeer: PinnedPeer? = null
    private data class PinnedPeer(val userId: String, val wire: String, val keys: PublicIdentity)

    /** The pin is scoped to the local account and persists across threads and events. */
    fun trust(peerId: String, peerKey: String) {
        trustedPeer(peerId, peerKey)
    }

    private fun trustedPeer(peerId: String, peerKey: String): PublicIdentity = synchronized(diskLock) {
        require(peerId.isNotBlank() && peerId != userId)
        require(peerKey != publicKey) { "Two accounts cannot share a chat identity." }
        lastPeer?.let { if (it.userId == peerId && it.wire == peerKey) return@synchronized it.keys }
        val parsed = PublicIdentity.parse(peerKey)
        val pin = AtomicFile(File(directory, "peer-" + digest(peerId)))
        val stored = pin.readIfPresent()
        if (stored == null) {
            pin.writeAtomically(peerKey.toByteArray())
        } else if (!stored.contentEquals(peerKey.toByteArray())) {
            throw GeneralSecurityException("This attendee’s chat identity changed. Sending is paused.")
        }
        lastPeer = PinnedPeer(peerId, peerKey, parsed)
        parsed
    }

    /** Compare each account's full fingerprint with the one displayed by its owner. */
    fun fingerprint(key: String): String {
        PublicIdentity.parse(key)
        return digest(key).uppercase(java.util.Locale.ROOT).chunked(4).joinToString(" ")
    }

    fun seal(eventId: String, threadId: String, messageId: String, peerId: String, peerKey: String, text: String): String {
        val plain = text.toByteArray()
        require(text.isNotBlank() && plain.size <= MAX_TEXT_BYTES)
        val peer = trustedPeer(peerId, peerKey)
        val context = messageContext(eventId, threadId, messageId, userId, peerId)
        val ownCopy = publicIdentity.encrypt(plain, context)
        val peerCopy = peer.encrypt(plain, context)
        val signature = identity.signing.getPrimitive(config, PublicKeySign::class.java)
            .sign(frame(context, ownCopy, peerCopy))
        return listOf("m1", encode(ownCopy), encode(peerCopy), encode(signature)).joinToString(".")
    }

    fun open(eventId: String, threadId: String, messageId: String, senderId: String, peerId: String, peerKey: String, body: String): String {
        require(senderId == userId || senderId == peerId)
        require(body.length <= 16_384)
        val peer = trustedPeer(peerId, peerKey)
        val sentByMe = senderId == userId
        val context = messageContext(eventId, threadId, messageId, senderId, if (sentByMe) peerId else userId)
        val parts = fields(body, "m1", 4)
        val senderCopy = decode(parts[1])
        val recipientCopy = decode(parts[2])
        val signer = if (sentByMe) publicIdentity else peer
        signer.signing.getPrimitive(config, PublicKeyVerify::class.java)
            .verify(decode(parts[3]), frame(context, senderCopy, recipientCopy))
        val ciphertext = if (sentByMe) senderCopy else recipientCopy
        val plaintext = identity.encryption.getPrimitive(config, HybridDecrypt::class.java).decrypt(ciphertext, context)
        require(plaintext.size <= MAX_TEXT_BYTES)
        return plaintext.toString(Charsets.UTF_8)
    }

    private class Identity(val encryption: KeysetHandle, val signing: KeysetHandle) {
        fun publicIdentity() = PublicIdentity(encryption.publicKeysetHandle, signing.publicKeysetHandle)
    }

    private class PublicIdentity(val encryption: KeysetHandle, val signing: KeysetHandle) {
        fun encrypt(plain: ByteArray, context: ByteArray): ByteArray =
            encryption.getPrimitive(config, HybridEncrypt::class.java).encrypt(plain, context)

        fun serialize() = listOf("k1", encode(TinkProtoKeysetFormat.serializeKeysetWithoutSecret(encryption, config)),
            encode(TinkProtoKeysetFormat.serializeKeysetWithoutSecret(signing, config))).joinToString(".")

        companion object {
            fun parse(wire: String): PublicIdentity {
                require(wire.length <= 2048)
                val parts = fields(wire, "k1", 3)
                val encryption = TinkProtoKeysetFormat.parseKeysetWithoutSecret(decode(parts[1]), config)
                val signing = TinkProtoKeysetFormat.parseKeysetWithoutSecret(decode(parts[2]), config)
                require(encryption.size() == 1 && encryption.primary.key.parameters == encryptionParams)
                require(signing.size() == 1 && signing.primary.key.parameters == signingParams)
                return PublicIdentity(encryption, signing)
            }
        }
    }

    private fun loadIdentity(): Identity {
        val alias = "UnspokenCues.EventChat.v1." + digest(userId)
        val uri = "android-keystore://$alias"
        val file = AtomicFile(File(directory, "identity"))
        val stored = file.readIfPresent()
        val encryptionContext = frame(userId.toByteArray(), "event-chat/identity/encryption/v1".toByteArray())
        val signingContext = frame(userId.toByteArray(), "event-chat/identity/signing/v1".toByteArray())
        if (stored != null) {
            val wrapper = AndroidKeystoreKmsClient().getAead(uri)
            val parts = fields(stored.toString(Charsets.UTF_8), "d1", 3)
            return Identity(
                TinkProtoKeysetFormat.parseEncryptedKeyset(decode(parts[1]), wrapper, encryptionContext, config),
                TinkProtoKeysetFormat.parseEncryptedKeyset(decode(parts[2]), wrapper, signingContext, config),
            )
        }
        val keystore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        check(!keystore.containsAlias(alias)) { "The stored chat identity is missing." }
        val wrapper = AndroidKeystoreKmsClient.getOrGenerateNewAeadKey(uri)
        val keys = Identity(KeysetHandle.generateNew(encryptionParams, config), KeysetHandle.generateNew(signingParams, config))
        val encrypted = TinkProtoKeysetFormat.serializeEncryptedKeyset(keys.encryption, wrapper, encryptionContext, config)
        val signed = TinkProtoKeysetFormat.serializeEncryptedKeyset(keys.signing, wrapper, signingContext, config)
        file.writeAtomically(listOf("d1", encode(encrypted), encode(signed)).joinToString(".").toByteArray())
        return keys
    }

    companion object {
        const val MAX_TEXT_BYTES = 4096
        private val diskLock = Any()
        private val config by lazy {
            HybridConfig.register()
            SignatureConfig.register()
            RegistryConfiguration.get()
        }
        private val encryptionParams = PredefinedHybridParameters.HPKE_X25519_HKDF_SHA256_AES_128_GCM
        private val signingParams = PredefinedSignatureParameters.ED25519
        private const val BASE64 = Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
        private fun encode(bytes: ByteArray): String = Base64.encodeToString(bytes, BASE64)
        private fun decode(text: String): ByteArray = Base64.decode(text, BASE64).also { require(encode(it) == text) }
        private fun digest(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }

        private fun fields(value: String, version: String, count: Int): List<String> =
            value.split('.', limit = count + 1).also { require(it.size == count && it.first() == version) }

        private fun messageContext(event: String, thread: String, message: String, sender: String, recipient: String): ByteArray {
            val fields = arrayOf("unspoken-cues/event-chat/message/v1", event, thread, message, sender, recipient)
            require(fields.all { it.isNotBlank() })
            return frame(*fields.map { it.toByteArray() }.toTypedArray())
        }

        // Explicit lengths avoid delimiter ambiguity in the authenticated transcript.
        private fun frame(vararg fields: ByteArray): ByteArray {
            val bytes = ByteArrayOutputStream()
            DataOutputStream(bytes).use { output ->
                for (field in fields) {
                    output.writeInt(field.size)
                    output.write(field)
                }
            }
            return bytes.toByteArray()
        }

        private fun AtomicFile.readIfPresent(): ByteArray? = try {
            openRead().use { it.readBytes() }
        } catch (missing: FileNotFoundException) {
            if (baseFile.exists() || File(baseFile.path + ".bak").exists()) throw missing
            null
        }

        private fun AtomicFile.writeAtomically(data: ByteArray) {
            val stream = startWrite()
            try {
                stream.write(data)
                finishWrite(stream)
            } catch (error: Throwable) {
                failWrite(stream)
                throw error
            }
            // AtomicFile logs some filesystem failures. Verify persistence before trusting new keys.
            check(readFully().contentEquals(data)) { "Couldn’t save chat identity data." }
        }
    }
}
