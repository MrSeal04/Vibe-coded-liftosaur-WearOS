package dev.fquo.liftwear.api

import dev.fquo.liftwear.api.dto.ErrorDetailDto
import java.io.IOException

/**
 * Typed failures from the Liftosaur v1 API.
 *
 * These are kept distinct because the watch needs a different screen for each:
 * "Premium required" and "no signal" must never both render as "something went wrong".
 */
sealed class LiftosaurError {

    /** 401 - key missing, malformed, or revoked. The user must re-pair from the phone. */
    data object InvalidApiKey : LiftosaurError()

    /** 403 - key is valid but the account has no active Premium subscription. */
    data object PremiumRequired : LiftosaurError()

    /** 400 `missing_set_input` - the set needed reps/weight/RPE that we did not send. */
    data class MissingSetInput(val message: String?) : LiftosaurError()

    /** 400 - any other rejected input. */
    data class BadRequest(val code: String?, val message: String?) : LiftosaurError()

    data class NotFound(val message: String?) : LiftosaurError()

    /**
     * 409. The four subtypes need different recovery, so they are modelled separately
     * rather than as one "conflict" bucket - see the outbox rules in the plan.
     */
    sealed class Conflict : LiftosaurError() {
        /** A workout is already live (commonly: started in the phone app). Re-sync onto it. */
        data object WorkoutAlreadyActive : Conflict()

        /** That startTime is taken. Retry with a fresh one. */
        data object StartTimeTaken : Conflict()

        /** Our cached workout disagrees with the server's. Park and resolve on the phone. */
        data object WorkoutMismatch : Conflict()

        /** The entryId was ambiguous. Park and resolve on the phone. */
        data object AmbiguousEntry : Conflict()

        data class Other(val code: String?, val message: String?) : Conflict()
    }

    /** 422 - Liftoscript parse or runtime failure. */
    data class ScriptError(val message: String?, val details: List<ErrorDetailDto>) : LiftosaurError()

    /** 5xx. */
    data class Server(val status: Int, val message: String?) : LiftosaurError()

    /** No usable connection. The outbox should hold and retry rather than surface this. */
    data class Network(val cause: Throwable) : LiftosaurError()

    /** Anything unrecognised - including a response body we could not parse. */
    data class Unknown(val status: Int?, val message: String?) : LiftosaurError()

    /** True when retrying the identical request later could plausibly succeed. */
    val isRetryable: Boolean
        get() = when (this) {
            is Network, is Server -> true
            is Conflict.StartTimeTaken -> true
            else -> false
        }

    /**
     * True when the outbox must stop and ask the user, rather than keep retrying.
     * Retrying these forever would silently strand a workout's worth of sets.
     */
    val requiresUserAction: Boolean
        get() = when (this) {
            InvalidApiKey, PremiumRequired -> true
            is Conflict.WorkoutMismatch, is Conflict.AmbiguousEntry -> true
            is MissingSetInput -> true
            else -> false
        }
}

/** Carries a [LiftosaurError] out through Retrofit, which propagates IOException from suspend calls. */
class LiftosaurException(val error: LiftosaurError) : IOException(error.toString())
