package dev.fquo.liftwear.wear.ui.history

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import dev.fquo.liftwear.api.history.WorkoutRecord
import dev.fquo.liftwear.data.history.HistoryPaging
import dev.fquo.liftwear.wear.ui.common.ErrorScreen
import dev.fquo.liftwear.wear.ui.common.LoadingScreen
import dev.fquo.liftwear.wear.ui.common.MessageScreen

/**
 * Past workouts, newest first.
 *
 * Reads the Room cache, so this opens instantly and works with no signal; the refresh runs
 * behind whatever is already on screen. Paging is explicit rather than infinite-scroll: each
 * page is a network round trip, and a list that fetches while a sleeve drags across it would
 * be spending signal the lifter may not have.
 */
@Composable
fun HistoryScreen(viewModel: HistoryViewModel, onOpenRecord: (Long) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    if (state.records.isEmpty()) {
        when {
            state.error != null -> ErrorScreen(state.error!!, onRetry = viewModel::refresh)
            state.firstLoad || state.paging.loading -> LoadingScreen("History")
            else -> MessageScreen("No history", "Finished workouts show up here.")
        }
        return
    }

    HistoryListContent(
        records = state.records,
        paging = state.paging,
        onOpenRecord = onOpenRecord,
        onLoadMore = viewModel::loadMore,
    )
}

@Composable
internal fun HistoryListContent(
    records: List<WorkoutRecord>,
    paging: HistoryPaging,
    onOpenRecord: (Long) -> Unit,
    onLoadMore: () -> Unit,
) {
    val listState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()

    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item {
                ListHeader(
                    modifier = Modifier.transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                ) {
                    Text("History", textAlign = TextAlign.Center)
                }
            }

            items(records, key = { it.id }) { record ->
                HistoryRow(
                    record = record,
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                    onClick = { onOpenRecord(record.id) },
                )
            }

            if (paging.hasMore) {
                item {
                    Button(
                        onClick = onLoadMore,
                        enabled = !paging.loading,
                        modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                        transformation = SurfaceTransformation(spec),
                    ) {
                        Text(
                            text = if (paging.loading) "Loading…" else "Older",
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(
    record: WorkoutRecord,
    modifier: Modifier,
    transformation: SurfaceTransformation,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    // The record id is the workout's start time in unix millis - verified against the date
    // in its own text - so the date needs no parsing and cannot disagree with the id.
    val date = remember(record.id) {
        DateUtils.formatDateTime(
            context,
            record.id,
            DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_ALL or DateUtils.FORMAT_SHOW_WEEKDAY,
        )
    }

    Button(onClick = onClick, modifier = modifier, transformation = transformation) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = date,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Text(
                text = HistorySummary.title(record),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = HistorySummary.subtitle(record),
                style = MaterialTheme.typography.bodyExtraSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}
