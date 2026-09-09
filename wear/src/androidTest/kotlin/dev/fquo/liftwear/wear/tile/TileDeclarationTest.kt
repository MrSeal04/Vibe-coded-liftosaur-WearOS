package dev.fquo.liftwear.wear.tile

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A Tile that is not declared correctly does not fail loudly - it simply never appears in
 * the picker, and there is nothing in logcat to say why. These assertions are cheap and
 * they are the only thing standing between a manifest typo and a feature that silently
 * does not exist.
 */
@RunWith(AndroidJUnit4::class)
class TileDeclarationTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun theTileIsDiscoverableThroughTheBindIntent() {
        val intent = Intent("androidx.wear.tiles.action.BIND_TILE_PROVIDER")
            .setPackage(context.packageName)
        val matches = context.packageManager.queryIntentServices(intent, 0)
        assertEquals(1, matches.size)
        assertEquals(
            LiftWearTileService::class.java.name,
            matches.single().serviceInfo.name,
        )
    }

    /**
     * Without this permission any app on the watch could bind the service and read what is
     * in the training cache.
     */
    @Test
    fun onlyTheSystemCanBindIt() {
        val info = context.packageManager.getServiceInfo(tileComponent(context), 0)
        assertEquals(
            "com.google.android.wearable.permission.BIND_TILE_PROVIDER",
            info.permission,
        )
        assertTrue(info.exported)
    }

    @Test
    fun itCarriesALabelAndAPreviewForThePicker() {
        val info = context.packageManager.getServiceInfo(
            tileComponent(context),
            PackageManager.GET_META_DATA,
        )
        assertNotNull(info.loadLabel(context.packageManager))
        assertTrue(
            "No androidx.wear.tiles.PREVIEW resource; the picker would show a blank card.",
            info.metaData?.getInt("androidx.wear.tiles.PREVIEW", 0) != 0,
        )
    }

    /** Cheap, and the reason it exists is that the system throws when no Tile is installed. */
    @Test
    fun requestingAnUpdateNeverThrows() {
        requestTileUpdate(context)
    }
}
