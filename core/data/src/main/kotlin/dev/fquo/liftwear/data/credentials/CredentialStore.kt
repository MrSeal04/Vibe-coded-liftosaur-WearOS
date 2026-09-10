package dev.fquo.liftwear.data.credentials

import android.content.Context
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.crypto.tink.Aead
import com.google.crypto.tink.integration.android.AndroidKeystore
import dev.fquo.liftwear.api.EventLog
import dev.fquo.liftwear.api.warn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

private val Context.credentialsDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "liftwear_credentials")

/**
 * Stores the Liftosaur API key encrypted at rest.
 *
 * `EncryptedSharedPreferences` was deprecated in April 2025, so the key is sealed with
 * an AES-256-GCM AEAD whose master key lives in the Android Keystore and never leaves it;
 * only the ciphertext reaches DataStore. Tink's `AndroidKeysetManager` is deprecated in
 * 1.18, so this uses [AndroidKeystore] directly - there is no keyset file to manage.
 */
class CredentialStore(
    context: Context,
    private val log: EventLog = EventLog.None,
) {

    private val appContext = context.applicationContext

    private val aead: Aead by lazy {
        if (!AndroidKeystore.hasKey(KEYSTORE_ALIAS)) {
            AndroidKeystore.generateNewAes256GcmKey(KEYSTORE_ALIAS)
        }
        AndroidKeystore.getAead(KEYSTORE_ALIAS)
    }

    /** Emits the decrypted key, or null when unpaired. Never throws on a corrupt blob. */
    val apiKey: Flow<String?> =
        appContext.credentialsDataStore.data.map { prefs -> prefs[CIPHERTEXT]?.let(::decryptOrNull) }

    suspend fun setApiKey(key: String) = withContext(Dispatchers.IO) {
        val sealed = Base64.encodeToString(
            aead.encrypt(key.toByteArray(Charsets.UTF_8), ASSOCIATED_DATA),
            Base64.NO_WRAP,
        )
        appContext.credentialsDataStore.edit { it[CIPHERTEXT] = sealed }
        Unit
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        appContext.credentialsDataStore.edit { it.remove(CIPHERTEXT) }
        Unit
    }

    /**
     * A wrong-looking key is indistinguishable from no key: returning null sends the user
     * back to setup, which is recoverable. Throwing here would brick the app instead.
     */
    private fun decryptOrNull(sealed: String): String? = runCatching {
        String(aead.decrypt(Base64.decode(sealed, Base64.NO_WRAP), ASSOCIATED_DATA), Charsets.UTF_8)
    }.onFailure {
        // Silently unpairing is the right behaviour and a baffling bug report; this says why.
        log.warn("Pairing", "the stored key could not be decrypted, so the watch is treated as unpaired", it)
    }.getOrNull()

    companion object {
        private const val KEYSTORE_ALIAS = "liftwear_credentials_master"
        private val ASSOCIATED_DATA = "liftwear-api-key".toByteArray(Charsets.UTF_8)
        private val CIPHERTEXT = stringPreferencesKey("api_key_ciphertext")

        /** Liftosaur Premium keys are prefixed; catches an obvious paste error before a round trip. */
        const val KEY_PREFIX = "lftsk_"

        fun looksLikeApiKey(raw: String): Boolean {
            val t = raw.trim()
            return t.startsWith(KEY_PREFIX) && t.length > KEY_PREFIX.length + 8
        }
    }
}
