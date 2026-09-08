package dev.fquo.liftwear.wear.rest

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.SerializationHelper
import androidx.wear.ongoing.Status
import dev.fquo.liftwear.wear.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Guards the two silent traps in the Ongoing Activity API.
 *
 * Both fail by posting a perfectly ordinary notification with no watch-face chip, nothing in
 * logcat, and no visible extras in `dumpsys` (which does not print Bundle extras at all). The
 * only way to know is to read the bundle back, which is what this does.
 */
@RunWith(AndroidJUnit4::class)
class OngoingActivityNotificationTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val endsAt = System.currentTimeMillis() + 90_000

    private fun builder() = NotificationCompat.Builder(context, "test_channel")
        .setSmallIcon(android.R.drawable.ic_media_play)
        .setContentTitle("Squat")
        .setOngoing(true)

    private fun status() = Status.Builder()
        .addTemplate("Rest #timer#")
        .addPart("timer", Status.TimerPart(endsAt))
        .build()

    // A touch intent is mandatory - OngoingActivity.Builder.build() throws without one.
    private fun touchIntent() = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun ongoing(b: NotificationCompat.Builder) =
        OngoingActivity.Builder(context, 1001, b)
            .setStaticIcon(android.R.drawable.ic_media_play)
            .setTouchIntent(touchIntent())
            .setStatus(status())
            .build()

    @Test
    fun apply_extends_the_builder_it_was_given_not_the_one_you_build_from() {
        // The first trap: apply() returns nothing and mutates its own builder. Building from
        // a second builder throws the extras away.
        val given = builder()
        ongoing(given).apply(context)

        val other = builder()
        assertFalse(
            "a different builder must not somehow carry the extras",
            SerializationHelper.hasOngoingActivity(other.build()),
        )
    }

    @Test
    fun apply_then_build_on_the_same_builder_keeps_the_bundle() {
        // The contract the service relies on. Historically build() dropped this bundle
        // (b/169394642), which is why the library carries a workaround inside update(); on
        // this platform it survives. If this ever starts failing, update() is still the
        // supported path and nothing needs changing - but the reason will have come back.
        val b = builder()
        ongoing(b).apply(context)
        assertTrue(SerializationHelper.hasOngoingActivity(b.build()))
    }

    @Test
    fun the_ongoing_activity_reads_back_off_the_posted_notification() {
        // What the system does to find the watch-face chip. If this cannot recover it,
        // neither can Wear OS.
        val b = builder()
        ongoing(b).apply(context)
        assertNotNull(SerializationHelper.create(b.build()))
    }

    @Test
    fun the_status_carries_a_timer_the_system_can_animate() {
        // A TimerPart is what lets the system render a per-second countdown without the app
        // being scheduled at all - the point of the whole arrangement.
        val part = status().getPart("timer")
        assertNotNull(part)
        assertTrue("must be a countdown, not a stopwatch", part is Status.TimerPart)
        assertEquals(listOf<CharSequence>("Rest #timer#"), status().templates)
    }
}
