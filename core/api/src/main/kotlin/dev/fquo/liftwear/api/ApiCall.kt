package dev.fquo.liftwear.api

import kotlinx.coroutines.CancellationException
import java.io.IOException

/**
 * Result of one API call, with failures already reduced to a [LiftosaurError].
 *
 * Kotlin's [Result] would work, but it invites `getOrThrow()` at call sites; on a watch
 * mid-workout every failure has to be handled, so the error is not optional to unwrap.
 */
sealed interface ApiResult<out T> {
    data class Ok<T>(val value: T) : ApiResult<T>
    data class Failure(val error: LiftosaurError) : ApiResult<Nothing>

    val errorOrNull: LiftosaurError?
        get() = (this as? Failure)?.error

    fun valueOrNull(): T? = (this as? Ok)?.value
}

inline fun <T, R> ApiResult<T>.map(transform: (T) -> R): ApiResult<R> = when (this) {
    is ApiResult.Ok -> ApiResult.Ok(transform(value))
    is ApiResult.Failure -> this
}

/**
 * Runs an API call, turning anything thrown into a typed error.
 *
 * [LiftosaurException] already carries a mapped error from the interceptor; a bare
 * [IOException] is a dead socket, which the outbox retries rather than surfaces.
 */
suspend fun <T> apiCall(block: suspend () -> T): ApiResult<T> =
    try {
        ApiResult.Ok(block())
    } catch (e: CancellationException) {
        // Never convert cancellation into a user-visible failure - it would leave a
        // stale error banner on a screen the user has already navigated away from.
        throw e
    } catch (e: LiftosaurException) {
        ApiResult.Failure(e.error)
    } catch (e: IOException) {
        ApiResult.Failure(LiftosaurError.Network(e))
    } catch (e: Exception) {
        // Deserialization failures land here. Unknown, not Network: retrying will not help.
        ApiResult.Failure(LiftosaurError.Unknown(status = null, message = e.message))
    }
