package dev.fquo.liftwear.wear.complication

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationText
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.CountDownTimeReference
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.data.TimeDifferenceComplicationText
import androidx.wear.watchface.complications.data.TimeDifferenceStyle
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import dev.fquo.liftwear.data.LiftWearContainer
import dev.fquo.liftwear.wear.LiftWearApplication
import dev.fquo.liftwear.wear.MainActivity
import dev.fquo.liftwear.wear.debuglog.eventLog
import dev.fquo.liftwear.wear.rest.RestController
import kotlinx.coroutines.flow.first
import java.time.Instant

/**
 * The watch face complication: the rest countdown, or how far through the session you are.
 *
 * Reads Room and the rest controller, never the network, for the same reason the Tile does:
 * a complication request arrives from the watch face's process on a schedule nobody
 * controls, usually with this app dead.
 *
 * The rest countdown is a [TimeDifferenceComplicationText] built from the deadline, so the
 * *system* animates it. A complication may only be redrawn about once a minute, so a number
 * this app rendered itself would spend most of its life wrong - the third surface in this
 * app where handing the system an instant rather than a countdown is what makes it work.
 */
class LiftWearComplicationService : SuspendingComplicationDataSourceService() {

    private val container: LiftWearContainer
        get() = (applicationContext as LiftWearApplication).container

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        val content = complicationContent(
            paired = container.pairing.value == LiftWearContainer.PairingState.Paired,
            // The shared flow, as the Tile does: its decode has already run for the update
            // that asked for this.
            workout = container.workouts.workout.first(),
            rest = RestController.get(this).state.value,
            now = System.currentTimeMillis(),
        )
        eventLog().log("Complication", "requested ${request.complicationType}: $content")
        return render(request.complicationType, content, openApp(this))
    }

    /**
     * Shown in the complication picker, where there is no workout to describe. Deliberately
     * a plausible mid-session state rather than the empty one: a picker entry reading "--"
     * tells the user nothing about what they would be choosing.
     */
    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        render(
            type = type,
            content = ComplicationContent.Progress(done = 6, total = 15, exercise = "Squat"),
            tapAction = openApp(this),
        )

    internal fun render(
        type: ComplicationType,
        content: ComplicationContent,
        tapAction: PendingIntent?,
    ): ComplicationData? {
        val description = PlainComplicationText.Builder(content.contentDescription()).build()
        return when (type) {
            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(
                text = shortTextFor(content),
                contentDescription = description,
            )
                .setTitle(content.title()?.let { PlainComplicationText.Builder(it).build() })
                .setTapAction(tapAction)
                .build()

            ComplicationType.RANGED_VALUE -> {
                val (value, max) = content.range()
                RangedValueComplicationData.Builder(
                    value = value,
                    min = 0f,
                    max = max,
                    contentDescription = description,
                )
                    .setText(shortTextFor(content))
                    .setTapAction(tapAction)
                    .build()
            }

            // A type we did not advertise. Returning null is how a data source says "I have
            // nothing for that", and is not an error.
            else -> null
        }
    }

    private fun shortTextFor(content: ComplicationContent): ComplicationText =
        when (content) {
            is ComplicationContent.Resting -> TimeDifferenceComplicationText.Builder(
                // STOPWATCH is the only style that keeps seconds below an hour. The
                // alternatives drop them, which for a ninety-second rest means the watch
                // face reads "2m" for most of its life - the failure the ambient screen has
                // to live with, and does not have to here, because the system is rendering
                // this one and can afford to tick.
                style = TimeDifferenceStyle.STOPWATCH,
                countDownTimeReference = CountDownTimeReference(Instant.ofEpochMilli(content.endsAt)),
            )
                // Off by default for STOPWATCH, and worth turning on: "00:00" reads as a
                // rest still running, where "now" is what the lifter is being told.
                .setDisplayAsNow(true)
                .build()

            else -> PlainComplicationText.Builder(content.shortText()).build()
        }

}

/**
 * Tapping the complication opens the app.
 *
 * A parameter of [LiftWearComplicationService.render] rather than something it reaches for
 * itself, so the layout can be built - and tested - without a Service that has been through
 * onCreate and has a base Context attached.
 */
internal fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
    context,
    0,
    Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
)

/**
 * Redraws every instance of this complication on every watch face using it.
 *
 * Wrapped for the same reason as the Tile's: the normal state for most users is not having
 * added it at all, and that must not be an error path.
 */
fun requestComplicationUpdate(context: Context) {
    runCatching {
        ComplicationDataSourceUpdateRequester
            .create(context, complicationComponent(context))
            .requestUpdateAll()
    }
}

fun complicationComponent(context: Context): ComponentName =
    ComponentName(context, LiftWearComplicationService::class.java)
