package dev.fquo.liftwear.wear.ui.history

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import dev.fquo.liftwear.api.history.WorkoutRecord
import dev.fquo.liftwear.wear.ui.common.MessageScreen

/**
 * One past workout, exercise by exercise.
 *
 * Everything here comes out of [dev.fquo.liftwear.api.history.WorkoutTextParser], which
 * reads a text format this app does not own and cannot pin a version of. Lines it did not
 * understand are printed verbatim at the bottom rather than dropped: a workout that was
 * genuinely performed should never disappear because its notation moved on.
 */
@Composable
fun HistoryDetailScreen(viewModel: HistoryViewModel, recordId: Long) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val record = state.records.firstOrNull { it.id == recordId }

    if (record == null) {
        MessageScreen("Workout not found", "It may have scrolled out of the cache.")
        return
    }

    HistoryDetailContent(record)
}

@Composable
internal fun HistoryDetailContent(record: WorkoutRecord) {
    val context = LocalContext.current
    val date = remember(record.id) {
        DateUtils.formatDateTime(
            context,
            record.id,
            DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_ALL,
        )
    }

    val listState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()

    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item {
                ListHeader(
                    modifier = Modifier.transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                ) {
                    Text(
                        text = HistorySummary.title(record),
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            item {
                Card(
                    onClick = {},
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(date, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                        HistorySummary.context(record)?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodyExtraSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            text = HistorySummary.subtitle(record),
                            style = MaterialTheme.typography.bodyExtraSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }

            items(record.exercises, key = { it.name + it.raw.hashCode() }) { line ->
                Card(
                    onClick = {},
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = line.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = HistorySummary.exerciseLine(line),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            if (record.unparsed.isNotEmpty()) {
                item {
                    ListHeader(
                        modifier = Modifier.transformedHeight(this, spec),
                        transformation = SurfaceTransformation(spec),
                    ) {
                        Text("Not understood", textAlign = TextAlign.Center)
                    }
                }
                items(record.unparsed, key = { it.hashCode() }) { raw ->
                    Card(
                        onClick = {},
                        modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                        transformation = SurfaceTransformation(spec),
                    ) {
                        Text(
                            text = raw,
                            style = MaterialTheme.typography.bodyExtraSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
