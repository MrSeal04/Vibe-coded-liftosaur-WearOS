package dev.fquo.liftwear.wear.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.PickerGroup
import androidx.wear.compose.material3.PickerState
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.rememberPickerState
import dev.fquo.liftwear.wear.ui.circularPadding
import dev.fquo.liftwear.wear.ui.edgeButtonInset
import dev.fquo.liftwear.wear.ui.topArcInset
import dev.fquo.liftwear.wear.ui.primaryEdgeButtonSize
import dev.fquo.liftwear.wear.ui.common.LoadingScreen
import dev.fquo.liftwear.wear.ui.common.MessageScreen

/**
 * Confirms one set through a pre-filled picker.
 *
 * Deliberately two interactions rather than one tap: rotate to adjust if the set did not
 * go as prescribed, then commit. Mislogging costs more than the extra press, because
 * correcting it later means finding the set again on a 1.4-inch screen.
 */
@Composable
fun SetConfirmScreen(
    viewModel: WorkoutViewModel,
    entryId: String,
    setId: String,
    onLogged: () -> Unit,
) {
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val ref = viewModel.setRef(entryId, setId)

    if (ref == null) {
        MessageScreen("Set not found", "The workout changed. Go back and try again.")
        return
    }
    if (busy) {
        LoadingScreen("Logging")
        return
    }

    // Built once per set. The pickers own the live selection from here on; rebuilding
    // this from their values would reset them on every rotary click.
    val initial = remember(entryId, setId) { SetConfirmState.forSet(ref.set, viewModel.units) }
    SetConfirmContent(
        exerciseName = ref.exerciseName,
        counter = setCounterLabel(ref),
        initial = initial,
        onLog = { completed -> viewModel.logSet(entryId, setId, completed, onLogged) },
        resetKey = setId,
    )
}

/**
 * Stateless body, so the layout can be exercised in previews at every screen size and font
 * scale without a ViewModel - the largest font on the smallest watch is the case that breaks.
 */
@Composable
internal fun SetConfirmContent(
    exerciseName: String,
    counter: String,
    initial: SetConfirmState,
    onLog: (dev.fquo.liftwear.api.dto.CompletedDto) -> Unit,
    resetKey: String,
) {
    var focused by remember(resetKey) { mutableStateOf(initial.initialField) }
    // Sized so three wheels fit side by side on a 198dp watch; PickerGroup auto-centres
    // the focused one when a fourth (unilateral) field pushes past the edge.
    val small = dev.fquo.liftwear.wear.ui.screenClass() == dev.fquo.liftwear.wear.ui.ScreenClass.Small
    val pickerWidth = if (small) 48.dp else 56.dp
    val pickerHeight = if (small) 76.dp else 88.dp
    // Three wheels fit side by side; a fourth (a unilateral set that also logs RPE) does
    // not, so that case falls back to PickerGroup's auto-centring of the focused wheel.
    val fitsSideBySide = initial.fields.size <= 3

    val pickers: List<Pair<SetField, PickerState>> = initial.fields.map { field ->
        field to rememberPickerState(
            initialNumberOfOptions = initial.optionCount(field).coerceAtLeast(1),
            initiallySelectedIndex = initial.indexOf(field),
            shouldRepeatOptions = false,
        )
    }

    val current: State<SetConfirmState> = remember(initial, pickers) {
        derivedStateOf {
            pickers.fold(initial) { acc, (field, picker) ->
                acc.withIndex(field, picker.selectedOptionIndex)
            }
        }
    }

    ScreenScaffold {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = circularPadding(0.04f),
                    end = circularPadding(0.04f),
                    top = topArcInset,
                    bottom = edgeButtonInset,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        ) {
            Text(
                text = exerciseName,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = buildString {
                    append(counter)
                    initial.weightUnit?.let { append("  ·  ").append(it) }
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            if (fitsSideBySide) {
                // Labels sit directly over their own wheel; a single centred caption would
                // not line up with anything once the wheels have different widths.
                Row(horizontalArrangement = Arrangement.Center) {
                    initial.fields.forEach { field ->
                        Text(
                            text = fieldLabel(field, short = small),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (field == focused) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            modifier = Modifier.width(pickerWidth),
                        )
                    }
                }
            } else {
                Text(
                    text = fieldLabel(focused, short = small),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            PickerGroup(
                selectedPickerState = pickers.firstOrNull { it.first == focused }?.second,
                autoCenter = !fitsSideBySide,
                modifier = Modifier.fillMaxWidth(),
            ) {
                pickers.forEach { (field, picker) ->
                    PickerGroupItem(
                        pickerState = picker,
                        selected = field == focused,
                        onSelected = { focused = field },
                        contentDescription = { fieldLabel(field) },
                        modifier = Modifier.size(width = pickerWidth, height = pickerHeight),
                    ) { optionIndex, pickerSelected ->
                        Text(
                            text = initial.labelFor(field, optionIndex),
                            style = MaterialTheme.typography.numeralSmall,
                            maxLines = 1,
                            color = if (pickerSelected) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

        }

        EdgeButton(
            onClick = { onLog(current.value.toCompleted()) },
            buttonSize = primaryEdgeButtonSize,
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Text("Log set", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}

/**
 * [short] is used on 198dp screens, where "weight" clips inside a 48dp column once the
 * user turns the system font scale up.
 */
internal fun fieldLabel(field: SetField, short: Boolean = false): String = when (field) {
    SetField.Reps -> "reps"
    SetField.RepsLeft -> "left"
    SetField.Weight -> if (short) "wt" else "weight"
    SetField.Rpe -> "RPE"
}
