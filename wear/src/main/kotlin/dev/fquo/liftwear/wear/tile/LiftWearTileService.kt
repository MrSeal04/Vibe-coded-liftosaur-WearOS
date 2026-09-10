package dev.fquo.liftwear.wear.tile

import android.content.ComponentName
import android.content.Context
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.layout.column
import androidx.wear.protolayout.material3.MaterialScope
import androidx.wear.protolayout.material3.Typography
import androidx.wear.protolayout.material3.primaryLayout
import androidx.wear.protolayout.material3.text
import androidx.wear.protolayout.material3.textEdgeButton
import androidx.wear.protolayout.modifiers.clickable
import androidx.wear.protolayout.types.layoutString
import androidx.wear.tiles.Material3TileService
import androidx.wear.tiles.RequestBuilders.TileRequest
import androidx.wear.tiles.TileBuilders.Tile
import androidx.wear.tiles.TileService
import androidx.wear.tiles.tile
import androidx.wear.tiles.timeline
import androidx.wear.tiles.timelineEntry
import dev.fquo.liftwear.data.LiftWearContainer
import dev.fquo.liftwear.wear.LiftWearApplication
import dev.fquo.liftwear.wear.MainActivity
import kotlinx.coroutines.flow.first
import kotlin.time.Duration.Companion.minutes

/**
 * The Tile: what set you are on, without opening anything.
 *
 * **Reads Room and nothing else.** A Tile request is time-boxed and routinely arrives with
 * the app dead - the system asks while the carousel is being scrolled - so a network call
 * here would produce either a blank Tile or a slow one. Every field comes from the cache
 * the app and the outbox worker already maintain.
 *
 * Freshness is set to zero: nothing on this Tile changes on a timer, only when a set is
 * logged, and that path already calls [requestTileUpdate]. Asking the system to poll would
 * spend battery to redraw an identical layout.
 */
class LiftWearTileService : Material3TileService() {

    private val container: LiftWearContainer
        get() = (applicationContext as LiftWearApplication).container

    override suspend fun MaterialScope.tileResponse(requestParams: TileRequest): Tile {
        val snapshot = tileSnapshot(
            paired = container.pairing.value == LiftWearContainer.PairingState.Paired,
            // The shared flows, not a fresh query: the update that triggered this request came
            // from a collector on these same flows, so the decode it needs has already run.
            workout = container.workouts.workout.first(),
            preview = container.workouts.preview.first(),
            sync = container.workouts.sync.first(),
        )
        return tile(
            timeline = timeline(timelineEntry(layout(snapshot))),
            freshness = 0.minutes,
        )
    }

    private fun MaterialScope.layout(snapshot: TileSnapshot): LayoutElement {
        val open = clickable(
            action = ActionBuilders.LaunchAction.Builder()
                .setAndroidActivity(
                    ActionBuilders.AndroidActivity.Builder()
                        .setPackageName(packageName)
                        .setClassName(MainActivity::class.java.name)
                        .build()
                )
                .build()
        )

        return primaryLayout(
            titleSlot = { text(snapshot.title.layoutString, typography = Typography.LABEL_MEDIUM, maxLines = 2) },
            mainSlot = {
                column(
                    *listOfNotNull(
                        snapshot.headline?.let {
                            text(it.layoutString, typography = Typography.DISPLAY_MEDIUM, maxLines = 1)
                        },
                        snapshot.detail?.let {
                            text(it.layoutString, typography = Typography.BODY_SMALL, maxLines = 2)
                        },
                        // Only ever shown when it is true, and it is the one thing on this
                        // Tile the lifter might need to act on.
                        snapshot.unsynced.takeIf { it > 0 }?.let {
                            text("$it to sync".layoutString, typography = Typography.BODY_EXTRA_SMALL, maxLines = 1)
                        },
                    ).toTypedArray()
                )
            },
            bottomSlot = {
                textEdgeButton(onClick = open) {
                    text(tileButtonLabel(snapshot.state).layoutString)
                }
            },
            onClick = open,
        )
    }
}

/**
 * Redraws the Tile.
 *
 * Cheap and idempotent, so it is called on every cache change rather than being reasoned
 * about case by case. Wrapped because the system throws if no Tile of ours is installed,
 * which is the normal state for a user who has never added it.
 */
fun requestTileUpdate(context: Context) {
    runCatching {
        TileService.getUpdater(context).requestUpdate(LiftWearTileService::class.java)
    }
}

/** For the manifest's preview attribute and for tests to name the service. */
fun tileComponent(context: Context): ComponentName =
    ComponentName(context, LiftWearTileService::class.java)
