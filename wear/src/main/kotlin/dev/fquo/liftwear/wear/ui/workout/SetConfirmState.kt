package dev.fquo.liftwear.wear.ui.workout

import dev.fquo.liftwear.api.Weight
import dev.fquo.liftwear.api.dto.CompletedDto
import dev.fquo.liftwear.api.dto.SetDto
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Which field the picker opens on.
 *
 * The chosen design confirms every set through a picker rather than one tap, so the only
 * thing that makes it two interactions instead of four is opening on the field that
 * actually needs changing.
 */
enum class SetField { Reps, RepsLeft, Weight, Rpe }

/**
 * Pure state behind the set-confirmation picker: which fields to show, their options, and
 * which option starts selected. No Compose, no Android - the selection rules are the part
 * worth testing, and they are the part that decides whether a set logs correctly.
 */
data class SetConfirmState(
    val fields: List<SetField>,
    val repsOptions: List<Int>,
    val repsIndex: Int,
    val repsLeftOptions: List<Int>,
    val repsLeftIndex: Int,
    val weightOptions: List<Weight>,
    val weightIndex: Int,
    val rpeOptions: List<Double>,
    val rpeIndex: Int,
    val initialField: SetField,
    /** Shown once beside the weight wheel; the options themselves are bare numbers. */
    val weightUnit: String?,
) {
    val reps: Int get() = repsOptions.getOrElse(repsIndex) { 0 }
    val repsLeft: Int get() = repsLeftOptions.getOrElse(repsLeftIndex) { 0 }
    val weight: Weight? get() = weightOptions.getOrNull(weightIndex)
    val rpe: Double? get() = rpeOptions.getOrNull(rpeIndex)

    fun withIndex(field: SetField, index: Int): SetConfirmState = when (field) {
        SetField.Reps -> copy(repsIndex = index)
        SetField.RepsLeft -> copy(repsLeftIndex = index)
        SetField.Weight -> copy(weightIndex = index)
        SetField.Rpe -> copy(rpeIndex = index)
    }

    fun indexOf(field: SetField): Int = when (field) {
        SetField.Reps -> repsIndex
        SetField.RepsLeft -> repsLeftIndex
        SetField.Weight -> weightIndex
        SetField.Rpe -> rpeIndex
    }

    fun optionCount(field: SetField): Int = when (field) {
        SetField.Reps -> repsOptions.size
        SetField.RepsLeft -> repsLeftOptions.size
        SetField.Weight -> weightOptions.size
        SetField.Rpe -> rpeOptions.size
    }

    fun labelFor(field: SetField, index: Int): String = when (field) {
        SetField.Reps -> repsOptions.getOrNull(index)?.toString().orEmpty()
        SetField.RepsLeft -> repsLeftOptions.getOrNull(index)?.toString().orEmpty()
        // Bare number: the unit is rendered once, in [weightUnit].
        SetField.Weight -> weightOptions.getOrNull(index)?.let(::formatWeightValue).orEmpty()
        SetField.Rpe -> rpeOptions.getOrNull(index)?.let(::formatRpe).orEmpty()
    }

    /**
     * The payload for `POST /workout/set`.
     *
     * Every shown field is always sent with a concrete value, which is why the API's
     * `400 missing_set_input` cannot be reached from this screen: a picker has no empty state.
     */
    fun toCompleted(): CompletedDto = CompletedDto(
        reps = reps,
        repsLeft = if (SetField.RepsLeft in fields) repsLeft else null,
        weight = if (SetField.Weight in fields) weight?.toString() else null,
        rpe = if (SetField.Rpe in fields) rpe else null,
    )

    companion object {

        /** Barbell jumps. Plate math is the server's job; this is only the picker step. */
        private const val KG_INCREMENT = 2.5
        private const val LB_INCREMENT = 5.0
        private const val STEPS_BELOW = 8
        private const val STEPS_ABOVE = 12

        fun forSet(set: SetDto, fallbackUnits: String = "lb"): SetConfirmState {
            val prescribedWeight = set.weight?.let(Weight::parse)
            val units = prescribedWeight?.unit ?: fallbackUnits

            val fields = buildList {
                add(SetField.Reps)
                if (set.isUnilateral) add(SetField.RepsLeft)
                if (prescribedWeight != null) add(SetField.Weight)
                if (set.logRpe || set.rpe != null) add(SetField.Rpe)
            }

            // An AMRAP has no prescribed count, only a floor - start there and let the
            // lifter dial up, rather than pre-filling a number they did not do.
            val prescribedReps = if (set.isAmrap) {
                set.minReps ?: set.reps ?: 1
            } else {
                set.reps ?: set.minReps ?: 1
            }
            val repsOptions = repsRange(prescribedReps, set.isAmrap)
            val weightOptions = prescribedWeight?.let { weightRange(it) }.orEmpty()
            val rpeOptions = rpeRange(set.rpe)

            return SetConfirmState(
                fields = fields,
                repsOptions = repsOptions,
                repsIndex = repsOptions.indexOf(prescribedReps).coerceAtLeast(0),
                repsLeftOptions = repsOptions,
                repsLeftIndex = repsOptions.indexOf(prescribedReps).coerceAtLeast(0),
                weightOptions = weightOptions,
                weightIndex = prescribedWeight
                    ?.let { w -> weightOptions.indexOfFirst { it.value == w.value } }
                    ?.coerceAtLeast(0) ?: 0,
                rpeOptions = rpeOptions,
                rpeIndex = (set.rpe?.let { rpeOptions.indexOf(it) } ?: rpeOptions.indexOf(8.0))
                    .coerceAtLeast(0),
                initialField = initialFieldFor(set, fields),
                weightUnit = if (prescribedWeight != null) units else null,
            )
        }

        /**
         * Where the rotary crown lands first. AMRAP wins over the others because the rep
         * count is the one number the program did not decide.
         */
        internal fun initialFieldFor(set: SetDto, fields: List<SetField>): SetField = when {
            set.isAmrap -> SetField.Reps
            set.askWeight && SetField.Weight in fields -> SetField.Weight
            set.logRpe && SetField.Rpe in fields -> SetField.Rpe
            else -> SetField.Reps
        }

        private fun repsRange(prescribed: Int, isAmrap: Boolean): List<Int> {
            val top = if (isAmrap) max(50, prescribed * 3) else max(30, prescribed * 2)
            return (0..top).toList()
        }

        private fun weightRange(prescribed: Weight): List<Weight> {
            val increment = when (prescribed.unit.lowercase()) {
                "kg" -> KG_INCREMENT
                "lb" -> LB_INCREMENT
                // Bodyweight-style units ("%") and anything unexpected get a unit step
                // rather than a barbell jump.
                else -> 1.0
            }
            val steps = (-STEPS_BELOW..STEPS_ABOVE)
                .map { prescribed.value + it * increment }
                .filter { it >= 0.0 }
            // The prescribed value may sit off the increment grid (a microplate, or a
            // percentage-derived number); it must still be selectable.
            return (steps + prescribed.value)
                .map { (it * 100).roundToInt() / 100.0 }
                .distinct()
                .sorted()
                .map { Weight(it, prescribed.unit) }
        }

        private fun rpeRange(prescribed: Double?): List<Double> {
            val grid = generateSequence(6.0) { it + 0.5 }.takeWhile { it <= 10.0 }.toList()
            return if (prescribed != null && prescribed !in grid) {
                (grid + prescribed).sorted()
            } else {
                grid
            }
        }

        fun formatWeightValue(weight: Weight): String {
            val v = weight.value
            return if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()
        }

        fun formatRpe(value: Double): String =
            if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
    }
}
