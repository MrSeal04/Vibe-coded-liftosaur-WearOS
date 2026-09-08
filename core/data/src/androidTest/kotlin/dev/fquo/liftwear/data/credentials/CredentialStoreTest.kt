package dev.fquo.liftwear.data.credentials

import androidx.test.core.app.ApplicationProvider
import com.google.crypto.tink.integration.android.AndroidKeystore
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Instrumented because the master key lives in the Android Keystore, which has no JVM
 * equivalent - a unit test would only prove that a fake was wired up correctly.
 */
@RunWith(AndroidJUnit4::class)
class CredentialStoreTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val store = CredentialStore(context)

    @Before
    fun clean() = runTest { store.clear() }

    @Test
    fun round_trips_a_key_through_the_keystore() = runTest {
        store.setApiKey("lftsk_round_trip_example")
        assertEquals("lftsk_round_trip_example", store.apiKey.first())
    }

    @Test
    fun starts_and_ends_unpaired() = runTest {
        assertNull(store.apiKey.first())
        store.setApiKey("lftsk_something")
        store.clear()
        assertNull(store.apiKey.first())
    }

    @Test
    fun the_key_is_not_readable_on_disk() = runTest {
        val secret = "lftsk_do_not_store_me_in_the_clear"
        store.setApiKey(secret)

        val files = File(context.filesDir, "datastore").walkTopDown().filter { it.isFile }.toList()
        assertTrue("expected the DataStore file to exist", files.isNotEmpty())
        files.forEach { file ->
            val bytes = file.readBytes()
            assertFalse(
                "${file.name} contains the API key in plaintext",
                String(bytes, Charsets.ISO_8859_1).contains(secret),
            )
        }
    }

    @Test
    fun an_undecryptable_blob_reads_as_unpaired_rather_than_throwing() = runTest {
        // Keystore keys really do go away - a factory reset or a lock-screen change can
        // invalidate them - and the stored ciphertext then outlives the key that sealed it.
        // A watch that crashed on startup for that reason would need a reinstall to fix;
        // reading null sends the user back to setup instead.
        store.setApiKey("lftsk_valid")
        assertEquals("lftsk_valid", store.apiKey.first())

        AndroidKeystore.deleteKey(KEYSTORE_ALIAS)
        assertNull(CredentialStore(context).apiKey.first())
    }

    @Test
    fun looksLikeApiKey_accepts_real_shapes_and_rejects_junk() {
        assertTrue(CredentialStore.looksLikeApiKey("lftsk_abcdefghij"))
        assertTrue(CredentialStore.looksLikeApiKey("  lftsk_abcdefghij  "))
        assertFalse(CredentialStore.looksLikeApiKey("lftsk_"))
        assertFalse(CredentialStore.looksLikeApiKey("sk_abcdefghijklmno"))
        assertFalse(CredentialStore.looksLikeApiKey(""))
    }

    private companion object {
        /** Mirrors CredentialStore's own alias; there is no public accessor by design. */
        const val KEYSTORE_ALIAS = "liftwear_credentials_master"
    }
}
