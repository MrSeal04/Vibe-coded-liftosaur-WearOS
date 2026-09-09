package dev.fquo.liftwear.wear.preview

import dev.fquo.liftwear.api.dto.CompletedDto
import dev.fquo.liftwear.api.history.WorkoutTextParser
import dev.fquo.liftwear.api.dto.DayDataDto
import dev.fquo.liftwear.api.dto.EntryDto
import dev.fquo.liftwear.api.dto.PlateDto
import dev.fquo.liftwear.api.dto.SetDto
import dev.fquo.liftwear.api.dto.WorkoutDto

/**
 * Invented data, shaped like a real `/workout/start` response.
 *
 * Deliberately not a captured fixture: previews are debug source, and no real training
 * history belongs in a file that ships in a build or lands in a public repo.
 */
object SampleWorkout {

    private fun set(
        id: String,
        reps: Int,
        weight: String?,
        warmup: Boolean = false,
        done: Boolean = false,
        amrap: Boolean = false,
        plates: List<PlateDto> = emptyList(),
        timer: Int? = 180,
    ) = SetDto(
        setId = id,
        isWarmup = warmup,
        reps = reps,
        isAmrap = amrap,
        weight = weight,
        plates = plates,
        timer = timer,
        completed = if (done) CompletedDto(reps = reps, weight = weight) else null,
    )

    val squat = EntryDto(
        entryId = "entry-squat",
        name = "Squat",
        hasUpdateScript = true,
        warmupSets = listOf(
            set("w1", 5, "45lb", warmup = true, done = true, timer = 60),
            set("w2", 5, "95lb", warmup = true, done = true, timer = 60),
        ),
        sets = listOf(
            set("s1", 5, "185lb", done = true, plates = listOf(PlateDto("45lb", 1), PlateDto("25lb", 1))),
            set("s2", 5, "185lb", plates = listOf(PlateDto("45lb", 1), PlateDto("25lb", 1))),
            set("s3", 5, "185lb", plates = listOf(PlateDto("45lb", 1), PlateDto("25lb", 1))),
            set("s4", 5, "185lb", amrap = true, plates = listOf(PlateDto("45lb", 1), PlateDto("25lb", 1))),
        ),
    )

    /** The long-name, no-plates case - a machine exercise, which is where layouts break. */
    val press = EntryDto(
        entryId = "entry-press",
        name = "Seated Dumbbell Shoulder Press",
        sets = listOf(set("p1", 12, "40lb", plates = emptyList()), set("p2", 12, "40lb")),
    )

    val bodyweight = EntryDto(
        entryId = "entry-pullup",
        name = "Pull Up",
        sets = listOf(set("u1", 8, null, amrap = true)),
    )

    val workout = WorkoutDto(
        programId = "sample",
        programName = "Sample 5x5",
        dayName = "Day A - Lower",
        dayData = DayDataDto(day = 1, week = 1, dayInWeek = 1),
        startTime = 1_767_330_000_000L,
        entries = listOf(squat, press, bodyweight),
    )

    /**
     * Invented history in Liftosaur's Liftoscript "Workouts format", including one record
     * the parser cannot read - that case is the whole reason the parser is tolerant, and it
     * has to be visible in the gallery or nobody would ever look at how it renders.
     *
     * Ids are unix millis, which is what the real API uses as a record's identity.
     */
    val historyText: List<Pair<Long, String>> = listOf(
        1_788_747_172_292L to """
            2026-09-07 02:12:52 +00:00 / program: "Sample Program" / dayName: "Day B - Upper" / week: 1 / dayInWeek: 4 / duration: 3300s / exercises: {
              Bent Over Row / 3x10 100lb / target: 3x10 100lb 90s
              Incline Bench Press, Barbell / 3x8 135lb, 1x6 155lb @9 / warmup: 1x10 45lb / target: 3x8 135lb 120s
              Face Pull / 3x15 30lb / target: 3x15 30lb 60s
              Hammer Curl / 3x12 25lb / target: 3x12 25lb 60s
            }
        """.trimIndent(),
        1_788_655_689_367L to """
            2026-09-06 00:48:09 +00:00 / program: "Sample Program" / dayName: "Day A - Push" / week: 1 / dayInWeek: 2 / duration: 2700s / exercises: {
              Bench Press / 5x5 135lb / warmup: 1x5 45lb, 1x5 95lb / target: 5x5 135lb 180s
              Overhead Press / 3x8 75lb @9 / target: 3x8 75lb 120s
            }
        """.trimIndent(),
        1_788_569_289_000L to """
            2026-09-05 00:48:09 +00:00 / program: "Sample Program" / dayName: "Day C - Legs" / week: 1 / dayInWeek: 1 / duration: 5400s / exercises: {
              Squat / 5x5 200lb / target: 5x5 200lb 210s
              Leg Press / 3x12 250lb / target: 3x12 250lb 90s
              Calf Raise / 4x15 80lb / target: 4x15 80lb 45s
            }
        """.trimIndent(),
        1_788_482_889_000L to """
            2026-09-04 00:48:09 +00:00 / program: "Sample Program" / dayName: "Conditioning" / week: 1 / dayInWeek: 5 / duration: 1200s / exercises: {
              ~~ notation this build has never seen ~~
            }
        """.trimIndent(),
    )

    val history = historyText.map { (id, text) -> WorkoutTextParser.parse(id, text) }
}
