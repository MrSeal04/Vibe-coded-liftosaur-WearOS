package dev.fquo.liftwear.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * The last known live workout, as the raw envelope the API returned.
 *
 * Stored as JSON rather than normalised into tables on purpose: every write response
 * returns the whole workout, and an update script can rewrite later sets' weights, so the
 * server's payload is always authoritative in its entirety. Normalising it would only
 * create opportunities to merge it wrongly.
 */
@Entity(tableName = "workout_cache")
data class WorkoutCacheEntity(
    @PrimaryKey val id: Int = SINGLE_ROW,
    /** Null means "no workout is running", which is different from "we have not looked". */
    val workoutJson: String?,
    val startTime: Long?,
    val fetchedAt: Long,
    /** True once a FINISH or DISCARD is queued, so the UI stops offering to log more sets. */
    val closed: Boolean = false,
) {
    companion object { const val SINGLE_ROW = 0 }
}

/**
 * One pending write.
 *
 * There is deliberately no START type. `GET /workout/next` reissues its setIds on every
 * call, so a queued start would produce ids that refer to nothing and no set could be
 * logged against it - starting requires a connection, and everything after it does not.
 * See docs/api-findings.md.
 */
@Entity(
    tableName = "outbox",
    indices = [Index("status"), Index("workoutStartTime")],
)
data class OutboxEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    /** Groups rows to a workout, and is what FINISH and DISCARD send. */
    val workoutStartTime: Long,
    val entryId: String? = null,
    val setId: String? = null,
    /** Serialized [dev.fquo.liftwear.api.dto.CompletedDto], or null to un-complete a set. */
    val payloadJson: String? = null,
    val createdAt: Long,
    val attempts: Int = 0,
    val lastError: String? = null,
    val status: String = STATUS_PENDING,
) {
    companion object {
        const val TYPE_SET = "SET"
        const val TYPE_FINISH = "FINISH"
        const val TYPE_DISCARD = "DISCARD"

        const val STATUS_PENDING = "PENDING"

        /** Needs the user: retrying forever would silently strand a workout's worth of sets. */
        const val STATUS_PARKED = "PARKED"
    }
}

/** Past workouts, kept so history is readable with no signal. */
@Entity(tableName = "history_cache")
data class HistoryRecordEntity(
    /** Unix millis; the API uses it as the record's identity. */
    @PrimaryKey val id: Long,
    val text: String,
    val cachedAt: Long,
)

/**
 * What the server said when a FINISH finally went through.
 *
 * The API runs progressions on finish and returns the next scheduled day. Queuing the
 * finish means that answer can arrive minutes later, so it is kept here rather than lost.
 */
@Entity(tableName = "finish_result")
data class FinishResultEntity(
    @PrimaryKey val id: Int = SINGLE_ROW,
    val workoutStartTime: Long,
    val nextDayName: String?,
    val finishedAt: Long,
) {
    companion object { const val SINGLE_ROW = 0 }
}
