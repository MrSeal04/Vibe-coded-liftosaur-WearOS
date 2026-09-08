package dev.fquo.liftwear.data.outbox

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.fquo.liftwear.api.LiftosaurError
import dev.fquo.liftwear.api.dto.WorkoutDto
import dev.fquo.liftwear.data.db.LiftWearDatabase
import dev.fquo.liftwear.data.db.OutboxEntity
import dev.fquo.liftwear.data.db.WorkoutCacheEntity
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented because the ordering guarantees are Room's: an in-memory fake of the DAO
 * would be testing the fake, not the queue that actually holds unsent training data.
 */
@RunWith(AndroidJUnit4::class)
class OutboxDrainerTest {

    private lateinit var db: LiftWearDatabase
    private lateinit var api: FakeLiftosaurApi
    private lateinit var drainer: OutboxDrainer

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val startTime = 1_000L

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            LiftWearDatabase::class.java,
        ).build()
        api = FakeLiftosaurApi()
        drainer = OutboxDrainer(
            api = api,
            outboxDao = db.outboxDao(),
            cacheDao = db.workoutCacheDao(),
            finishResultDao = db.finishResultDao(),
            json = json,
            now = { 5_000L },
        )
    }

    @After
    fun tearDown() = db.close()

    private suspend fun queueSet(setId: String, entryId: String = "e1", reps: Int = 5) {
        db.outboxDao().insert(
            OutboxEntity(
                type = OutboxEntity.TYPE_SET,
                workoutStartTime = startTime,
                entryId = entryId,
                setId = setId,
                payloadJson = """{"reps":$reps}""",
                createdAt = 0L,
            )
        )
    }

    private suspend fun queueFinish() {
        db.outboxDao().insert(
            OutboxEntity(
                type = OutboxEntity.TYPE_FINISH,
                workoutStartTime = startTime,
                payloadJson = """{"startTime":$startTime}""",
                createdAt = 0L,
            )
        )
    }

    @Test
    fun an_empty_queue_is_idle() = runTest {
        assertEquals(DrainResult.Idle, drainer.drain())
    }

    @Test
    fun five_offline_sets_leave_in_one_batch_and_the_queue_empties() = runTest {
        repeat(5) { queueSet("s$it") }

        val result = drainer.drain()

        assertEquals(DrainResult.Drained(5), result)
        assertEquals("five sets should cost one round trip, not five", 1, api.sentSetBatches.size)
        assertEquals(5, api.sentSetBatches.single().size)
        assertTrue(db.outboxDao().pending().isEmpty())
    }

    @Test
    fun sets_are_sent_in_the_order_they_were_logged() = runTest {
        // Out of order, a progression script computes from the wrong state.
        listOf("a", "b", "c").forEach { queueSet(it) }
        drainer.drain()
        assertEquals(listOf("a", "b", "c"), api.sentSetBatches.single().map { it.setId })
    }

    @Test
    fun a_finish_is_sent_after_the_sets_it_finishes() = runTest {
        queueSet("s1")
        queueFinish()

        assertEquals(DrainResult.Drained(2), drainer.drain())
        assertEquals(1, api.sentSetBatches.size)
        assertEquals(1, api.finishes.size)
    }

    @Test
    fun nothing_is_dropped_when_the_network_is_down() = runTest {
        repeat(3) { queueSet("s$it") }
        api.failures += FakeLiftosaurApi.offline()

        val result = drainer.drain()

        assertTrue(result is DrainResult.Retry)
        assertEquals("the sets must still be there to send later", 3, db.outboxDao().pending().size)
        assertEquals(1, db.outboxDao().pending().first().attempts)
    }

    @Test
    fun a_queue_survives_the_process_dying_between_drains() = runTest {
        repeat(4) { queueSet("s$it") }
        api.failures += FakeLiftosaurApi.offline()
        drainer.drain()

        // Same database, a brand new drainer - as after a force-stop and relaunch.
        val revived = OutboxDrainer(api, db.outboxDao(), db.workoutCacheDao(), db.finishResultDao(), json)
        assertEquals(DrainResult.Drained(4), revived.drain())
        assertEquals(4, api.sentSetBatches.single().size)
    }

    @Test
    fun a_replayed_drain_does_not_send_a_set_twice() = runTest {
        queueSet("s1")
        drainer.drain()
        drainer.drain()
        assertEquals(1, api.sentSetBatches.size)
    }

    @Test
    fun the_server_copy_of_the_workout_replaces_the_cache() = runTest {
        // Completing a set can rewrite later sets' weights through an update script, and
        // only the server can compute that - so its answer wins outright.
        api.workoutToReturn = WorkoutDto(startTime = startTime, dayName = "Recomputed")
        queueSet("s1")

        drainer.drain()

        val cached = db.workoutCacheDao().get()
        assertNotNull(cached?.workoutJson)
        assertTrue(cached!!.workoutJson!!.contains("Recomputed"))
    }

    @Test
    fun premium_lapsing_parks_the_queue_instead_of_retrying_forever() = runTest {
        repeat(2) { queueSet("s$it") }
        api.failures += LiftosaurError.PremiumRequired

        val result = drainer.drain()

        assertTrue(result is DrainResult.Parked)
        assertTrue("parked rows must leave the pending queue", db.outboxDao().pending().isEmpty())
        assertEquals("but must not be deleted", 2, countParked())
    }

    @Test
    fun a_workout_mismatch_parks_for_the_user_to_resolve() = runTest {
        queueSet("s1")
        api.failures += LiftosaurError.Conflict.WorkoutMismatch
        assertTrue(drainer.drain() is DrainResult.Parked)
        assertEquals(1, countParked())
    }

    @Test
    fun missing_set_input_parks_rather_than_looping() = runTest {
        // No retry can fix a set the server says is missing required input.
        queueSet("s1")
        api.failures += LiftosaurError.MissingSetInput("needs reps")
        assertTrue(drainer.drain() is DrainResult.Parked)
    }

    @Test
    fun already_active_resyncs_and_retries_when_it_is_the_same_workout() = runTest {
        // The realistic collision: a session started in the phone app that turns out to be
        // the very workout the watch is queueing for.
        queueSet("s1")
        api.failures += LiftosaurError.Conflict.WorkoutAlreadyActive
        api.serverCurrentWorkout = WorkoutDto(startTime = startTime, dayName = "Same session")

        val result = drainer.drain()

        assertTrue(result is DrainResult.Retry)
        assertEquals(1, api.currentWorkoutCalls)
        assertEquals("nothing may be dropped", 1, db.outboxDao().pending().size)
        assertTrue(db.workoutCacheDao().get()!!.workoutJson!!.contains("Same session"))
    }

    @Test
    fun already_active_parks_when_the_server_is_running_a_different_workout() = runTest {
        queueSet("s1")
        api.failures += LiftosaurError.Conflict.WorkoutAlreadyActive
        api.serverCurrentWorkout = WorkoutDto(startTime = 9_999L)

        assertTrue(drainer.drain() is DrainResult.Parked)
        assertEquals(1, countParked())
    }

    @Test
    fun unparking_lets_the_queue_go_again_with_its_payloads_intact() = runTest {
        queueSet("s1", reps = 7)
        api.failures += LiftosaurError.Conflict.WorkoutMismatch
        drainer.drain()

        db.outboxDao().unparkAll()
        assertEquals(DrainResult.Drained(1), drainer.drain())
        assertEquals(7, api.sentSetBatches.single().single().completed?.reps)
    }

    @Test
    fun a_successful_finish_keeps_the_next_scheduled_day() = runTest {
        // The progression runs server-side and the screen that asked has usually gone by
        // the time the answer arrives, so it has to be stored rather than returned.
        queueFinish()
        drainer.drain()
        assertEquals("Day B", db.finishResultDao().observeOnce()?.nextDayName)
    }

    @Test
    fun a_queued_discard_reaches_the_server_with_the_right_start_time() = runTest {
        db.outboxDao().insert(
            OutboxEntity(
                type = OutboxEntity.TYPE_DISCARD,
                workoutStartTime = startTime,
                createdAt = 0L,
            )
        )
        drainer.drain()
        assertEquals(startTime, api.discards.single().startTime)
    }

    @Test
    fun a_partial_failure_keeps_everything_after_the_failing_batch() = runTest {
        queueSet("s1")
        queueFinish()
        // The sets go through; the finish does not.
        api.failures += null
        api.failures += FakeLiftosaurApi.offline()

        val result = drainer.drain()

        assertTrue(result is DrainResult.Retry)
        assertEquals(1, api.sentSetBatches.size)
        assertEquals("the finish must still be queued", 1, db.outboxDao().pending().size)
        assertEquals(OutboxEntity.TYPE_FINISH, db.outboxDao().pending().single().type)
    }

    private suspend fun countParked(): Int =
        db.outboxDao().observeAllOnce().count { it.status == OutboxEntity.STATUS_PARKED }
}
