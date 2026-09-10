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
import dev.fquo.liftwear.data.outbox.FakeLiftosaurApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The app reads the workout *shared* from the container's scope; [WorkoutRepositoryTest]
 * reads it cold. Sharing is a performance measure, so it has to cost nothing in correctness:
 * a shared flow still follows every write, and a second reader is handed the decode the first
 * one already paid for instead of running its own.
 */
@RunWith(AndroidJUnit4::class)
class WorkoutRepositorySharingTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val startTime = 2_000L

    private lateinit var db: LiftWearDatabase
    private lateinit var scope: CoroutineScope
    private lateinit var repo: WorkoutRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, LiftWearDatabase::class.java).build()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val api = FakeLiftosaurApi().apply {
            workoutToReturn = WorkoutDto(
                startTime = startTime,
                dayName = "Day A",
                entries = listOf(
                    EntryDto(entryId = "e1", name = "Squat", sets = listOf(SetDto(setId = "s1", reps = 5))),
                ),
            )
        }
        repo = WorkoutRepository(
            context = context,
            api = api,
            db = db,
            json = json,
            now = { 7_000L },
            scheduleDrain = {},
            sharingScope = scope,
        )
    }

    @After
    fun tearDown() {
        // The shared upstream is still querying; it has to stop before its database closes.
        scope.cancel()
        db.close()
    }

    @Test
    fun a_shared_workout_follows_a_logged_set() = runTest {
        assertTrue(repo.start(startTime = startTime) is ApiResult.Ok)

        repo.logSet("e1", "s1", CompletedDto(reps = 7))

        val seen = repo.workout.first { it?.entries?.single()?.sets?.single()?.completed != null }
        assertEquals(7, seen!!.entries[0].sets[0].completed?.reps)
    }

    @Test
    fun a_second_reader_gets_the_same_decode_rather_than_its_own() = runTest {
        assertTrue(repo.start(startTime = startTime) is ApiResult.Ok)

        val first = repo.workout.first { it != null }
        assertNotNull(first)
        // A cold flow would query and decode again, producing an equal but distinct object.
        assertSame(first, repo.workout.first())
    }
}
