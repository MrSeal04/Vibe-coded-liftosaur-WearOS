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
  Lat Pulldown, Leverage Machine / 1x13 85lb, 1x12 100lb, 1x12 100lb @10 / warmup: 1x0 50lb, 1x0 70lb / target: 2x12 85lb 90s, 1x12 85lb @10+ 90s
  Chin Up / 3x0 0lb, 1x0 0lb @10 / target: 3x4 0lb 150s, 1x4 0lb @10+ 150s
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
