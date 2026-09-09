package dev.fquo.liftwear.data.history

import dev.fquo.liftwear.api.ApiResult
import dev.fquo.liftwear.api.LiftosaurApi
import dev.fquo.liftwear.api.apiCall
import dev.fquo.liftwear.api.history.WorkoutRecord
import dev.fquo.liftwear.api.history.WorkoutTextParser
import dev.fquo.liftwear.data.db.HistoryRecordEntity
import dev.fquo.liftwear.data.db.LiftWearDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/** Where the next page starts, and whether there is one. */
data class HistoryPaging(
    val cursor: Long? = null,
    val hasMore: Boolean = true,
    val loading: Boolean = false,
)

/**
 * Past workouts.
 *
 * Reads come from Room so history is browsable with no signal - the same rule the live
 * workout follows, for the same reason: this is an app used in basements.
 *
 * The API returns Liftoscript *text*, not structured JSON, so every record is run through
 * [WorkoutTextParser] on the way out. Parsing happens off the main thread and on read
 * rather than on write, because a format the parser learns to handle better should improve
 * what is already cached rather than requiring a re-fetch.
 */
class HistoryRepository(
    private val api: LiftosaurApi,
    private val db: LiftWearDatabase,
    private val now: () -> Long = System::currentTimeMillis,
) {

    private val dao = db.historyDao()

    private val _paging = MutableStateFlow(HistoryPaging())
    val paging: StateFlow<HistoryPaging> = _paging.asStateFlow()

    val records: Flow<List<WorkoutRecord>> = dao.observe(CACHE_LIMIT)
        .map { rows -> rows.map { WorkoutTextParser.parse(it.id, it.text) } }
        .flowOn(Dispatchers.Default)

    suspend fun record(id: Long): WorkoutRecord? =
        dao.get(id)?.let { WorkoutTextParser.parse(it.id, it.text) }

    /**
     * Re-reads the newest page.
     *
     * Merges rather than replaces. A failed refresh must leave the cache exactly as it was:
     * the alternative is a lifter opening History on the underground with signal for one
     * request and losing everything they could previously read offline.
     */
    suspend fun refresh(): ApiResult<Unit> {
        _paging.value = _paging.value.copy(loading = true)
        val result = fetch(cursor = null)
        _paging.value = when (result) {
            is ApiResult.Ok -> HistoryPaging(
                cursor = result.value.cursor,
                hasMore = result.value.hasMore,
                loading = false,
            )
            is ApiResult.Failure -> _paging.value.copy(loading = false)
        }
        return result.map { }
    }

    /** The next page down. No-op once the server says there is nothing more. */
    suspend fun loadMore(): ApiResult<Unit> {
        val paging = _paging.value
        if (paging.loading || !paging.hasMore) return ApiResult.Ok(Unit)
        _paging.value = paging.copy(loading = true)
        val result = fetch(cursor = paging.cursor)
        _paging.value = when (result) {
            is ApiResult.Ok -> paging.copy(
                // A page that comes back empty ends the walk even if the server still says
                // hasMore, so a cursor that stops advancing cannot spin forever.
                cursor = result.value.cursor ?: paging.cursor,
                hasMore = result.value.hasMore && result.value.count > 0,
                loading = false,
            )
            is ApiResult.Failure -> paging.copy(loading = false)
        }
        return result.map { }
    }

    private data class Page(val count: Int, val cursor: Long?, val hasMore: Boolean)

    private suspend fun fetch(cursor: Long?): ApiResult<Page> =
        apiCall { api.getHistory(limit = PAGE_SIZE, cursor = cursor).data }
            .also { result ->
                if (result is ApiResult.Ok) {
                    val fetchedAt = now()
                    dao.putAll(
                        result.value.records.map {
                            HistoryRecordEntity(id = it.id, text = it.text, cachedAt = fetchedAt)
                        }
                    )
                    dao.trimTo(CACHE_LIMIT)
                }
            }
            .map { Page(it.records.size, it.nextCursor, it.hasMore) }

    suspend fun clear() = dao.clear()

    companion object {
        const val PAGE_SIZE = 20

        /** Deep enough to scroll through a training block, shallow enough to stay small. */
        const val CACHE_LIMIT = 100
    }
}

private inline fun <T, R> ApiResult<T>.map(transform: (T) -> R): ApiResult<R> = when (this) {
    is ApiResult.Ok -> ApiResult.Ok(transform(value))
    is ApiResult.Failure -> this
}
