package dev.fquo.liftwear.wear.preview

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import dev.fquo.liftwear.wear.rest.RestController
import dev.fquo.liftwear.wear.rest.RestState
import dev.fquo.liftwear.wear.rest.WorkoutSessionService
import dev.fquo.liftwear.wear.ui.workout.rememberRestNow
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

        // Debug-only: arms a real rest so the exact alarms, the haptics and the Ongoing
        // Activity chip can be exercised on a device without a live workout or an account.
        intent.getIntExtra("liveRestSeconds", 0).takeIf { it > 0 }?.let { seconds ->
            WorkoutSessionService.start(this)
            RestController.get(this).start(seconds, "Debug rest")
        }
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
        "rest", "rest-warning", "rest-done" -> {
            val now = System.currentTimeMillis()
            val rest = when (screen) {
                "rest-warning" -> RestState(endsAt = now + 6_000, durationSeconds = 180, label = "Squat · set 2 / 4")
                "rest-done" -> RestState(endsAt = now - 2_000, durationSeconds = 180, label = "Squat · set 2 / 4")
                else -> RestState(endsAt = now + 95_000, durationSeconds = 180, label = "Squat · set 2 / 4")
            }
            val tick by rememberRestNow(rest)
            ExerciseFocusPage(
                entry = SampleWorkout.squat,
                ref = WorkoutPlan.firstIncompleteIn(workout, 0),
                busy = false,
                sync = SyncState(),
                rest = rest,
                now = tick,
                progressFraction = 0.35f,
                allDone = false,
                onPrimary = {},
                onOpenSetList = {},
            )
        }
        "setup" -> SetupPrompt(malformed = false, onEnterKey = {})
        "setup-phone" -> WaitingForPhone(phone = PhoneStatus.Ready, onEnterHere = {})
        "setup-nocompanion" -> WaitingForPhone(phone = PhoneStatus.NoCompanion, onEnterHere = {})
        else -> MessageScreen("Unknown screen", screen)
    }
}
