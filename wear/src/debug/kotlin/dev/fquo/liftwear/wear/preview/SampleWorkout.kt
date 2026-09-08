package dev.fquo.liftwear.wear.preview

import dev.fquo.liftwear.api.dto.CompletedDto
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
}
