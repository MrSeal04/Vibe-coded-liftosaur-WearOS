---
name: wear-emulator-verify
description: Verify LiftWear on the Wear OS emulators - AVD boot rules, installing before am start, the DesignGalleryActivity screenshot harness, both round screen sizes, and driving ambient/always-on from adb. Use when a change needs to be seen on a watch, when capturing round-display screenshots, when running connectedAndroidTest, on "Activity class {...} does not exist", on a screencap that returns the watch face or a splash screen, when both emulators die at once, or when checking whether the app owns the ambient screen.
---

# Wear OS emulator verification

The loop behind every "verified on device" claim in the plan file. `headless-screenshot` is
browser-only and does not apply here.

## Setup, every shell

```sh
source ~/.androidenv                                  # JAVA_HOME + ANDROID_HOME
export PATH="$PATH:$ANDROID_HOME/emulator:$ANDROID_HOME/platform-tools"
```

Without it Gradle dies with `ERROR: JAVA_HOME is not set`.

## Boot exactly one AVD

`LiftWear_GalaxySmall` (396x396, API 33, Wear OS 4) and `LiftWear_Large` (454x454, API 36,
Wear OS 6). Both have `hw.lcd.circular=true`; the stock profiles ship `circular=false`, which
makes `Configuration.isScreenRound` lie.

```sh
./gradlew --stop                                      # frees ~2GB before booting
nohup emulator -avd LiftWear_Large -no-snapshot-save -no-audio \
      -gpu swiftshader_indirect > /tmp/emu.log 2>&1 &
adb wait-for-device shell 'while [ "$(getprop sys.boot_completed)" != "1" ]; do sleep 3; done'
```

**Never run two emulators at once** — 31GB is not enough with Gradle up, and both get killed.
Cover the second screen size by `adb emu kill`, `./gradlew --stop`, then booting the other.

## Screenshots

```sh
./gradlew :wear:assembleDebug
adb install -r wear/build/outputs/apk/debug/wear-debug.apk
adb shell am start -S -n dev.fquo.liftwear/dev.fquo.liftwear.wear.preview.DesignGalleryActivity \
    --es screen ambient-rest
sleep 5 && adb exec-out screencap -p > shot.png
```

- **Install first.** `connectedAndroidTest` uninstalls the app when it finishes, so a later
  `am start` fails with `Activity class {...} does not exist` and the screencap silently
  returns the watch face.
- **Wait ~5s, not 2.** Two seconds captures the dumbbell splash.
- A screencap showing the watch face means the app never took focus — check
  `adb shell dumpsys window | grep mCurrentFocus`.
- Screens live in `DesignGalleryActivity`: `focus`, `focus-long`, `focus-bodyweight`,
  `confirm`, `home`, `home-idle`, `rest*`, `ambient*`, `setup*`. Sample data only, so this
  writes nothing to the live Liftosaur account.
- `--ei liveRestSeconds 45` arms a real rest, exercising the exact alarms, haptics and the
  Ongoing Activity chip without an account.

## Ambient / always-on

```sh
adb shell input keyevent 223      # sleep -> ambient
adb shell input keyevent 224      # wake  -> interactive
adb shell dumpsys display | grep mScreenState        # ON | DOZE | DOZE_SUSPEND
adb logcat -d | grep -E "AmbientTaskStateMachine|AmbiactiveComponentManager"
```

Who owns the ambient screen is in one log line:

- `-> TaskAmbiactive ... not eligible for ambient lite` — **the app owns it** and had better
  be drawing an ambient surface.
- `-> TaskAmbientLite ... is eligible for ambient lite` — the system covers the app with its
  own clock. This is what an ordinary app (e.g. Settings) gets; use one as the baseline.

Measured 2026-09: Wear OS 6 gives LiftWear `TaskAmbiactive` even with nothing ambient-related
composed, where Wear OS 4 gives `TaskAmbientLite` — so test both images. The system ends an
ambiactive session after **~60s**, returning to the watch face and `DOZE_SUSPEND`.

Force-stop and reboot before testing a negative: a killed process can leave registrations
behind, and a guard only ever observed saying one thing has not been tested.

## Instrumented tests

```sh
./gradlew :wear:connectedDebugAndroidTest :core:data:connectedDebugAndroidTest
```

`--tests` is not accepted; filter with
`-Pandroid.testInstrumentationRunnerArguments.package=dev.fquo.liftwear.wear.ambient`.
Results (counts, failures) are in `*/build/outputs/androidTest-results/connected/debug/*.xml`.
