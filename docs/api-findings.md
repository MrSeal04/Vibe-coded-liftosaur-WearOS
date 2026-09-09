# Liftosaur API — findings from live schema capture

Captured 2026-09-07 against the real v1 API. Raw responses are in `fixtures/`
(gitignored — they contain real training data). This file records the *schema*
and the decisions that follow from it.

## 1. setIds are NOT stable — offline start is impossible

The docs say "Set IDs generated client-side". They are not. `GET /workout/next`
returns fresh random `setId`s on **every call**:

```
call 1: kevryi, wzmhwm, dmpvvc
call 2: xyoasw, dfkrkf, xdipyx
call 3: rsdhaq, xkjzad, mvvorc
```

`startTime` also advances per call. A prefetched `/workout/next` therefore cannot
be used to log sets offline — its setIds refer to nothing.

**Consequence — the plan's documented fallback is now the actual design:**

> **Online is required to START a workout. Everything after that works offline.**

`POST /workout/start` returns the workout *with* its setIds; we cache that
response and the outbox logs against those real IDs. Sets and finish queue
offline exactly as designed. Only the initial start needs connectivity.

*Open question for Phase 4 (needs a write test against the disposable program):
does `POST /workout/set` accept a setId the client invented? If so, offline start
could be revisited. Not blocking.*

## 2. History is Liftoscript TEXT, not structured JSON

`GET /history` returns records shaped `{ id, text }` — nothing else. `id` is a
unix-ms timestamp. The `text` is the Liftoscript Workouts format:

```
2026-01-02 09:15:00 +00:00 / program: "Example Program" / dayName: "Day A" /
week: 1 / dayInWeek: 1 / duration: 3332s / exercises: {
  Incline Bench Press, Barbell / 1x10 95lb, 1x8 115lb, 1x8 115lb @10 / warmup: 1x10 45lb, 1x10 65lb / target: 2x8 95lb 90s, 1x8 95lb @10+ 90s
  Chin Up / 3x6 0lb, 1x5 0lb @10 / target: 3x6 0lb 150s, 1x6 0lb @10+ 150s
}
```

Set notation: `<sets>x<reps> <weight>` , `@N` = RPE, trailing `+` = AMRAP,
`<n>s` = rest seconds.

**Consequence for Phase 8:** the watch needs a small tolerant parser for this
format — header key/value pairs, then one line per exercise. Extract date,
program, dayName, duration and a per-exercise summary; that is all a round
screen can usefully show. Do **not** attempt full-fidelity Liftoscript parsing,
and never crash on an unrecognised line — fall back to showing the raw line.

## 3. Pagination

`{ hasMore: bool, nextCursor: <id of last record>, records: [...] }`.
The cursor is just the last record's id. `limit` works as documented.

## 4. Entry / set shape (confirmed against live data)

`warmupSets` and `sets` are **separate arrays** on each entry, both containing
full set objects with their own setIds.

```
entry:  entryId, exerciseId, equipment, name, imageUrl, superset (null),
        notes, description, hasUpdateScript, promptedVars ([]), warmupSets[], sets[]
set:    setId, index, isWarmup, reps, minReps, isAmrap, weight, originalWeight,
        plates[], rpe, logRpe, askWeight, isUnilateral, timer, setTimer, completed
```

Observed: `superset: null`, `promptedVars: []`, `plates: []` for machine
exercises. The AMRAP set is the last of the working sets (`isAmrap: true`).

## 5. Settings

```json
{ "units": "lb", "timers": { "warmup": 90, "workout": 180, "superset": null } }
```

`timers.superset` is **null** — every timer field must be nullable in the DTO,
with a sane client-side default.

## 6. `description` is markdown and can be very long

A captured entry's `description` was ~1.4 KB of markdown with `**bold**` and
embedded newlines. On a round watch screen this cannot be shown inline.

**Consequence:** truncate hard on the workout screen (or show an info affordance
that opens a scrollable detail screen). Strip/render markdown — do not dump raw
`**` at the user.

## 7. Numeric values can carry float artifacts

A measurement came back in the shape `"12.300000190734863%"` — the server does not round
before serialising. Anything derived from a float may arrive this way.

**Consequence:** `Weight.parse` must accept arbitrary decimal precision (it does),
and every display path must round explicitly. Never render a parsed value raw.

## 8. Sanitising fixtures for the repo

`fixtures/` (repo root) holds raw captures and is gitignored. Sanitised copies
under `core/api/src/test/resources/fixtures/` are committed as test data:
structure, types and nullability preserved, training content replaced.

The `.gitignore` patterns are anchored (`/fixtures/`, `/backups/`) — unanchored
`fixtures/` would also match the test-resource directory at any depth and
silently exclude the committed test data.

Measurement fixtures are deliberately **not** committed: they carry bodyweight
and body-fat readings, and nothing in scope needs them.

---

## Write-path findings (2026-09-08, Phase 4)

First writes ever sent to the live account. Run against a disposable program
(`LiftWear TEST - delete me`), with a fresh backup taken first; everything created
was deleted afterwards and the account verified back to its prior record count, two
programs, the real one still current.

### 6. There is no API to set the current program — and it is not needed

`POST /programs` and `DELETE /programs/:id` exist, `PUT /programs/:id` updates one,
but nothing marks a program current. The plan's Phase 0 safeguard 2 said to "make it
current for test sessions", which is not possible.

It is also unnecessary, and the alternative is safer: **`POST /workout/start` takes an
explicit `programId` and starts from it regardless of which program is current.**

```
current program: <real-program-id>        start body: {"programId":"zwslmjce", ...}
returns:         programName "LiftWear TEST - delete me", dayName "Test Day"
after the test:  <real-program-id> still isCurrent: true
```

All write testing therefore runs against a program that is never current, so the phone
app never shows anything unexpected and the real program's day pointer never moves.
`DELETE /programs/:id` refuses to delete the current program, which is a useful guard.

### 7. `POST /workout/sets` is idempotent on replay — verified, not assumed

The docs claim writes are last-writer-wins and safely repeatable. Confirmed directly,
because the outbox's crash-recovery depends on it:

```
send 5 sets in one call  -> 5 sets completed, 5 sets on the entry
send the IDENTICAL call  -> 5 sets completed, 5 sets on the entry
```

No duplicates, no error. A drain that dies after the server accepted a batch but before
the rows were deleted is safe to repeat.

### 8. Open question answered: client-invented setIds are REJECTED

The Phase 0 note left this open. `POST /workout/set` with a made-up setId:

```json
{"error":{"code":"set_not_found","message":"No set 'liftwearfake01' in the current workout"}}
```

**Offline start is therefore impossible, confirmed by a write test rather than inferred**
from setId instability. The Phase 4 decision to keep START out of the outbox is correct
and can stop being provisional. Note `set_not_found` is a 4th `400`-family code not in
the published error table; it maps to `BadRequest` today, which is the right behaviour
(park, do not retry).

### 9. entryIds are readable slugs, unlike setIds

The test entry came back as `zercherSquat_barbell` — derived from exercise and equipment,
not random. Only setIds are regenerated per call. Nothing depends on this, but it means an
entryId is stable across calls in a way a setId is not.
