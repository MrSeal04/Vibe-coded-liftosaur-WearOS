package dev.fquo.liftwear.data.outbox

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.fquo.liftwear.api.EventLog
import java.util.concurrent.TimeUnit

/**
 * Implemented by the Application so the worker can reach the drainer.
 *
 * WorkManager constructs workers itself, and a custom WorkerFactory would be more
 * machinery than four objects justify.
 */
interface OutboxHost {
    val outboxDrainer: OutboxDrainer

    /** Where a worker run is written down. A host that keeps no debug log leaves this alone. */
    val eventLog: EventLog get() = EventLog.None
}

class OutboxWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val host = applicationContext as? OutboxHost ?: return Result.failure()
        val drained = host.outboxDrainer.drain()

        // An idle first attempt is the common case - the extra workers a burst of sets appends
        // find the queue already empty - and says nothing. A retry, or real work, does: the
        // attempt number is how WorkManager's backoff shows up in a bug report.
        if (drained != DrainResult.Idle || runAttemptCount > 0) {
            host.eventLog.log("Outbox", "worker attempt ${runAttemptCount + 1}: $drained")
        }

        return when (drained) {
            DrainResult.Idle, is DrainResult.Drained -> Result.success()

            // Transient: let WorkManager's backoff decide when to come back.
            is DrainResult.Retry -> Result.retry()

            // Parked rows need the user. Retrying on a timer would burn battery and never
            // succeed, and the rows are safe in the database until they are unparked.
            is DrainResult.Parked -> Result.success()
        }
    }
}

object OutboxScheduler {

    const val UNIQUE_WORK = "liftwear-outbox"

    /**
     * Asks for a drain.
     *
     * APPEND_OR_REPLACE keeps one chain: writes are ordered, so two drains must never run
     * concurrently and race to send the same rows. A drain empties the whole queue, so the
     * extra workers a burst of sets appends mostly find nothing and finish immediately -
     * which is the point, because it guarantees a drain after the *last* set.
     */
    fun enqueue(context: Context) {
        val request = OneTimeWorkRequestBuilder<OutboxWorker>()
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context.applicationContext)
            .beginUniqueWork(UNIQUE_WORK, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
            .enqueue()
    }
}
