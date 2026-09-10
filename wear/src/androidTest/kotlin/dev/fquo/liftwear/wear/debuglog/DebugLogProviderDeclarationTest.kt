package dev.fquo.liftwear.wear.debuglog

import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The debug log holds training history, and the provider that serves it is exported so adb can
 * reach a non-debuggable build. The permission is the only thing keeping every other app on the
 * watch out, and a manifest typo would remove it without a single error anywhere.
 */
@RunWith(AndroidJUnit4::class)
class DebugLogProviderDeclarationTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun onlyTheAdbShellCanReadTheLog() {
        val info = context.packageManager.resolveContentProvider(
            "${context.packageName}.debuglog",
            PackageManager.ComponentInfoFlags.of(0),
        )
        assertNotNull("No debug log provider is declared", info)
        assertEquals(DebugLogProvider::class.java.name, info!!.name)
        assertTrue(info.exported)
        assertEquals("android.permission.DUMP", info.readPermission)
        assertEquals("android.permission.DUMP", info.writePermission)
    }
}
