package dev.fquo.liftwear.wear.tile

import dev.fquo.liftwear.api.Weight
import dev.fquo.liftwear.api.dto.WorkoutDto
import dev.fquo.liftwear.data.workout.SyncState
import dev.fquo.liftwear.data.workout.WorkoutPlan
import dev.fquo.liftwear.wear.ui.workout.repsLabel
import dev.fquo.liftwear.wear.ui.workout.setCounterLabel

/**
 * Everything the Tile draws, decided before any ProtoLayout builder is touched.
 *
 * Separated because a Tile is otherwise untestable on the JVM: its output is a protobuf
 * built inside a bound service against a device configuration. What is worth testing is
 * which of the four things it says and how the numbers are worded, and that is all here.
 */
data class TileSnapshot(
    val state: State,
    /** The line above the number: exercise, day, or the app's name. */
    val title: String,
    /** The number itself, or null when there is none to show. */
    val headline: String?,
    val detail: String?,
    /** Sets completed, 0f-1f; drives the arc. Null when there is no workout to measure. */
    val progress: Float?,
    val unsynced: Int,
) {
    enum class State {
        /** No API key. The Tile cannot show training data and must not pretend to. */
        Unpaired,

        /** A workout is running: the Tile is a status readout of the set you are on. */
        Live,

        /** Nothing running, but the server told us what today holds. */
        Ready,

        /** Nothing running and nothing cached - the Tile has never been able to look. */
        Idle,
    }
}

/**
 * Builds the snapshot from cached state only. No network, no clock, no Android.
 *
 * A Tile request is time-boxed and can arrive with the app dead, so everything here has to
 * come out of Room. That constraint is what makes the persisted preview worth its row: an
 * in-memory one would leave this permanently on [TileSnapshot.State.Idle].
 */
fun tileSnapshot(
    paired: Boolean,
    workout: WorkoutDto?,
    preview: WorkoutDto?,
    sync: SyncState = SyncState(),
): TileSnapshot {
    if (!paired) {
        return TileSnapshot(
            state = TileSnapshot.State.Unpaired,
            title = "LiftWear",
            headline = null,
            detail = "Set up on your phone",
            progress = null,
            unsynced = 0,
        )
    }

    if (workout != null) {
        val progress = WorkoutPlan.progress(workout)
        val ref = WorkoutPlan.firstIncomplete(workout)
        if (ref == null) {
            // Every set logged but the workout not finished - the Tile should say so, since
            // finishing is the one thing still owed and it is a tap away.
            return TileSnapshot(
                state = TileSnapshot.State.Live,
                title = workout.dayName ?: "Workout",
                headline = "Done",
                detail = "${progress.completed} sets · ready to finish",
                progress = 1f,
                unsynced = sync.pending,
            )
        }
        val weight = ref.set.weight?.let(Weight::parse)
        return TileSnapshot(
            state = TileSnapshot.State.Live,
            title = ref.exerciseName,
            headline = weight?.toString() ?: repsLabel(ref.set),
            detail = if (weight != null) {
                "${repsLabel(ref.set)}  ${setCounterLabel(ref)}"
            } else {
                setCounterLabel(ref)
            },
            progress = progress.fraction,
            unsynced = sync.pending,
        )
    }

    if (preview != null) {
        val total = WorkoutPlan.progress(preview).total
        return TileSnapshot(
            state = TileSnapshot.State.Ready,
            title = preview.dayName ?: preview.programName ?: "Next workout",
            headline = preview.entries.size.toString(),
            detail = if (preview.entries.size == 1) "exercise · $total sets" else "exercises · $total sets",
            progress = null,
            unsynced = sync.pending,
        )
    }

    return TileSnapshot(
        state = TileSnapshot.State.Idle,
        title = "LiftWear",
        headline = null,
        detail = "Open to load today's workout",
        progress = null,
        unsynced = sync.pending,
    )
}

/**
 * The Tile's button always opens the app and never writes.
 *
 * "Start" was the obvious label and it is the wrong one: starting a workout is a write to a
 * real training log, this app confirms every write, and a Tile carousel is somewhere a
 * sleeve brushes past. The Tile says what is next; the decision stays one deliberate tap
 * away on Home.
 */
fun tileButtonLabel(state: TileSnapshot.State): String = when (state) {
    TileSnapshot.State.Live -> "Open"
    TileSnapshot.State.Unpaired -> "Set up"
    else -> "Open"
}
