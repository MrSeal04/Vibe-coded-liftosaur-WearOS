package dev.fquo.liftwear.wear.ambient

import dev.fquo.liftwear.api.Weight
import dev.fquo.liftwear.api.dto.WorkoutDto
import dev.fquo.liftwear.data.workout.WorkoutPlan
import dev.fquo.liftwear.wear.rest.RestPhase
import dev.fquo.liftwear.wear.rest.RestState
import dev.fquo.liftwear.wear.ui.workout.repsLabel
import dev.fquo.liftwear.wear.ui.workout.setCounterLabel

/**
 * The three lines the ambient screen shows besides the clock. Nothing else fits, and
 * nothing else is worth the pixels: in ambient the lifter is glancing, not reading.
 *
 * All three empty means there is nothing to report and the screen falls back to the time
 * alone - a minimal stand-in watch face, which is the honest thing to show when the app is
 * being held on the ambient screen with no workout behind it.
 */
data class AmbientContent(
    val title: String? = null,
    val headline: String? = null,
    val detail: String? = null,
) {
    val isEmpty: Boolean get() = title == null && headline == null && detail == null
}

/**
 * What to show in ambient, given what the workout is doing.
 *
 * Rest is deliberately reported in whole minutes rather than as a countdown. The app is
 * only allowed to redraw about once a minute in ambient, so a "1:47" put on screen would
 * be wrong for fifty-nine of the next sixty seconds. Whole minutes, floored, stay true for
 * the entire span they are on screen: "2 min left" means at least two minutes remain, and
 * it still means that just before the next tick replaces it.
 *
 * The second-by-second countdown is not lost - it is on the Ongoing Activity chip, which
 * the system animates itself and which is not subject to this throttle.
 */
fun ambientContent(workout: WorkoutDto?, rest: RestState, now: Long): AmbientContent {
    if (rest.isActive) {
        val title = rest.label ?: "Rest"
        if (rest.phase(now) == RestPhase.Done) return AmbientContent(title, "GO", "rest over")
        val minutes = rest.remainingSeconds(now) / 60
        return AmbientContent(
            title = title,
            headline = if (minutes >= 1) minutes.toString() else "<1",
            detail = "min left",
        )
    }

    if (workout == null) return AmbientContent()

    val ref = WorkoutPlan.firstIncomplete(workout) ?: return AmbientContent(
        title = workout.dayName ?: workout.programName ?: "Workout",
        headline = "Done",
        detail = "all sets logged",
    )

    val weight = ref.set.weight?.let(Weight::parse)
    return if (weight != null) {
        AmbientContent(
            title = ref.exerciseName,
            headline = weight.toString(),
            detail = "${repsLabel(ref.set)}  ${setCounterLabel(ref)}",
        )
    } else {
        // Bodyweight work: the rep count is the only number there is, so it is the headline.
        AmbientContent(
            title = ref.exerciseName,
            headline = repsLabel(ref.set),
            detail = setCounterLabel(ref),
        )
    }
}
