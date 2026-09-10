---
name: wear-emulator-verify
description: Verify LiftWear on the Wear OS emulators - AVD boot rules, installing before am start, the DesignGalleryActivity screenshot harness, font-scale sweeps and contact sheets, and driving ambient, Tiles and complications from adb. Use when a change needs to be seen on a watch, when capturing round-display screenshots, when running connectedAndroidTest, on "Activity class {...} does not exist", on a screencap that returns the watch face or a splash screen, on "Unrecognized operation" or "Complication slot is not enabled" from the debug surface, when a Tile or complication goes blank after a test run, when both emulators die at once, when checking whether the app owns the ambient screen, or when testing on a real watch over Wi-Fi - a serial containing "(2)" that ANDROID_SERIAL reports as "device not found", a link that drops between commands or mid-install, "connection refused" on an address mDNS still lists, a screencap returning the charging AOD or a locked watch face, or forcing Doze to measure whether setExactAndAllowWhileIdle alarms get deferred, or measuring jank and cold start on a real watch - dexopt "status=verify" after a sideload, "Failure while dumping the app" from gfxinfo, or meminfo counting several Activities from stacked launches, or pulling the app's debug log (content read on the debuglog provider), proving a crash lands in it, or "INSTALL_FAILED_UPDATE_INCOMPATIBLE" from connectedAndroidTest, or tapping a watch or phone button over adb (uiautomator dump), or the phone companion saying "LiftWear is not on the watch" with the app installed.
---

# Wear OS emulator verification

The loop behind every "verified on device" claim in the plan file. `headless-screenshot` is
browser-only and does not apply here.

## Boot exactly one AVD

```sh
source ~/.androidenv     # JAVA_HOME + ANDROID_HOME; without it Gradle dies immediately
export PATH="$PATH:$ANDROID_HOME/emulator:$ANDROID_HOME/platform-tools"
export ANDROID_SERIAL=<serial>   # with more than one target; see the real-hardware note"
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
  `adb shell dumpsys window | grep mCurrentFocus`. On a **real, locked** watch that is the
  keyguard: `mCurrentFocus=…KeyguardLayer…` while `mFocusedApp` is your activity, and
  `dumpsys window policy` says `mIsShowing=true`. `am start` reports success either way and
  the screencap is silently the watch face. A swipe will not clear a PIN — the user must
  unlock, and a worn watch then stays unlocked while a charging one re-locks.
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
ambiactive session after **~60s**, returning to the watch face and `DOZE_SUSPEND`. Confirmed
on a real Galaxy Watch 4 (Wear OS 6), which settles on `TaskAmbiactive` with the app as target.

**`screencap` cannot capture the ambient surface on real hardware.** Once the panel reaches
`DOZE_SUSPEND` it is driven by display offload, so a screenshot returns the *watch face*; once
the screen is fully OFF it returns a ~1.5KB black frame. Neither means your app failed to draw.
Read ambient from logcat and with eyes — the emulator is the only place screenshots work.

**Never read `sys.burn_in_protection.enabled` as what the app will be told.** A Galaxy Watch 4
reports `1` for that property while Wear Compose hands the app
`isBurnInProtectionRequired=false, isLowBitAmbientSupported=false`. Only the values carried on
`AmbientMode.Ambient` drive the code, so log those instead:
`Log.i(TAG, "burnIn=${mode.isBurnInProtectionRequired} lowBit=${mode.isLowBitAmbientSupported}")`.

Force-stop and reboot before testing a negative: a killed process leaves registrations behind,
and a guard only ever observed saying one thing has not been tested.

## A real watch, over Wi-Fi

**ADB debugging and Wireless debugging are two separate toggles** — the first alone exposes
nothing to the network, and `adb mdns services` stays empty. With both on, discovery gives you
the address and you never have to read numbers off the watch:

```sh
adb mdns services          # _adb-tls-pairing._tcp and _adb-tls-connect._tcp, different ports
adb pair <ip>:<pairing-port> <6-digit code>
adb connect <ip>:<connect-port>
```

Pairing leaves **two transports** for one device (the mDNS name and the explicit `ip:port`), so
every later command needs a target. **Do not use `adb $FLAGS`** — this shell is zsh, which does
not word-split unquoted variables, so `-s <ip>` arrives as one argument and adb answers
`-s requires an argument`.

**A re-pairing appends `(2)` to the mDNS name**, giving a serial with a space in it
(`adb-<serial>-<suffix> (2)._adb-tls-connect._tcp`). `ANDROID_SERIAL` silently fails to match
that — every command answers `device not found` while `adb devices` clearly lists it. Quote it
into `-s` instead: `adb -s "$W" …` (verified 2026-09).

**The watch drops Wi-Fi whenever it idles**, so the transport dies between commands and a
54 MB `install` usually dies mid-transfer. Wear OS 6 has no "Wi-Fi always on" setting to stop
it. Wrap adb so every call re-resolves and reconnects first — that turns a dead session into a
few seconds of retry:

```sh
ok() { adb devices | grep -q "^${W}	device"; }
if ! ok; then for i in $(seq 1 12); do
    addr=$(adb mdns services | grep '_adb-tls-connect' | awk '{print $3}' | head -1)
    [ -n "$addr" ] && adb connect "$addr" >/dev/null 2>&1; ok && break; sleep 3
done; fi
adb -s "$W" "$@"
```

The advertised port goes stale after a drop — `connection refused` on a name mDNS still lists
means `adb kill-server && adb start-server` to flush the cache, then re-read the port.

**A charging Galaxy Watch is useless for screen tests.** On the puck the system's charging AOD
owns the display outright (`AmbientTaskStateMachine: -> TaskForcedAmbient. Reason: forced
ambient`, logged from the *sysui* pid, not the app's), so `am start` succeeds, the app draws,
and the screencap is a battery percentage. Ambient and always-on can only be tested on battery.

**A watch off the wrist locks itself.** `dumpsys trust` shows `deviceLocked=1`; `am start`
reports success and screencaps return the watch face. `input swipe 198 340 198 60` clears a
swipe-only lock (`deviceLocked=0` afterwards proves it), but a PIN needs a human — do not type
one over adb.

**Cold start is ~10s** on real hardware, not the ~5s the emulator needs (verified 2026-09-10:
not for a **release** build, which reached first frame in 0.9–1.9s by `am start -W` TotalTime
and showed Home content by +3s — debug builds not re-measured).
`mCurrentFocus=null` right after `am start` usually means you looked too early, not that it failed.

### Measuring performance

**A sideloaded install runs with no AOT code at all.** After `adb install`,
`dumpsys package dexopt | grep -A3 "\[dev.fquo.liftwear\]"` reads `status=verify` even though
the APK carries `assets/dexopt/baseline.prof` and profileinstaller; ART only compiles it in
background dexopt (idle + charging). **Install the `.dm` beside it** and it compiles at install
instead — `status=speed-profile`, `reason=install-dm`, verified 2026-09-10 on the Wear OS 6
emulator and the Galaxy Watch 4. The names must match, so run it from the output directory:

```sh
cd wear/build/outputs/apk/release && adb -s "$W" install-multiple -r wear-release.apk baselineProfiles/0/wear-release.dm
```

To measure what an APK-only sideload feels like, or to compare one APK in both states, force it:

```sh
adb -s "$W" shell "nohup cmd package compile -m speed-profile -f dev.fquo.liftwear > /data/local/tmp/c.log 2>&1 &"
adb -s "$W" shell am start -W -n dev.fquo.liftwear/.wear.MainActivity   # TotalTime = first frame
adb -s "$W" shell dumpsys gfxinfo dev.fquo.liftwear reset               # drive input swipe, then dump
```

The compile took ~50s on a Galaxy Watch 4; it runs under on-device `nohup` so a Wi-Fi drop
cannot cut it off — poll the dexopt status until it flips. Measured 2026-09-10, same APK, 16
swipes over Home: `verify` 14.0% janky, p90 65ms, p99 101ms, 44 missed vsync; `speed-profile`
3.2%, 31ms, 40ms, 0.

- `dumpsys gfxinfo` answering `Failure while dumping the app` means the process is cached and
  frozen — bring the app to the foreground before dumping.
- `Activities: 3` in `dumpsys meminfo` with one screen visible: `dumpsys activity activities`
  lists each `MainActivity` record with `launchedFromPackage`; `com.samsung.android.wearable.sysui`
  entries are Tile / Ongoing-chip launches stacking new instances onto the task. To reproduce
  without tapping anything, launch with `am start -n <pkg>/.wear.MainActivity`, press HOME, then
  `am start -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n …`: the
  pre-`singleTask` build went to 2 records, the fixed one stays at 1 (verified 2026-09-10).
- **A helper that counts on a dropped transport prints 0**, which reads exactly like "not
  stacked". Check `adb -s "$W" get-state` says `device` before trusting any count.
- `am force-stop` cancels the app's alarms — confirm no rest is pending before timing cold starts.

### Doze, without taking the watch off the charger

**Doze never engages while charging** — but the framework can be lied to, which is the only
practical way to test a rest timer on a watch whose battery you are trying to preserve:

```sh
adb -s "$W" shell dumpsys battery unplug        # framework now believes it is on battery
adb -s "$W" shell input keyevent 223            # screen off
adb -s "$W" shell dumpsys deviceidle force-idle # holds IDLE; "step" does NOT
adb -s "$W" shell dumpsys deviceidle unforce    # ALWAYS undo both of these
adb -s "$W" shell dumpsys battery reset
```

**Use `force-idle`, not `step`.** `step` only advances the state machine one notch, so the
device opens a maintenance window on its own a couple of minutes later (`DeviceIdleController.deep`
in the alarm log) and the measurement is no longer under Doze. `force-idle` prints
`Now forced in to deep idle mode` and holds until `unforce`. Either way it stays `ACTIVE`
forever if the unplug did not take — check `dumpsys battery` shows it unpowered first.

**Touching adb pulls the device out of idle**, so a wrapper that reconnects will silently ruin
the run. Verify the alarms are scheduled *before* sealing, then make no contact at all until
well past the deadline, and confirm `get deep` still says `IDLE` on first contact afterwards.

**Verify the rest actually armed.** `am start` that prints nothing scheduled nothing, and the
run measures an empty queue. A pending alarm must show a *future* `origWhen`; entries under
`Reason=pi_cancelled` are the removal history, not pending — `-S` force-stops the app, and that
cancels its alarms.

Read the result off the alarm itself rather than waiting for a buzz: in `dumpsys alarm`,
**`whenElapsed == maxWhenElapsed` means no deferral window was applied**, and the gap between
two alarms surviving intact is what disproves a rate limit. `exactAllowReason=policy_permission`
confirms `USE_EXACT_ALARM` was install-granted; `flags=0x5` is
`STANDALONE|ALLOW_WHILE_IDLE`, i.e. genuinely `setExactAndAllowWhileIdle`.

Measured 2026-09 on a Galaxy Watch 4 (Wear OS 6), sealed in forced deep idle with no adb contact
across the deadline: two rest alarms 10s apart fired **+15ms and +3ms** off their targets, both
waking the device. The feared ~9-minute allow-while-idle quota does **not** bite there, so
`setExactAndAllowWhileIdle` is sufficient and `setAlarmClock` is not needed.

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

**`:wear`'s run uninstalls the app from every device it runs on — with the real watch attached,
that wipes its live workout, outbox and key.** Pin it: `ANDROID_SERIAL=emulator-5554` is honoured
here even though it is not for the `(2)` Wi-Fi serial; the log then reads
`Running tests on devices: LiftWear_Large(AVD)` with the watch still connected (verified 2026-09-10
on `:core:data`'s run, whose test APK is its own package; `pm list packages` on the watch afterwards
showed nothing new).
A watch that looked gone can reattach mid-session over mDNS, so pin every run rather than
relying on `adb devices` having been empty earlier.

`--tests` is not accepted; filter with
`-Pandroid.testInstrumentationRunnerArguments.package=dev.fquo.liftwear.wear.ambient`.
Results (counts, failures) are in `*/build/outputs/androidTest-results/connected/debug/*.xml`.
Read them with `find … -name '*.xml'`, not a glob: in zsh an unmatched glob aborts the command.
A stale **release**-signed install on the emulator makes the next run fail with
`INSTALL_FAILED_UPDATE_INCOMPATIBLE`; uninstall it from the emulator first.

## Reading the debug log

```sh
adb shell content read --uri content://dev.fquo.liftwear.debuglog/log > liftwear-debug.log
```

The provider is gated on `DUMP`, which the adb shell holds, so no `run-as` or debuggable build is
needed - verified 2026-09-10 on the release build, on the Galaxy Watch 4 over the `(2)` serial
(`adb -s "$W" shell content read …`) as well as on the emulator. The watch → phone path
(Settings → **Send log to phone**) was verified the same day: the watch's own log records
`… saved the debug log (N bytes)`, which only prints when the phone's byte count matches.

On the emulator (2026-09-10):

- `--ei liveRestSeconds 14` writes `alarm warning fired, +2ms against …` and `alarm done fired`
  lines - the cheapest way to see whether alarms are being deferred. The emulator also logs
  `notifications are off, so there is no watch-face chip`.
- `--es screen crash` (debug gallery) crashes on purpose. Relaunch and the log holds the stack
  trace under `E Crash`, then `previous process ended …: CRASH` from the next start.

## Driving the watch UI over adb

- **Do the whole navigation in one command.** Between separate commands the Galaxy Watch 4 fell
  back to the watch face within about a minute, and the next swipe opened the app launcher
  instead. Wake, `am start`, navigate and tap together, and gate every tap on `mCurrentFocus`
  still being LiftWear.
- **Find a button by its text, not a remembered coordinate.** `adb shell uiautomator dump
  /sdcard/ui.xml` works on Wear OS 6 and on the phone, and lists Compose text nodes with
  `bounds`; tap the centre. A coordinate from an earlier screenshot hit **Resume** instead of
  Settings once, because Home scrolls differently after a cold start.
- The debug log's `Nav` lines say which screen a tap really reached - cheaper than a screenshot.
- Opening the workout screen on a watch without `POST_NOTIFICATIONS` raises the system permission
  dialog, which then owns focus. Leave the answer to the user.
- A release with resource shrinking drops `array/android_wear_capabilities` unless
  `wear/src/main/res/raw/keep.xml` keeps it; the phone companion then says "LiftWear is not on the
  watch" with the app installed. Check with `aapt2 dump resources` (verified 2026-09-10).
