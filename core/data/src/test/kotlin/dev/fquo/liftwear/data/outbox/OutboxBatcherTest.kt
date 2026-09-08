package dev.fquo.liftwear.data.outbox

import dev.fquo.liftwear.data.db.OutboxEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OutboxBatcherTest {

    private var nextId = 1L

    private fun set(
        setId: String,
        entryId: String = "e1",
        startTime: Long = 100L,
        payload: String? = """{"reps":5}""",
    ) = OutboxEntity(
        id = nextId++,
        type = OutboxEntity.TYPE_SET,
        workoutStartTime = startTime,
        entryId = entryId,
        setId = setId,
        payloadJson = payload,
        createdAt = 0L,
    )

    private fun finish(startTime: Long = 100L) = OutboxEntity(
        id = nextId++,
        type = OutboxEntity.TYPE_FINISH,
        workoutStartTime = startTime,
        createdAt = 0L,
    )

    @Test
    fun `an empty queue produces no batch`() {
        assertNull(OutboxBatcher.next(emptyList()))
    }

    @Test
    fun `consecutive sets coalesce into one request`() {
        val rows = listOf(set("s1"), set("s2"), set("s3"))
        val batch = OutboxBatcher.next(rows)!!
        assertEquals(OutboxEntity.TYPE_SET, batch.type)
        assertEquals(3, batch.rows.size)
    }

    @Test
    fun `a finish never joins the sets it finishes`() {
        val batch = OutboxBatcher.next(listOf(set("s1"), set("s2"), finish()))!!
        assertEquals(2, batch.rows.size)
        assertEquals(listOf("s1", "s2"), batch.rows.map { it.setId })
    }

    @Test
    fun `finish and discard are always sent alone`() {
        val batch = OutboxBatcher.next(listOf(finish(), finish()))!!
        assertEquals(1, batch.rows.size)
        assertEquals(OutboxEntity.TYPE_FINISH, batch.type)
    }

    @Test
    fun `a batch never spans two workouts`() {
        val batch = OutboxBatcher.next(listOf(set("s1", startTime = 100L), set("s2", startTime = 200L)))!!
        assertEquals(1, batch.rows.size)
        assertEquals(100L, batch.workoutStartTime)
    }

    @Test
    fun `only the leading run is taken, never a scan for compatible rows`() {
        // Sending s3 with s1 would put it ahead of the finish that precedes it, and an
        // update script would then compute from the wrong state.
        val rows = listOf(set("s1"), finish(), set("s3"))
        val batch = OutboxBatcher.next(rows)!!
        assertEquals(listOf("s1"), batch.rows.map { it.setId })
    }

    @Test
    fun `a batch is capped so one failure cannot strand a huge send`() {
        val rows = (1..40).map { set("s$it") }
        assertEquals(OutboxBatcher.MAX_SET_BATCH, OutboxBatcher.next(rows)!!.rows.size)
    }

    @Test
    fun `repeat logs of one set collapse to the latest payload`() {
        // A lifter correcting a set they just logged. Two entries for one setId in a single
        // POST /workout/sets is undefined, so only the newest payload is sent.
        val first = set("s1", payload = """{"reps":5}""")
        val corrected = set("s1", payload = """{"reps":3}""")
        val collapsed = OutboxBatcher.collapse(listOf(first, set("s2"), corrected))
        assertEquals(2, collapsed.size)
        assertEquals("""{"reps":3}""", collapsed.first { it.setId == "s1" }.payloadJson)
    }

    @Test
    fun `collapsing keeps the original position so order still matches performance`() {
        val collapsed = OutboxBatcher.collapse(
            listOf(set("s1", payload = "a"), set("s2"), set("s1", payload = "b"))
        )
        assertEquals(listOf("s1", "s2"), collapsed.map { it.setId })
    }

    @Test
    fun `the same setId under different entries is not collapsed`() {
        val collapsed = OutboxBatcher.collapse(
            listOf(set("s1", entryId = "e1"), set("s1", entryId = "e2"))
        )
        assertEquals(2, collapsed.size)
    }

    @Test
    fun `an un-complete overwrites an earlier completion of the same set`() {
        val collapsed = OutboxBatcher.collapse(
            listOf(set("s1", payload = """{"reps":5}"""), set("s1", payload = null))
        )
        assertEquals(1, collapsed.size)
        assertNull(collapsed.single().payloadJson)
    }

    @Test
    fun `every row in the batch is retired, including collapsed duplicates`() {
        val rows = listOf(set("s1"), set("s1"), set("s2"))
        val batch = OutboxBatcher.next(rows)!!
        // collapse() shrinks what is sent; ids says what gets deleted, and the duplicate
        // must not survive to be sent again.
        assertEquals(3, batch.ids.size)
        assertEquals(2, OutboxBatcher.collapse(batch.rows).size)
    }
}
