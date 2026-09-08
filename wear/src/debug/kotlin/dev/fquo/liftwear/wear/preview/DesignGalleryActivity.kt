package dev.fquo.liftwear.wear.preview

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import dev.fquo.liftwear.data.workout.SyncState
import dev.fquo.liftwear.data.workout.WorkoutPlan
import dev.fquo.liftwear.wear.ui.common.MessageScreen
import dev.fquo.liftwear.wear.ui.home.HomeContent
import dev.fquo.liftwear.wear.ui.setup.PhoneStatus
import dev.fquo.liftwear.wear.ui.setup.SetupPrompt
import dev.fquo.liftwear.wear.ui.setup.WaitingForPhone
import dev.fquo.liftwear.wear.ui.workout.ExerciseFocusPage
import dev.fquo.liftwear.wear.ui.workout.SetConfirmContent
import dev.fquo.liftwear.wear.ui.workout.SetConfirmState
import dev.fquo.liftwear.wear.ui.workout.setCounterLabel

/**
 * Screenshot harness for the round-display pass.
 *
 * `adb shell am start -n dev.fquo.liftwear/dev.fquo.liftwear.wear.preview.DesignGalleryActivity
 *      --es screen focus`
 *
 * Android Studio's preview pane covers the same ground interactively; this exists so the
 * layouts can also be captured headlessly with `adb exec-out screencap`.
 */
class DesignGalleryActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val screen = intent.getStringExtra("screen") ?: "focus"
        setContent {
            MaterialTheme {
                AppScaffold { Gallery(screen) }
            }
        }
    }
}

@Composable
private fun Gallery(screen: String) {
    val workout = SampleWorkout.workout
    when (screen) {
        "focus" -> ExerciseFocusPage(
            entry = SampleWorkout.squat,
            ref = WorkoutPlan.firstIncompleteIn(workout, 0),
            busy = false,
            sync = SyncState(),
            progressFraction = WorkoutPlan.progress(workout).fraction,
            allDone = false,
            onPrimary = {},
            onOpenSetList = {},
        )
        "focus-long" -> ExerciseFocusPage(
            entry = SampleWorkout.press,
            ref = WorkoutPlan.firstIncompleteIn(workout, 1),
            busy = false,
            sync = SyncState(),
            progressFraction = 0.45f,
            allDone = false,
            onPrimary = {},
            onOpenSetList = {},
        )
        "focus-bodyweight" -> ExerciseFocusPage(
            entry = SampleWorkout.bodyweight,
            ref = WorkoutPlan.firstIncompleteIn(workout, 2),
            busy = false,
            sync = SyncState(),
            progressFraction = 0.9f,
            allDone = false,
            onPrimary = {},
            onOpenSetList = {},
        )
        "confirm" -> {
            val ref = WorkoutPlan.firstIncompleteIn(workout, 0)!!
            SetConfirmContent(
                exerciseName = ref.exerciseName,
                counter = setCounterLabel(ref),
                initial = SetConfirmState.forSet(ref.set.copy(logRpe = true)),
                onLog = {},
                resetKey = ref.setId,
            )
        }
        "home" -> HomeContent(
            active = workout,
            preview = null,
            onPrimary = {},
            onOpenHistory = {},
            onOpenSettings = {},
        )
        "home-idle" -> HomeContent(
            active = null,
            preview = workout,
            onPrimary = {},
            onOpenHistory = {},
            onOpenSettings = {},
        )
        "setup" -> SetupPrompt(malformed = false, onEnterKey = {})
        "setup-phone" -> WaitingForPhone(phone = PhoneStatus.Ready, onEnterHere = {})
        "setup-nocompanion" -> WaitingForPhone(phone = PhoneStatus.NoCompanion, onEnterHere = {})
        else -> MessageScreen("Unknown screen", screen)
    }
}
