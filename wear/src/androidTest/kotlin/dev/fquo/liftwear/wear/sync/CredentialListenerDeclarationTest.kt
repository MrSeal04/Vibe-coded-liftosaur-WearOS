package dev.fquo.liftwear.wear.sync

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The credential hand-off is delivered by Google Play services binding this service, and a
 * refused bind is completely silent from inside this app.
 *
 * Found on a Galaxy Watch 4: the service carried
 * `android:permission="com.google.android.gms.permission.BIND_LISTENER_SERVICE"`, GMS on that
 * build does not hold it, and every delivery was rejected -
 *
 *     Permission Denial: Accessing service .../.sync.CredentialListenerService from
 *     pid=..., uid=10109 requires com.google.android.gms.permission.BIND_LISTENER_SERVICE
 *
 * with the SecurityException landing inside WearableService, not here. The key then only ever
 * arrived through SetupViewModel's sweep - never while the app was closed, which is the entire
 * reason this service exists. `android:permission` on a <service> gates who may *bind* to it,
 * so declaring one GMS lacks disables the listener rather than protecting it.
 */
@RunWith(AndroidJUnit4::class)
class CredentialListenerDeclarationTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun component() =
        ComponentName(context, CredentialListenerService::class.java)

    /** The regression that cost this project a hand-off: any bind permission at all is wrong. */
    @Test
    fun nothingGatesWhoMayBindIt() {
        val info = context.packageManager.getServiceInfo(component(), 0)
        assertNull(
            "A bind permission here silently disables delivery: GMS does not hold " +
                "BIND_LISTENER_SERVICE on every device, and the refusal never reaches this app.",
            info.permission,
        )
        assertTrue("GMS cannot bind a service that is not exported", info.exported)
    }

    /** A path typo means the service is simply never woken, with nothing in logcat. */
    @Test
    fun itIsDiscoverableForTheCredentialsPathOnly() {
        val intent = Intent("com.google.android.gms.wearable.DATA_CHANGED")
            .setPackage(context.packageName)
            .setDataAndType(
                android.net.Uri.parse("wear://node/liftwear/credentials"),
                null,
            )
        val matches = context.packageManager.queryIntentServices(intent, 0)
        assertEquals(1, matches.size)
        assertEquals(
            CredentialListenerService::class.java.name,
            matches.single().serviceInfo.name,
        )
    }

    /**
     * The filter is deliberately the literal credentials path. A prefix filter would also
     * wake this service for the watch's own ack on /liftwear/credentials/ack.
     */
    @Test
    fun theAckPathDoesNotWakeIt() {
        val intent = Intent("com.google.android.gms.wearable.DATA_CHANGED")
            .setPackage(context.packageName)
            .setDataAndType(
                android.net.Uri.parse("wear://node/liftwear/credentials/ack"),
                null,
            )
        assertTrue(context.packageManager.queryIntentServices(intent, 0).isEmpty())
    }
}
