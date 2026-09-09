package dev.fquo.liftwear.wear.ambient

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The ambient path depends on a shared library this app does not ship and cannot compile
 * against - `wear-sdk`, loaded from the device's own framework. Whether it is there is a
 * property of the watch, not of the build, so it is only answerable on a device.
 *
 * Run on the Wear OS 4 (API 33) and Wear OS 6 (API 36) images this app supports.
 */
@RunWith(AndroidJUnit4::class)
class AmbientSupportTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun theWearSdkIsOnThisWatch() {
        // The uses-library entry that pulls it in is merged from the Wear Compose foundation
        // AAR with required="false", so a missing library is an install-time no-op and a
        // runtime LinkageError. Naming the classes explicitly says which ones matter.
        assertNotNull(Class.forName("com.google.wear.Sdk"))
        assertNotNull(Class.forName("com.google.wear.services.ambient.AmbientManager"))
    }

    @Test
    fun ambientIsReportedAvailable() {
        assertTrue(
            "No AmbientManager on API ${Build.VERSION.SDK_INT}; the app stays interactive, " +
                "which is correct but means ambient mode was never exercised here.",
            AmbientSupport.isAvailable(context),
        )
    }

    @Test
    fun theProbeIsCachedRatherThanRepeated() {
        val first = AmbientSupport.isAvailable(context)
        repeat(50) { assertTrue(AmbientSupport.isAvailable(context) == first) }
    }
}
