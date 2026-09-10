# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

**LiftWear** — an unofficial, standalone Wear OS client for [Liftosaur](https://www.liftosaur.com),
built against its v1 REST API. Deliberately uses none of Liftosaur's name, logo or namespace.
Package `dev.fquo.liftwear`; `:wear` is minSdk 33 (Wear OS 4) and `:mobile` minSdk 30, both
targetSdk 36 / compileSdk 37.

The app replaces pulling a phone out between sets: start today's workout, see the current set
as a large glanceable number, confirm it through a picker, get buzzed when rest is up. It must
keep working in a gym basement with no signal.

## Commands

Nothing runs without the toolchain env — Gradle dies with `ERROR: JAVA_HOME is not set`:

```sh
source ~/.androidenv                                   # JAVA_HOME + ANDROID_HOME
export PATH="$PATH:$ANDROID_HOME/emulator:$ANDROID_HOME/platform-tools"
```

```sh
./gradlew :wear:assembleDebug :mobile:assembleDebug    # both APKs
./gradlew test                                         # all JVM unit tests
./gradlew :wear:testDebugUnitTest                      # one module
./gradlew :core:api:testDebugUnitTest --tests '*WeightTest'   # one class (JVM only)
./gradlew :wear:connectedDebugAndroidTest :core:data:connectedDebugAndroidTest
```

`connectedAndroidTest` does **not** accept `--tests`. Filter with
`-Pandroid.testInstrumentationRunnerArguments.package=dev.fquo.liftwear.wear.ambient`.
Counts and failures land in `*/build/outputs/androidTest-results/connected/debug/*.xml`.

### Tests that touch the live account

`LiveApiSmokeTest` is strictly read-only (GET only) and skipped unless **both** hold: env
`LIFTWEAR_LIVE=1`, and a key at `~/.config/liftwear/api_key`.

```sh
LIFTWEAR_LIVE=1 ./gradlew :core:api:testDebugUnitTest --tests '*LiveApiSmokeTest'
```

### Emulators and screenshots

Use the **`wear-emulator-verify`** skill (`.claude/skills/`). It carries the AVD boot rules
(one at a time — two exhaust RAM and kill both), the `DesignGalleryActivity` screenshot
harness, font-scale sweeps, and the undocumented adb broadcasts that add a Tile or a
complication. Read it before trying to see anything on a watch.

### Scripts (`scripts/`, bash — `zsh` breaks `source lib.sh`)

- `backup-account.sh` — read-only full export of the live account into `backups/`.
- `capture-schema.sh` — read-only; captures API responses into `fixtures/`.
- `testprog.sh` — creates/deletes the disposable test program. Refuses to delete any id not
  recorded in the gitignored `.liftwear-testids`.

## Architecture

```
:wear      Wear OS app      applicationId dev.fquo.liftwear
:mobile    phone companion  applicationId dev.fquo.liftwear   (same, and it must be)
:core:api        Retrofit client, DTOs, error mapping, Weight, Liftoscript history parser
:core:data       Room (workout cache + outbox + history), DataStore, repositories
:core:datalayer  shared Data Layer paths and the credential hand-off rules
```

`:wear` and `:mobile` **must** share `applicationId`, `versionCode` and signing certificate.
That is a hard Wearable Data Layer requirement, not a convention.

### The offline model — read this before touching `:core:data`

The single most load-bearing design decision: **the UI only ever reads Room and never awaits
the network.** Every tap returns as soon as the write is durably queued.

- **There is no START in the outbox.** `GET /workout/next` reissues its `setId`s on every
  call, so a queued start produces ids nothing can be logged against. Starting requires a
  connection; everything after it does not. Proven live — an invented setId returns
  `set_not_found`. See `docs/api-findings.md`.
- **The workout is cached as raw JSON, not normalised.** Every write response returns the
  whole workout and update scripts rewrite later sets' weights, so the server's payload is
  authoritative in its entirety.
- `workout_cache` holds **two rows**: `SINGLE_ROW` (the live workout) and `PREVIEW_ROW`
  (`/workout/next`, display-only). The preview is persisted because the Tile and complication
  are asked for content from a process that may have started for that question alone.
- **`refreshCurrent()` refuses while writes are queued** and schedules a drain instead. A
  reconcile that won would erase unsent sets — the one way this design could silently lose data.
- **Only the leading run of the queue is batched.** Never scan for compatible rows further
  down: sending a set past a finish makes a progression compute from the wrong state.
- **Parked rows leave the pending queue but are never deleted.** `retryParked()` and
  `abandonQueued()` are separate, separately confirmed actions; the latter is the only path in
  the app that destroys logged training data.
- `LiftWearDatabase.create` has **no destructive migration**, deliberately: the outbox can hold
  the only copy of sets logged offline. A schema change needs a real migration.

### Credentials

DataStore + Tink AEAD with the key in the Android Keystore (`AndroidKeystore.getAead()` —
`AndroidKeysetManager` is deprecated, `EncryptedSharedPreferences` is dead). Only ciphertext is
persisted. The phone delivers the key over a Data Layer `DataItem`; the watch **stores before
it acks**, and the phone deletes the item on ack. A stale or redelivered credential is still
acked, or the token replicates in the Data Layer forever.

### Hand the system an instant, not a countdown

Three surfaces need a ticking timer and none of them may draw one:

| Surface | Mechanism |
|---|---|
| Ongoing Activity chip | `Status.TimerPart(timeZeroMillis)`, posted via `OngoingActivity.update()` |
| Ambient screen | whole floored minutes only — the app may redraw ~once/minute |
| Complication | `TimeDifferenceComplicationText` + `TimeDifferenceStyle.STOPWATCH` |

`RestState` stores an absolute `endsAt`, mirrored to SharedPreferences, so it survives the
screen going off, process death and the app reopening. The buzz comes from
`AlarmManager.setExactAndAllowWhileIdle`, never a coroutine `delay`.

**Measured in forced deep Doze on a real Galaxy Watch 4 (2026-09-10): +15 ms and +3 ms.** The
documented ~9-minute allow-while-idle quota does not apply there — two alarms ten seconds
apart both kept their times — so `setAlarmClock` is not needed. To re-test, seal the watch
with `dumpsys deviceidle force-idle` (not `step`, which lets a maintenance window through) and
make no adb contact until past the deadline; touching adb wakes it and voids the run.

`OngoingActivity.apply()` extends the builder it is *handed* and returns nothing — building the
notification from a second builder silently drops the extras, with nothing in logcat and
nothing in `dumpsys`. Always post through `update()`.

### Ambient mode

The app does **not** choose whether it owns the ambient screen: Wear OS 6 holds it there
regardless (`AmbientTaskStateMachine: -> TaskAmbiactive`), where Wear OS 4 gives
`TaskAmbientLite`. So `AmbientAware` composes the manager unconditionally and the surface
degrades instead. It draws the ambient layer *over* the interactive tree rather than swapping
it — swapping disposes the nav host and takes the workout ViewModel with it.

### Round-display rules

- Two type scales, split at `screenWidthDp < 225` (`ScreenClass.Small` / `Large`). 396px and
  384px watches are Small; 454px and 480px are Large.
- **A round screen is narrower than the square it is drawn in.** `RoundGeometry.insetFraction`
  gives the real half-chord; `circularPadding()` (a flat percentage) is only correct near the
  vertical centre. Content near the top needs `arcSafePadding()`.
- `ScreenScaffold` cannot lay out an `edgeButton` without a `ScrollInfoProvider`, so
  non-scrolling screens place it by hand and reserve `edgeButtonInset` themselves.
- Variable-length content auto-sizes (`AutoSizeNumeral` / `AutoSizeLabel`). Numeral typography
  tokens carry a fixed `lineHeight` that auto-size does *not* scale — reset it to
  `TextUnit.Unspecified` or the shrunk glyph is clipped by its own line box.
- Lists are `TransformingLazyColumn`, never plain `LazyColumn`.

### History

`GET /history` returns Liftoscript **text**, not JSON. `WorkoutTextParser` is deliberately
tolerant and parses on *read*, so a better parser improves what is already cached. A line only
counts as an exercise if it yielded at least one set group; anything else is preserved verbatim
under "Not understood". An exercise with a target but nothing performed reads `not logged` —
never its target, which would look like work that was done.

### Tile and complication

Both read Room and nothing else — their requests arrive from a system process, time-boxed,
usually with the app dead. Neither may write: the Tile's button says "Open", never "Start",
because a carousel is somewhere a sleeve brushes past. `LiftWearApplication` collects the Room
flows and pushes updates to both, so a set logged on the wrist and a batch landed by the
outbox worker (same process) are handled by one collector.

## Live-account safety

There is no sandbox. The only API key belongs to a real account with real training history.

- Back up (`backup-account.sh`) before any write.
- All write testing goes through the **disposable program** from `testprog.sh`.
  `POST /workout/start` takes an explicit `programId` that overrides the current one, so the
  real program is never touched — there is no API to set a current program.
- `DELETE /history/:id` and `DELETE /programs/:id` hit production and are irreversible. Never
  script them over a range; delete by explicit id, and only ids in `.liftwear-testids`.

**The GitHub repo is public.** `fixtures/`, `backups/` and `.liftwear-testids` are gitignored
because they hold real training data. Committed fixtures and test samples use invented weights,
exercises and program names — keep it that way when adding samples.

## Reference

- `docs/api-findings.md` — everything the published API docs got wrong or left out, verified
  against the live API. Read it before assuming anything about a payload.
- Per-phase build history, including the bugs each phase found and why, is in the git log.
