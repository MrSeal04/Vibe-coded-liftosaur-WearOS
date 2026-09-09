---
name: wear-emulator-verify
description: Verify LiftWear on the Wear OS emulators - AVD boot rules, installing before am start, the DesignGalleryActivity screenshot harness, font-scale sweeps and contact sheets, and driving ambient, Tiles and complications from adb. Use when a change needs to be seen on a watch, when capturing round-display screenshots, when running connectedAndroidTest, on "Activity class {...} does not exist", on a screencap that returns the watch face or a splash screen, on "Unrecognized operation" or "Complication slot is not enabled" from the debug surface, when a Tile or complication goes blank after a test run, when both emulators die at once, or when checking whether the app owns the ambient screen.
---

# Wear OS emulator verification

The loop behind every "verified on device" claim in the plan file. `headless-screenshot` is
browser-only and does not apply here.

## Boot exactly one AVD

```sh
source ~/.androidenv     # JAVA_HOME + ANDROID_HOME; without it Gradle dies immediately
export PATH="$PATH:$ANDROID_HOME/emulator:$ANDROID_HOME/platform-tools"
./gradlew --stop         # frees ~2GB before booting
nohup emulator -avd LiftWear_Large -no-snapshot-save -no-audio \
      -gpu swiftshader_indirect > /tmp/emu.log 2>&1 &
adb wait-for-device shell 'while [ "$(getprop sys.boot_completed)" != "1" ]; do sleep 3; done'
```

`LiftWear_GalaxySmall` (396x396, API 33, Wear OS 4) and `LiftWear_Large` (454x454, API 36,
Wear OS 6), both with `hw.lcd.circular=true` — the stock profiles ship `circular=false`, which
makes `Configuration.isScreenRound` lie.

**Never run two at once** — 31GB is not enough with Gradle up and both get killed. Cover the
other size with `adb emu kill`, `./gradlew --stop`, then boot the other.

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
- `--es seed live|ready|clear` writes sample data into the Room cache and pairs with a
  placeholder key, so the Tile and the complication can be seen in every state. It refuses to
  displace a real key. `clear` first when switching between `live` and `ready` - `live` wins.

## Reviewing many screens at once

Screens render one at a time but are far quicker to judge as a grid. `scripts/` has no helper;
build one with PIL (installed) that tiles PNGs 4-across with filename captions, and read the
sheet instead of the individual shots. The layout-breaking case is **396x396 at font scale
1.24** - sweep that first, fix, then confirm on 454x454.

```sh
adb shell settings put system font_scale 1.24     # 1.0 to reset
```

## Ambient / always-on

```sh
adb shell input keyevent 223 / 224                   # sleep -> ambient / wake
adb shell dumpsys display | grep mScreenState        # ON | DOZE | DOZE_SUSPEND
adb logcat -d | grep -E "AmbientTaskStateMachine|AmbiactiveComponentManager"
```

One log line says who owns the screen: `-> TaskAmbiactive ... not eligible for ambient lite`
means **the app owns it** and had better be drawing an ambient surface; `-> TaskAmbientLite`
means the system covers it with its own clock (what Settings gets — use it as the baseline).

Measured 2026-09: Wear OS 6 gives LiftWear `TaskAmbiactive` even with nothing ambient-related
composed, where Wear OS 4 gives `TaskAmbientLite` — test both images. The system ends an
ambiactive session after **~60s**, returning to the watch face and `DOZE_SUSPEND`.

Force-stop and reboot before testing a negative: a killed process leaves registrations behind,
and a guard only ever observed saying one thing has not been tested.

## Tiles and complications

Both are added through an undocumented debug broadcast. Probing an unknown operation prints
`Unrecognized operation <x>`, which is how the rest of this was found.

```sh
# Tile: lands at an index in the carousel; swipe left from the watch face to reach it.
adb shell am broadcast -a com.google.android.wearable.app.DEBUG_SURFACE \
    --es operation add-tile --ecn component <pkg>/<TileService>

# Complication: the face must be applied first, and `type` is the wire int (3 = SHORT_TEXT).
adb shell am broadcast -a com.google.android.wearable.app.DEBUG_SURFACE \
    --es operation set-watchface --es watchFaceId "<pkg>/<WatchFaceService>"
adb shell am broadcast -a com.google.android.wearable.app.DEBUG_SURFACE \
    --es operation set-complication --ecn component <pkg>/<ComplicationService> \
    --es watchFaceId "<pkg>/<WatchFaceService>" --ei slot 0 --ei type 3
```

- `watchFaceId` must be the **fully qualified service**, not the package - a bare package gives
  `Watch face package is not installed`.
- `Complication slot is not enabled` means that face is not the active one, or has no slots.
  Most stock faces report every slot disabled; loop over
  `adb shell dumpsys package -f | grep -o "<rwf-pkg>/[a-zA-Z0-9.]*WatchFaceService"` and take
  the first that accepts slot 0.
- `show-tile` does not exist; swipe to the tile instead.
- **`connectedAndroidTest` uninstalls the app**, which clears the tile from the carousel and
  the complication from its slot. A blank surface after a test run is the harness, not a bug -
  re-add and reinstall before screenshotting.

## Instrumented tests

```sh
./gradlew :wear:connectedDebugAndroidTest :core:data:connectedDebugAndroidTest
```

`--tests` is not accepted; filter with
`-Pandroid.testInstrumentationRunnerArguments.package=dev.fquo.liftwear.wear.ambient`.
Results (counts, failures) are in `*/build/outputs/androidTest-results/connected/debug/*.xml`.
