package dev.fquo.liftwear.wear.complication

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * The complication builders validate on `build()` - a RANGED_VALUE with no text, title or
 * image throws, and so does a value outside its own range. None of that shows up at compile
 * time, and on a watch face a throw here is invisible: the slot simply stays empty.
 */
@RunWith(AndroidJUnit4::class)
class ComplicationServiceTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val service = LiftWearComplicationService()

    /**
     * Supplied rather than taken from the service: a bare service instance has no base
     * Context, which is exactly the seam the render signature was changed to expose.
     */
    private val tap get() = openApp(context)

    private val states = listOf(
        ComplicationContent.Unpaired,
        ComplicationContent.Idle,
        ComplicationContent.Progress(done = 0, total = 0, exercise = null),
        ComplicationContent.Progress(done = 6, total = 15, exercise = "Squat"),
        ComplicationContent.Progress(done = 15, total = 15, exercise = null),
        ComplicationContent.Resting(
            endsAt = Instant.now().plusSeconds(90).toEpochMilli(),
            restingFrom = "Squat",
        ),
    )

    @Test
    fun everyStateBuildsForEveryAdvertisedType() {
        for (content in states) {
            for (type in listOf(ComplicationType.SHORT_TEXT, ComplicationType.RANGED_VALUE)) {
                assertNotNull("$content as $type", service.render(type, content, tap))
            }
        }
    }

    @Test
    fun theRangedValueStaysInsideItsOwnRange() {
        for (content in states) {
            val data = service.render(ComplicationType.RANGED_VALUE, content, tap)
            require(data is RangedValueComplicationData)
            assertTrue(
                "$content produced ${data.value} outside ${data.min}..${data.max}",
                data.value in data.min..data.max,
            )
        }
    }

    /**
     * The countdown has to be time-dependent text the system renders. A plain string here
     * would freeze at whatever the value was when the watch face last asked, which on a
     * complication is about once a minute.
     */
    @Test
    fun theRestCountdownIsRenderedByTheSystem() {
        val resting = ComplicationContent.Resting(
            endsAt = Instant.now().plusSeconds(90).toEpochMilli(),
            restingFrom = "Squat",
        )
        val data = service.render(ComplicationType.SHORT_TEXT, resting, tap)
        require(data is ShortTextComplicationData)

        val now = Instant.now()
        val atNow = data.text.getTextAt(context.resources, now).toString()
        val atLater = data.text.getTextAt(context.resources, now.plusSeconds(45)).toString()
        assertTrue("text did not change over 45s: '$atNow' vs '$atLater'", atNow != atLater)

        // And it has to tick in *seconds*. The styles that report only whole minutes leave a
        // ninety-second rest reading "2m" for most of its life.
        val atOneSecondLater = data.text.getTextAt(context.resources, now.plusSeconds(1)).toString()
        assertTrue(
            "countdown is not second-by-second: '$atNow' then '$atOneSecondLater'",
            atNow != atOneSecondLater,
        )
    }

    /** Zero has to read as "go", not as a rest that is still running. */
    @Test
    fun theCountdownSaysNowRatherThanZero() {
        val ends = Instant.now()
        val data = service.render(
            ComplicationType.SHORT_TEXT,
            ComplicationContent.Resting(endsAt = ends.toEpochMilli(), restingFrom = "Squat"),
            tap,
        )
        require(data is ShortTextComplicationData)
        val atZero = data.text.getTextAt(context.resources, ends.plusSeconds(1)).toString()
        assertTrue("expected a 'now'-style label, got '$atZero'", !atZero.contains("00:00"))
    }

    /** A type we never advertised is not an error - it is "nothing for that". */
    @Test
    fun anUnsupportedTypeYieldsNothingRatherThanThrowing() {
        assertNull(service.render(ComplicationType.LONG_TEXT, ComplicationContent.Idle, tap))
    }

    /**
     * The picker shows this. An entry reading "--" tells the user nothing about what they
     * would be choosing, so the preview is a plausible mid-session state.
     */
    @Test
    fun thePreviewShowsSomethingWorthChoosing() {
        val preview = service.render(
            ComplicationType.SHORT_TEXT,
            ComplicationContent.Progress(done = 6, total = 15, exercise = "Squat"),
            tap,
        )
        require(preview is ShortTextComplicationData)
        assertEquals("6/15", preview.text.getTextAt(context.resources, Instant.now()).toString())
        assertEquals("Squat", preview.title!!.getTextAt(context.resources, Instant.now()).toString())
    }

    // --- the manifest, which fails silently when it is wrong ---

    @Test
    fun theComplicationIsDiscoverableAndProtected() {
        val intent = Intent("android.support.wearable.complications.ACTION_COMPLICATION_UPDATE_REQUEST")
            .setPackage(context.packageName)
        val matches = context.packageManager.queryIntentServices(intent, 0)
        assertEquals(1, matches.size)
        assertEquals(LiftWearComplicationService::class.java.name, matches.single().serviceInfo.name)

        val info = context.packageManager.getServiceInfo(
            complicationComponent(context),
            PackageManager.GET_META_DATA,
        )
        assertEquals(
            "com.google.android.wearable.permission.BIND_COMPLICATION_PROVIDER",
            info.permission,
        )
        assertEquals(
            "SHORT_TEXT,RANGED_VALUE",
            info.metaData?.getString("android.support.wearable.complications.SUPPORTED_TYPES"),
        )
    }

    @Test
    fun requestingAnUpdateNeverThrows() {
        requestComplicationUpdate(context)
    }
}
