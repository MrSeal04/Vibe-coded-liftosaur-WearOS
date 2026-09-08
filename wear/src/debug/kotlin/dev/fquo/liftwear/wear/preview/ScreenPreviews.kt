package dev.fquo.liftwear.wear.preview

import androidx.compose.runtime.Composable
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import androidx.wear.compose.ui.tooling.preview.WearPreviewFontScales
import dev.fquo.liftwear.data.workout.WorkoutPlan
import dev.fquo.liftwear.wear.ui.home.HomeContent
import dev.fquo.liftwear.wear.ui.setup.PhoneStatus
import dev.fquo.liftwear.wear.ui.setup.SetupPrompt
import dev.fquo.liftwear.wear.ui.setup.WaitingForPhone
import dev.fquo.liftwear.wear.ui.workout.ExerciseFocusPage
import dev.fquo.liftwear.wear.ui.workout.SetConfirmContent
import dev.fquo.liftwear.wear.ui.workout.SetConfirmState
import dev.fquo.liftwear.wear.ui.workout.setCounterLabel

/**
 * Round-display previews.
 *
 * [WearPreviewDevices] covers both screen sizes and [WearPreviewFontScales] the accessibility
 * range; the combination that actually breaks layouts is the largest font on the smallest
 * watch, which is why both annotations are on every screen rather than just the devices one.
 */
@Composable
private fun Wrap(content: @Composable () -> Unit) {
    MaterialTheme { AppScaffold { content() } }
}

@WearPreviewDevices
@WearPreviewFontScales
@Composable
fun WorkoutFocusPreview() = Wrap {
    val workout = SampleWorkout.workout
    ExerciseFocusPage(
        entry = SampleWorkout.squat,
        ref = WorkoutPlan.firstIncompleteIn(workout, 0),
        busy = false,
        progressFraction = WorkoutPlan.progress(workout).fraction,
        allDone = false,
        onPrimary = {},
        onOpenSetList = {},
    )
}

/** The long-name, no-plates case: a machine exercise on a 192dp screen. */
@WearPreviewDevices
@WearPreviewFontScales
@Composable
fun WorkoutFocusLongNamePreview() = Wrap {
    val workout = SampleWorkout.workout
    ExerciseFocusPage(
        entry = SampleWorkout.press,
        ref = WorkoutPlan.firstIncompleteIn(workout, 1),
        busy = false,
        progressFraction = 0.4f,
        allDone = false,
        onPrimary = {},
        onOpenSetList = {},
    )
}

/** Bodyweight AMRAP: no weight, so the rep count becomes the headline number. */
@WearPreviewDevices
@WearPreviewFontScales
@Composable
fun WorkoutFocusBodyweightPreview() = Wrap {
    val workout = SampleWorkout.workout
    ExerciseFocusPage(
        entry = SampleWorkout.bodyweight,
        ref = WorkoutPlan.firstIncompleteIn(workout, 2),
        busy = false,
        progressFraction = 0.9f,
        allDone = false,
        onPrimary = {},
        onOpenSetList = {},
    )
}

/** Three pickers is the widest this screen ever gets. */
@WearPreviewDevices
@WearPreviewFontScales
@Composable
fun SetConfirmPreview() = Wrap {
    val ref = WorkoutPlan.firstIncompleteIn(SampleWorkout.workout, 0)!!
    SetConfirmContent(
        exerciseName = ref.exerciseName,
        counter = setCounterLabel(ref),
        initial = SetConfirmState.forSet(ref.set.copy(logRpe = true)),
        onLog = {},
        resetKey = ref.setId,
    )
}

@WearPreviewDevices
@WearPreviewFontScales
@Composable
fun HomeActivePreview() = Wrap {
    HomeContent(
        active = SampleWorkout.workout,
        preview = null,
        onPrimary = {},
        onOpenHistory = {},
        onOpenSettings = {},
    )
}

@WearPreviewDevices
@WearPreviewFontScales
@Composable
fun HomeIdlePreview() = Wrap {
    HomeContent(
        active = null,
        preview = SampleWorkout.workout,
        onPrimary = {},
        onOpenHistory = {},
        onOpenSettings = {},
    )
}

@WearPreviewDevices
@WearPreviewFontScales
@Composable
fun SetupPreview() = Wrap { SetupPrompt(malformed = false, onEnterKey = {}) }

@WearPreviewDevices
@WearPreviewFontScales
@Composable
fun WaitingForPhonePreview() = Wrap {
    WaitingForPhone(phone = PhoneStatus.Ready, onEnterHere = {})
}

/** A phone is there but the companion is not - different advice to "no phone". */
@WearPreviewDevices
@WearPreviewFontScales
@Composable
fun NoCompanionPreview() = Wrap {
    WaitingForPhone(phone = PhoneStatus.NoCompanion, onEnterHere = {})
}
