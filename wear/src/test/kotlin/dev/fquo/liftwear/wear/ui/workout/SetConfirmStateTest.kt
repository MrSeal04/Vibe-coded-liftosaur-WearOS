package dev.fquo.liftwear.wear.ui.workout

import dev.fquo.liftwear.api.dto.SetDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The picker's selection rules. These decide what actually lands in Liftosaur, so they are
 * tested rather than eyeballed on an emulator.
 */
class SetConfirmStateTest {

    private fun set(
        setId: String = "abc123",
        reps: Int? = 5,
        minReps: Int? = null,
        weight: String? = "100lb",
        isAmrap: Boolean = false,
        askWeight: Boolean = false,
        logRpe: Boolean = false,
        rpe: Double? = null,
        isUnilateral: Boolean = false,
    ) = SetDto(
        setId = setId,
        reps = reps,
        minReps = minReps,
        weight = weight,
        isAmrap = isAmrap,
        askWeight = askWeight,
        logRpe = logRpe,
        rpe = rpe,
        isUnilateral = isUnilateral,
    )

    @Test
    fun `plain set prefills prescribed reps and weight`() {
        val state = SetConfirmState.forSet(set())
        assertEquals(5, state.reps)
        assertEquals("100lb", state.weight.toString())
        assertEquals(SetField.Reps, state.initialField)
    }

    @Test
    fun `weight picker steps by 5 in pounds and 2 point 5 in kilos`() {
        val lb = SetConfirmState.forSet(set(weight = "100lb"))
        val below = lb.weightOptions[lb.weightIndex - 1].value
        assertEquals(95.0, below, 0.001)

        val kg = SetConfirmState.forSet(set(weight = "60kg"))
        assertEquals(57.5, kg.weightOptions[kg.weightIndex - 1].value, 0.001)
    }

    @Test
    fun `off-grid prescribed weight stays selectable`() {
        // A microplate lift: 102.5lb is not on the 5lb grid built around it.
        val state = SetConfirmState.forSet(set(weight = "102.5lb"))
        assertEquals(102.5, state.weight!!.value, 0.001)
        assertEquals(1, state.weightOptions.count { it.value == 102.5 })
        assertEquals(state.weightOptions.sortedBy { it.value }, state.weightOptions)
    }

    @Test
    fun `weight options never go negative`() {
        val state = SetConfirmState.forSet(set(weight = "10lb"))
        assertTrue(state.weightOptions.all { it.value >= 0.0 })
    }

    @Test
    fun `amrap focuses reps and starts at the prescribed minimum`() {
        val state = SetConfirmState.forSet(set(reps = null, minReps = 3, isAmrap = true))
        assertEquals(SetField.Reps, state.initialField)
        assertEquals(3, state.reps)
        // An AMRAP can run well past the floor, so the range has to be generous.
        assertTrue(state.repsOptions.last() >= 50)
    }

    @Test
    fun `amrap wins over askWeight for initial focus`() {
        val state = SetConfirmState.forSet(set(isAmrap = true, askWeight = true))
        assertEquals(SetField.Reps, state.initialField)
    }

    @Test
    fun `askWeight focuses the weight picker`() {
        val state = SetConfirmState.forSet(set(askWeight = true))
        assertEquals(SetField.Weight, state.initialField)
    }

    @Test
    fun `logRpe adds an RPE field focused by default`() {
        val state = SetConfirmState.forSet(set(logRpe = true))
        assertEquals(SetField.Rpe, state.initialField)
        assertTrue(SetField.Rpe in state.fields)
        assertEquals(8.0, state.rpe!!, 0.001)
    }

    @Test
    fun `prescribed RPE off the half-point grid is selectable`() {
        val state = SetConfirmState.forSet(set(logRpe = true, rpe = 7.25))
        assertEquals(7.25, state.rpe!!, 0.001)
    }

    @Test
    fun `unilateral set exposes a second rep field`() {
        val state = SetConfirmState.forSet(set(isUnilateral = true))
        assertTrue(SetField.RepsLeft in state.fields)
        assertNotNull(state.toCompleted().repsLeft)
    }

    @Test
    fun `bilateral set sends no repsLeft`() {
        assertEquals(null, SetConfirmState.forSet(set()).toCompleted().repsLeft)
    }

    @Test
    fun `bodyweight set with no weight hides the weight picker`() {
        val state = SetConfirmState.forSet(set(weight = null))
        assertFalse(SetField.Weight in state.fields)
        assertEquals(null, state.toCompleted().weight)
    }

    @Test
    fun `every completed payload carries a rep count`() {
        // The API rejects a set missing required input with 400 missing_set_input; a picker
        // has no empty state, so that failure is unreachable from this screen.
        val cases = listOf(
            set(),
            set(isAmrap = true, reps = null, minReps = null),
            set(askWeight = true),
            set(logRpe = true),
            set(weight = null),
        )
        cases.forEach { s ->
            assertNotNull(SetConfirmState.forSet(s).toCompleted().reps)
        }
    }

    @Test
    fun `rotating a picker updates only that field`() {
        val state = SetConfirmState.forSet(set(logRpe = true))
        val rotated = state.withIndex(SetField.Reps, state.repsIndex + 2)
        assertEquals(7, rotated.reps)
        assertEquals(state.weight, rotated.weight)
        assertEquals(state.rpe, rotated.rpe)
    }

    @Test
    fun `picker shows a bare number but still logs the unit`() {
        // The wheel is 48-56dp wide; "185lb" clipped to "185l" there, so the unit moved to
        // a label. The payload must be unaffected by that presentation change.
        val state = SetConfirmState.forSet(set(weight = "185lb"))
        assertEquals("lb", state.weightUnit)
        assertEquals("185", state.labelFor(SetField.Weight, state.weightIndex))
        assertEquals("185lb", state.toCompleted().weight)
    }

    @Test
    fun `fractional weights keep their decimal in the picker`() {
        val state = SetConfirmState.forSet(set(weight = "62.5kg"))
        assertEquals("62.5", state.labelFor(SetField.Weight, state.weightIndex))
        assertEquals("62.5kg", state.toCompleted().weight)
    }

    @Test
    fun `a set with no weight has no unit label`() {
        assertEquals(null, SetConfirmState.forSet(set(weight = null)).weightUnit)
    }

    @Test
    fun `percentage weights step by one rather than a barbell jump`() {
        val state = SetConfirmState.forSet(set(weight = "80%"))
        assertEquals(79.0, state.weightOptions[state.weightIndex - 1].value, 0.001)
    }
}
