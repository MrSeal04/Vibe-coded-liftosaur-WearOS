package dev.fquo.liftwear.data.outbox

import dev.fquo.liftwear.data.db.OutboxEntity

/**
 * The next unit of work to send, and the rows it retires.
 *
 * Batches never span workouts or types: a FINISH must not overtake the sets it finishes,
 * and there is no batch endpoint that mixes them.
 */
data class OutboxBatch(
    val type: String,
    val workoutStartTime: Long,
    val rows: List<OutboxEntity>,
) {
    val ids: List<Long> get() = rows.map { it.id }
}

object OutboxBatcher {

    /** The API accepts a list; keep it modest so one failure does not strand a huge send. */
    const val MAX_SET_BATCH = 25

    /**
     * Takes the leading run of pending rows that can go in one request.
     *
     * Only the *leading* run, never a scan for compatible rows further down: ordering per
     * workout is strict, because completing a set can rewrite later sets through an update
     * script, and sending them out of order would compute from the wrong state.
     */
    fun next(pending: List<OutboxEntity>): OutboxBatch? {
        val head = pending.firstOrNull() ?: return null
        if (head.type != OutboxEntity.TYPE_SET) {
            // FINISH and DISCARD are always sent alone.
            return OutboxBatch(head.type, head.workoutStartTime, listOf(head))
        }
        val run = pending
            .asSequence()
            .takeWhile { it.type == OutboxEntity.TYPE_SET && it.workoutStartTime == head.workoutStartTime }
            .take(MAX_SET_BATCH)
            .toList()
        return OutboxBatch(OutboxEntity.TYPE_SET, head.workoutStartTime, run)
    }

    /**
     * Collapses repeat logs of the same set, keeping the last.
     *
     * A lifter correcting a set they just logged produces two rows for one setId. The API is
     * last-writer-wins by resource identity, but sending both in a single `POST /workout/sets`
     * would put two entries for the same set in one request, which nothing defines. Both rows
     * are still retired - only the payload is deduplicated.
     */
    fun collapse(rows: List<OutboxEntity>): List<OutboxEntity> {
        val lastByTarget = LinkedHashMap<Pair<String?, String?>, OutboxEntity>()
        for (row in rows) {
            val target = row.entryId to row.setId
            // Re-put so the newest payload wins but the original position is kept: order
            // still matters to the server's progression scripts.
            lastByTarget[target] = row
        }
        return lastByTarget.values.toList()
    }
}
