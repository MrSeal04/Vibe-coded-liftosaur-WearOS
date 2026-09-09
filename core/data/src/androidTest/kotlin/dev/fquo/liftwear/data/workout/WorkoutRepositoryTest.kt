package dev.fquo.liftwear.data.workout

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.fquo.liftwear.api.ApiResult
import dev.fquo.liftwear.api.dto.CompletedDto
import dev.fquo.liftwear.api.dto.EntryDto
import dev.fquo.liftwear.api.dto.SetDto
import dev.fquo.liftwear.api.dto.WorkoutDto
import dev.fquo.liftwear.data.db.LiftWearDatabase
import dev.fquo.liftwear.data.db.OutboxEntity
import dev.fquo.liftwear.data.outbox.DrainResult
import dev.fquo.liftwear.data.outbox.FakeLiftosaurApi
import dev.fquo.liftwear.data.outbox.OutboxDrainer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The promise this class makes: a tap never waits on the network, and nothing logged is
 * ever lost. Both only mean anything against a real database.
 */
@RunWith(AndroidJUnit4::class)
class WorkoutRepositoryTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val startTime = 2_000L

    private lateinit var db: LiftWearDatabase
    private lateinit var api: FakeLiftosaurApi
    private lateinit var repo: WorkoutRepository
    private var drainsRequested = 0

    private val workout = WorkoutDto(
        startTime = startTime,
        dayName = "Day A",
        entries = listOf(
            EntryDto(
                entryId = "e1",
                name = "Squat",
                sets = listOf(SetDto(setId = "s1", reps = 5), SetDto(setId = "s2", reps = 5)),
            )
        ),
    )

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, LiftWearDatabase::class.java).build()
        api = FakeLiftosaurApi()
        api.workoutToReturn = workout
        drainsRequested = 0
        repo = WorkoutRepository(
            context = context,
            api = api,
            db = db,
            json = json,
            now = { 7_000L },
            // WorkManager is not the thing under test; that it is *asked* to drain is.
            scheduleDrain = { drainsRequested++ },
        )
    }

    @After
    fun tearDown() = db.close()

    private suspend fun startWorkout() {
        assertTrue(repo.start(startTime = startTime) is ApiResult.Ok)
    }

    @Test
    fun a_logged_set_shows_immediately_and_is_queued() = runTest {
        startWorkout()

        val result = repo.logSet("e1", "s1", CompletedDto(reps = 5))

        assertTrue(result is ApiResult.Ok)
        // On screen at once...
        assertEquals(5, repo.workout.first()!!.entries[0].sets[0].completed?.reps)
        // ...and durably queued, with a drain asked for.
        assertEquals(1, db.outboxDao().pending().size)
        assertEquals(1, drainsRequested)
        assertEquals("nothing may have been sent yet", 0, api.sentSetBatches.size)
    }

    @Test
    fun logging_offline_costs_no_network_call_at_all() = runTest {
        startWorkout()
        // Every send this fake could make would throw; the repository must not make one.
        api.failures += FakeLiftosaurApi.offline()

        repeat(2) { i -> assertTrue(repo.logSet("e1", "s$i", CompletedDto(reps = 5)) is ApiResult.Ok) }

        assertEquals(2, db.outboxDao().pending().size)
    }

    @Test
    fun five_sets_logged_offline_survive_and_land_exactly_once() = runTest {
        startWorkout()
        val sets = listOf("s1", "s2", "s1", "s2", "s1")
        sets.forEachIndexed { i, id -> repo.logSet("e1", id, CompletedDto(reps = 5 + i)) }

        // A fresh drainer on the same database, as after a force-stop.
        val drainer = OutboxDrainer(api, db.outboxDao(), db.workoutCacheDao(), db.finishResultDao(), json)
        assertEquals(DrainResult.Drained(5), drainer.drain())

        assertEquals(1, api.sentSetBatches.size)
        val sent = api.sentSetBatches.single()
        assertEquals("repeat logs of one set collapse", 2, sent.size)
        // The last value logged for each set is the one that reaches the server.
        assertEquals(9, sent.first { it.setId == "s1" }.completed?.reps)
        assertEquals(8, sent.first { it.setId == "s2" }.completed?.reps)
        assertTrue(db.outboxDao().pending().isEmpty())
    }

    @Test
    fun un_completing_a_set_clears_it_on_screen_and_queues_a_null() = runTest {
        startWorkout()
        repo.logSet("e1", "s1", CompletedDto(reps = 5))
        repo.logSet("e1", "s1", null)

        assertNull(repo.workout.first()!!.entries[0].sets[0].completed)
        assertNull(db.outboxDao().pending().last().payloadJson)
    }

    @Test
    fun a_refresh_never_overwrites_unsent_sets() = runTest {
        // This is the one way the outbox could silently lose data: a reconcile that wins.
        startWorkout()
        repo.logSet("e1", "s1", CompletedDto(reps = 5))
        api.serverCurrentWorkout = WorkoutDto(startTime = startTime, dayName = "Server copy")

        repo.refreshCurrent()

        assertEquals("Day A", repo.workout.first()!!.dayName)
        assertEquals(5, repo.workout.first()!!.entries[0].sets[0].completed?.reps)
        assertEquals("it should ask for a drain instead", 2, drainsRequested)
    }

    @Test
    fun a_refresh_does_reconcile_once_the_queue_is_empty() = runTest {
        startWorkout()
        api.serverCurrentWorkout = WorkoutDto(startTime = startTime, dayName = "Server copy")
        repo.refreshCurrent()
        assertEquals("Server copy", repo.workout.first()!!.dayName)
    }

    @Test
    fun finishing_closes_the_workout_locally_without_waiting_for_the_server() = runTest {
        startWorkout()
        repo.logSet("e1", "s1", CompletedDto(reps = 5))

        assertTrue(repo.finish() is ApiResult.Ok)

        assertNull("the workout is over as far as the watch is concerned", repo.workout.first())
        assertEquals(0, api.finishes.size)
        val queued = db.outboxDao().pending()
        assertEquals(OutboxEntity.TYPE_SET, queued.first().type)
        assertEquals("the finish must not overtake the sets", OutboxEntity.TYPE_FINISH, queued.last().type)
    }

    @Test
    fun sync_state_tracks_what_is_waiting() = runTest {
        startWorkout()
        assertTrue(repo.sync.first().isSynced)

        repo.logSet("e1", "s1", CompletedDto(reps = 5))
        assertEquals(1, repo.sync.first().pending)
        assertFalse(repo.sync.first().isSynced)
    }

    @Test
    fun discarding_unsent_work_is_the_only_thing_that_loses_it() = runTest {
        startWorkout()
        repo.logSet("e1", "s1", CompletedDto(reps = 5))

        repo.abandonQueued()

        assertTrue(db.outboxDao().pending().isEmpty())
        assertNull(repo.workout.first())
    }

    @Test
    fun logging_with_no_workout_fails_rather_than_queueing_an_orphan() = runTest {
        assertTrue(repo.logSet("e1", "s1", CompletedDto(reps = 5)) is ApiResult.Failure)
        assertTrue(db.outboxDao().pending().isEmpty())
    }

    @Test
    fun the_real_on_disk_database_opens_and_round_trips() = runTest {
        // Exercises LiftWearDatabase.create - the builder the app actually uses, including
        // its deliberate absence of destructive migration.
        context.deleteDatabase("liftwear.db")
        val onDisk = LiftWearDatabase.create(context)
        try {
            onDisk.outboxDao().insert(
                OutboxEntity(
                    type = OutboxEntity.TYPE_SET,
                    workoutStartTime = 1L,
                    entryId = "e1",
                    setId = "s1",
                    createdAt = 0L,
                )
            )
            assertNotNull(onDisk.outboxDao().pending().singleOrNull())
        } finally {
            onDisk.close()
            context.deleteDatabase("liftwear.db")
        }
    }

    // --- the persisted preview (Phase 7) ---

    /**
     * The Tile is asked for a layout by a system process, on a schedule nobody controls,
     * usually with this app dead. Before the preview was persisted it lived in a
     * MutableStateFlow, so that request could only ever have found it empty.
     */
    @Test
    fun the_preview_survives_the_process_that_fetched_it() = runTest {
        api.nextWorkoutToReturn = workout.copy(dayName = "Day B")
        repo.refreshPreview()

        // A second repository on the same database is what a Tile request gets: a fresh
        // object graph in a process that was started for this and nothing else.
        val reborn = WorkoutRepository(context, api, db, json, now = { 9_000L }, scheduleDrain = {})
        assertEquals("Day B", reborn.currentPreview()?.dayName)
    }

    @Test
    fun the_live_workout_and_the_preview_do_not_overwrite_each_other() = runTest {
        api.nextWorkoutToReturn = workout.copy(dayName = "Day B", startTime = 0L)
        repo.refreshPreview()
        repo.start()

        assertEquals("Day A", repo.current()?.dayName)
        assertEquals("Day B", repo.currentPreview()?.dayName)
    }

    /**
     * Throwing away unsent work is destructive by design, but only to unsent work. Blanking
     * the preview as well would leave the Tile saying "Open to load today's workout" until
     * the user next opened the app - punishing them twice for one failed sync.
     */
    @Test
    fun abandoning_queued_writes_leaves_the_preview_alone() = runTest {
        api.nextWorkoutToReturn = workout.copy(dayName = "Day B", startTime = 0L)
        repo.refreshPreview()
        repo.start()
        repo.logSet("e1", "s1", CompletedDto(reps = 5))

        repo.abandonQueued()

        assertNull(repo.current())
        assertEquals("Day B", repo.currentPreview()?.dayName)
    }
}
