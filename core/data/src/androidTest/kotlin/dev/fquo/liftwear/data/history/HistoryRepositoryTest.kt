package dev.fquo.liftwear.data.history

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.fquo.liftwear.api.ApiResult
import dev.fquo.liftwear.api.dto.HistoryRecordDto
import dev.fquo.liftwear.data.db.LiftWearDatabase
import dev.fquo.liftwear.data.outbox.FakeLiftosaurApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * History has to be readable in a basement, which means every one of these assertions is
 * about what survives when the network does not.
 */
@RunWith(AndroidJUnit4::class)
class HistoryRepositoryTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private lateinit var db: LiftWearDatabase
    private lateinit var api: FakeLiftosaurApi
    private lateinit var repo: HistoryRepository

    /** Ids are unix millis, newest first - the shape the real API returns. */
    private fun record(id: Long, day: String) = HistoryRecordDto(
        id = id,
        text = """
            2026-01-02 09:15:00 +00:00 / program: "P" / dayName: "$day" / week: 1 / duration: 600s / exercises: {
              Squat / 3x5 100lb / target: 3x5 100lb 180s
            }
        """.trimIndent(),
    )

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, LiftWearDatabase::class.java).build()
        api = FakeLiftosaurApi()
        repo = HistoryRepository(api, db, now = { 5_000L })
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun records_are_parsed_out_of_the_cache_newest_first() = runTest {
        api.historyRecords = listOf(record(3_000L, "C"), record(1_000L, "A"), record(2_000L, "B"))
        repo.refresh()

        val records = repo.records.first()
        assertEquals(listOf(3_000L, 2_000L, 1_000L), records.map { it.id })
        assertEquals(listOf("C", "B", "A"), records.map { it.dayName })
        assertEquals("Squat", records.first().exercises.single().name)
    }

    @Test
    fun paging_walks_down_with_the_cursor_and_stops() = runTest {
        api.historyRecords = (1..45).map { record(it * 1_000L, "Day $it") }

        repo.refresh()
        assertEquals(HistoryRepository.PAGE_SIZE, repo.records.first().size)
        assertTrue(repo.paging.value.hasMore)

        repo.loadMore()
        assertEquals(HistoryRepository.PAGE_SIZE * 2, repo.records.first().size)

        repo.loadMore()
        assertEquals(45, repo.records.first().size)
        assertFalse(repo.paging.value.hasMore)

        // Every request after the first carried the previous page's last id.
        assertEquals(listOf(null, 26_000L, 6_000L), api.historyRequests.map { it.second })
    }

    @Test
    fun asking_for_more_once_the_server_says_there_is_none_is_a_no_op() = runTest {
        api.historyRecords = listOf(record(1_000L, "A"))
        repo.refresh()
        val before = api.historyRequests.size

        repo.loadMore()
        repo.loadMore()

        assertEquals(before, api.historyRequests.size)
    }

    /**
     * The failure this design exists for: signal for exactly one request, then nothing. The
     * cache has to come back untouched, not empty.
     */
    @Test
    fun a_failed_refresh_leaves_the_cache_exactly_as_it_was() = runTest {
        api.historyRecords = listOf(record(2_000L, "B"), record(1_000L, "A"))
        repo.refresh()
        assertEquals(2, repo.records.first().size)

        api.failures += FakeLiftosaurApi.offline()
        val result = repo.refresh()

        assertTrue(result is ApiResult.Failure)
        assertEquals(listOf(2_000L, 1_000L), repo.records.first().map { it.id })
        assertFalse(repo.paging.value.loading)
    }

    @Test
    fun history_read_from_a_cold_process_needs_no_network_at_all() = runTest {
        api.historyRecords = listOf(record(1_000L, "A"))
        repo.refresh()
        val requestsBefore = api.historyRequests.size

        // A new repository over the same database, with the API primed to blow up if used.
        val reborn = HistoryRepository(api, db)
        assertEquals("A", reborn.records.first().single().dayName)
        assertNotNull(reborn.record(1_000L))
        assertEquals(requestsBefore, api.historyRequests.size)
    }

    /**
     * Unbounded on a watch would mean years of training text nobody scrolls back to. The
     * cap is enforced on write; anything evicted is one paged request away.
     */
    @Test
    fun the_cache_is_capped_at_the_newest_records() = runTest {
        api.historyRecords = (1..HistoryRepository.CACHE_LIMIT + 30).map { record(it * 1_000L, "D$it") }
        repeat(10) { repo.loadMore().let { } ; repo.refresh() }

        assertTrue(db.historyDao().count() <= HistoryRepository.CACHE_LIMIT)
        // Newest survived.
        assertNotNull(db.historyDao().get((HistoryRepository.CACHE_LIMIT + 30) * 1_000L))
    }
}
