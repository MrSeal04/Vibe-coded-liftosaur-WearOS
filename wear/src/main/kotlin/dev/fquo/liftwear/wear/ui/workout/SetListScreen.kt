package dev.fquo.liftwear.wear.ui.workout

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import dev.fquo.liftwear.api.Weight
import dev.fquo.liftwear.data.workout.SetRef
import dev.fquo.liftwear.data.workout.WorkoutPlan
import dev.fquo.liftwear.wear.ui.common.MessageScreen

/**
 * Every set of one exercise, so sets can be completed or corrected out of order.
 *
 * A `TransformingLazyColumn` rather than a plain list: the edge scaling is what makes a
 * long list legible on a circle, where the top and bottom rows are physically narrower.
 */
@Composable
fun SetListScreen(
    viewModel: WorkoutViewModel,
    entryIndex: Int,
    onConfirmSet: (entryId: String, setId: String) -> Unit,
) {
    val workout by viewModel.workout.collectAsStateWithLifecycle()
    val entry = workout?.entries?.getOrNull(entryIndex)

    if (workout == null || entry == null) {
        MessageScreen("Exercise not found", "The workout changed.")
        return
    }

    val refs = WorkoutPlan.refsFor(entry, entryIndex)
    val listState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()

    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item {
                ListHeader(
                    modifier = Modifier.transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                ) {
                    Text(entry.name, textAlign = TextAlign.Center, maxLines = 2)
                }
            }
            items(refs, key = { it.setId }) { ref ->
                Button(
                    onClick = {
                        if (ref.isCompleted) {
                            viewModel.undoSet(ref.entryId, ref.setId)
                        } else {
                            onConfirmSet(ref.entryId, ref.setId)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = setCounterLabel(ref),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(text = setSummary(ref), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                    }
                }
            }
        }
    }
}

/** A completed set shows what was logged, not what was prescribed - that is the whole point of checking. */
internal fun setSummary(ref: SetRef): String {
    val done = ref.set.completed
    return if (done != null) {
        val weight = done.weight?.let(Weight::parse)?.toString() ?: ""
        val rpe = done.rpe?.let { " @${SetConfirmState.formatRpe(it)}" } ?: ""
        "✓ ${done.reps ?: "?"}${if (weight.isEmpty()) "" else " × $weight"}$rpe"
    } else {
        val weight = ref.set.weight?.let(Weight::parse)?.toString()
        val reps = repsLabel(ref.set).removePrefix("× ")
        if (weight == null) reps else "$weight  ×  $reps"
    }
}
