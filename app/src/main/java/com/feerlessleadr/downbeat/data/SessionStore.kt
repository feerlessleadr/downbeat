package com.feerlessleadr.downbeat.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** What is needed to reconnect: the address the user typed and a Music Assistant access token. Never a password. */
@Serializable
data class Session(val serverUrl: String, val token: String)

interface SessionStore {
    fun load(): Session?
    fun save(session: Session)
    fun clear()
}

/**
 * Keeps the session encrypted with an AES-GCM key that lives in the Android Keystore (the key never leaves the
 * secure hardware or Keystore process). Only the encrypted blob is in SharedPreferences. If decryption fails (for
 * example after a restore onto another phone) the session is treated as absent and the user signs in again.
 * Not covered by unit tests: it needs a real Keystore (verify on a device).
 */
class KeystoreSessionStore(context: Context) : SessionStore {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun load(): Session? {
        val blob = prefs.getString(KEY_BLOB, null) ?: return null
        return try {
            val (ivText, dataText) = blob.split(":", limit = 2)
            val cipher = Cipher.getInstance(TRANSFORM)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(ivText, Base64.NO_WRAP)))
            val json = cipher.doFinal(Base64.decode(dataText, Base64.NO_WRAP)).decodeToString()
            Json.decodeFromString(Session.serializer(), json)
        } catch (e: Exception) {
            null
        }
    }

    override fun save(session: Session) {
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val data = cipher.doFinal(Json.encodeToString(Session.serializer(), session).encodeToByteArray())
        val blob = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(data, Base64.NO_WRAP)
        prefs.edit().putString(KEY_BLOB, blob).apply()
    }

    override fun clear() {
        prefs.edit().remove(KEY_BLOB).apply()
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val PREFS = "downbeat_session"
        const val KEY_BLOB = "blob"
        const val ALIAS = "downbeat_session_key"
        const val TRANSFORM = "AES/GCM/NoPadding"
    }
}
