package dev.fquo.liftwear.wear.preview

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.wear.compose.foundation.AmbientMode
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import dev.fquo.liftwear.wear.ambient.AmbientAware
import dev.fquo.liftwear.wear.ambient.AmbientSurface
import dev.fquo.liftwear.wear.ambient.AmbientWorkoutSurface
import dev.fquo.liftwear.wear.ambient.ambientContent
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
import dev.fquo.liftwear.data.LiftWearContainer
import dev.fquo.liftwear.data.db.WorkoutCacheEntity
import kotlinx.coroutines.flow.first
import dev.fquo.liftwear.wear.LiftWearApplication
import dev.fquo.liftwear.wear.tile.requestTileUpdate
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import kotlinx.serialization.json.Json
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

    private fun seedCache(mode: String) {
        val container = (application as LiftWearApplication).container
        val dao = container.database.workoutCacheDao()
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
        val encoded = json.encodeToString(
            dev.fquo.liftwear.api.dto.WorkoutDto.serializer(),
            SampleWorkout.workout,
        )
        lifecycleScope.launch {
            // The Tile shows nothing but "set up on your phone" while unpaired, which is the
            // correct behaviour and also makes every other state invisible. A placeholder is
            // written only when there is genuinely no key, so this can never displace a real
            // one on a watch that is actually set up.
            if (mode != "clear" && container.pairing.value != LiftWearContainer.PairingState.Paired) {
                container.setApiKey(DEBUG_PLACEHOLDER_KEY)
            }
            when (mode) {
                "live" -> dao.put(
                    WorkoutCacheEntity(
                        id = WorkoutCacheEntity.SINGLE_ROW,
                        workoutJson = encoded,
                        startTime = SampleWorkout.workout.startTime,
                        fetchedAt = System.currentTimeMillis(),
                    )
                )
                "ready" -> dao.put(
                    WorkoutCacheEntity(
                        id = WorkoutCacheEntity.PREVIEW_ROW,
                        workoutJson = encoded,
                        startTime = null,
                        fetchedAt = System.currentTimeMillis(),
                    )
                )
                else -> {
                    dao.clearAll()
                    if (container.credentials.apiKey.first() == DEBUG_PLACEHOLDER_KEY) container.unpair()
                }
            }
            requestTileUpdate(this@DesignGalleryActivity)
        }
    }

    private companion object {
        /** Deliberately shaped like a key and deliberately not one; every call it makes 401s. */
        const val DEBUG_PLACEHOLDER_KEY = "lftsk_debugplaceholdernotarealkey"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val screen = intent.getStringExtra("screen") ?: "focus"

        // Debug-only: arms a real rest so the exact alarms, the haptics and the Ongoing
        // Activity chip can be exercised on a device without a live workout or an account.
        intent.getIntExtra("liveRestSeconds", 0).takeIf { it > 0 }?.let { seconds ->
            WorkoutSessionService.start(this)
            RestController.get(this).start(seconds, "Debug rest")
        }

        // Debug-only: writes sample data straight into the Room cache so the Tile and the
        // complication - which read Room and nothing else - can be seen in every state on a
        // watch with no API key. Nothing here can reach the network: without a key the
        // interceptor fails every request before it is sent.
        intent.getStringExtra("seed")?.let { seedCache(it) }
        setContent {
            MaterialTheme {
                // The ambient screens replace the whole surface, TimeText included, so
                // wrapping them in AppScaffold would put a second clock in the screenshot
                // that the real app never shows.
                if (screen.startsWith("ambient")) Gallery(screen) else AppScaffold { Gallery(screen) }
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
        // The four ambient cases, with the panel's capabilities forced rather than asked
        // for: an emulator reports neither burn-in nor low-bit, so the two renderings that
        // actually need checking would otherwise never appear on screen here.
        "ambient", "ambient-rest", "ambient-done", "ambient-lowbit", "ambient-burnin" -> {
            val now = System.currentTimeMillis()
            val rest = when (screen) {
                "ambient-rest" -> RestState(endsAt = now + 145_000, durationSeconds = 180, label = "Squat · set 2 / 4")
                "ambient-done" -> RestState(endsAt = now - 2_000, durationSeconds = 180, label = "Squat · set 2 / 4")
                else -> RestState()
            }
            AmbientSurface(
                mode = AmbientMode.Ambient(
                    isBurnInProtectionRequired = screen == "ambient-burnin",
                    isLowBitAmbientSupported = screen == "ambient-lowbit",
                ),
                content = ambientContent(workout, rest, now),
                now = now,
                // Any non-zero tick shows the burn-in walk displaced rather than at rest.
                tick = 3,
            )
        }
        // The real wiring rather than a forced rendering: this turns always-on on for the
        // activity and waits for the system to dim it. Drive it with
        //   adb shell input keyevent 223   (sleep -> ambient)
        //   adb shell input keyevent 224   (wake  -> interactive)
        // No workout behind it: the surface falls back to the time alone. Reachable in
        // real use because Wear OS 6 holds this app on the ambient screen whether or not it
        // asked to be there.
        "ambient-idle" -> {
            val now = System.currentTimeMillis()
            AmbientSurface(
                mode = AmbientMode.Ambient(isBurnInProtectionRequired = false, isLowBitAmbientSupported = false),
                content = ambientContent(workout = null, rest = RestState(), now = now),
                now = now,
                tick = 0,
            )
        }
        "ambient-live" -> {
            val rest = RestState(
                endsAt = System.currentTimeMillis() + 145_000,
                durationSeconds = 180,
                label = "Squat · set 2 / 4",
            )
            AmbientAware(
                ambient = { mode -> AmbientWorkoutSurface(mode, workout, rest) },
            ) {
                AppScaffold {
                    ExerciseFocusPage(
                        entry = SampleWorkout.squat,
                        ref = WorkoutPlan.firstIncompleteIn(workout, 0),
                        busy = false,
                        sync = SyncState(),
                        progressFraction = 0.35f,
                        allDone = false,
                        onPrimary = {},
                        onOpenSetList = {},
                    )
                }
            }
        }
        "setup" -> SetupPrompt(malformed = false, onEnterKey = {})
        "setup-phone" -> WaitingForPhone(phone = PhoneStatus.Ready, onEnterHere = {})
        "setup-nocompanion" -> WaitingForPhone(phone = PhoneStatus.NoCompanion, onEnterHere = {})
        else -> MessageScreen("Unknown screen", screen)
    }
}
