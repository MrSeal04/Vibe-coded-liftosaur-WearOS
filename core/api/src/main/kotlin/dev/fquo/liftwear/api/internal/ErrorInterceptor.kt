package dev.fquo.liftwear.api.internal

import dev.fquo.liftwear.api.LiftosaurError
import dev.fquo.liftwear.api.LiftosaurException
import dev.fquo.liftwear.api.dto.ErrorEnvelope
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * Converts non-2xx responses into typed [LiftosaurError]s.
 *
 * Runs as an interceptor so the mapping happens once, before Retrofit tries to
 * deserialise an error body into a success type.
 */
internal class ErrorInterceptor(private val json: Json) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val response = try {
            chain.proceed(chain.request())
        } catch (e: LiftosaurException) {
            throw e
        } catch (e: IOException) {
            throw LiftosaurException(LiftosaurError.Network(e))
        }

        if (response.isSuccessful) return response

        // peekBody leaves the body readable, so nothing downstream is disturbed.
        val raw = runCatching { response.peekBody(MAX_ERROR_BYTES).string() }.getOrNull()
        val body = raw
            ?.let { runCatching { json.decodeFromString<ErrorEnvelope>(it).error }.getOrNull() }

        val code = body?.code
        val message = body?.message

        val error = when (response.code) {
            401 -> LiftosaurError.InvalidApiKey
            403 -> LiftosaurError.PremiumRequired
            400 -> if (code == "missing_set_input") {
                LiftosaurError.MissingSetInput(message)
            } else {
                LiftosaurError.BadRequest(code, message)
            }
            404 -> LiftosaurError.NotFound(message)
            409 -> when (code) {
                "workout_already_active" -> LiftosaurError.Conflict.WorkoutAlreadyActive
                "workout_start_time_taken" -> LiftosaurError.Conflict.StartTimeTaken
                "workout_mismatch" -> LiftosaurError.Conflict.WorkoutMismatch
                "ambiguous_entry" -> LiftosaurError.Conflict.AmbiguousEntry
                else -> LiftosaurError.Conflict.Other(code, message)
            }
            422 -> LiftosaurError.ScriptError(message, body?.details.orEmpty())
            in 500..599 -> LiftosaurError.Server(response.code, message)
            else -> LiftosaurError.Unknown(response.code, message)
        }

        response.close()
        throw LiftosaurException(error)
    }

    private companion object {
        const val MAX_ERROR_BYTES = 64L * 1024
    }
}
