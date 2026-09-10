package dev.fquo.liftwear.data.outbox

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.fquo.liftwear.api.EventLog
import dev.fquo.liftwear.api.LiftosaurError
import dev.fquo.liftwear.data.db.LiftWearDatabase
import dev.fquo.liftwear.data.db.OutboxEntity
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The outbox is where training data can get stuck, so its log lines are the ones a bug report
 * leans on hardest: a parked batch has to say which rows and why, loudly.
 */
@RunWith(AndroidJUnit4::class)
class OutboxLoggingTest {

    private lateinit var db: LiftWearDatabase
    private lateinit var api: FakeLiftosaurApi
    private lateinit var drainer: OutboxDrainer
    private val lines = mutableListOf<Pair<EventLog.Level, String>>()

    private val recorder = object : EventLog {
        override fun log(area: String, message: String, level: EventLog.Level, error: Throwable?) {
            synchronized(lines) { lines += level to "$area $message" }
        }
    }

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
            json = Json { ignoreUnknownKeys = true; encodeDefaults = false },
            now = { 5_000L },
            log = recorder,
        )
    }

    @After
    fun tearDown() = db.close()

    private suspend fun queueSet(setId: String) {
        db.outboxDao().insert(
            OutboxEntity(
                type = OutboxEntity.TYPE_SET,
                workoutStartTime = 1_000L,
                entryId = "e1",
                setId = setId,
                payloadJson = """{"reps":5}""",
                createdAt = 0L,
            )
        )
    }

    @Test
    fun a_parked_batch_is_an_error_naming_its_rows_and_the_reason() = runTest {
        queueSet("s1")
        api.failures += LiftosaurError.Conflict.WorkoutMismatch

        assertTrue(drainer.drain() is DrainResult.Parked)

        val errors = lines.filter { it.first == EventLog.Level.Error }.map { it.second }
        assertEquals(1, errors.size)
        assertTrue(errors.single(), errors.single().contains("PARKED") && errors.single().contains("WorkoutMismatch"))
    }

    @Test
    fun a_retry_is_a_warning_and_a_drain_says_how_many_rows_went() = runTest {
        queueSet("s1")
        queueSet("s2")
        api.failures += FakeLiftosaurApi.offline()

        assertTrue(drainer.drain() is DrainResult.Retry)
        assertTrue(lines.toString(), lines.any { it.first == EventLog.Level.Warn && it.second.contains("will retry") })

        assertEquals(DrainResult.Drained(2), drainer.drain())
        assertTrue(lines.toString(), lines.any { it.second.contains("drained 2") })
    }

    @Test
    fun an_empty_queue_writes_nothing() = runTest {
        drainer.drain()
        assertTrue(lines.toString(), lines.isEmpty())
    }
}
