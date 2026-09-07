package dev.fquo.liftwear.api.internal

import dev.fquo.liftwear.api.LiftosaurError
import dev.fquo.liftwear.api.LiftosaurException
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Adds the bearer token and the two headers the API requires on writes.
 *
 * The docs mark Device-Id/Client as write-only requirements, but sending them on
 * every request is harmless and avoids a class of bug where a request is switched
 * from GET to POST and silently loses them.
 */
internal class AuthInterceptor(
    private val apiKeyProvider: () -> String?,
    private val deviceIdProvider: () -> String,
    private val clientName: String,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        // Fail fast and typed, rather than letting the server return a bare 401.
        val key = apiKeyProvider() ?: throw LiftosaurException(LiftosaurError.InvalidApiKey)

        val request = chain.request().newBuilder()
            .header("Authorization", "Bearer $key")
            .header("Accept", "application/json")
            .header("X-Liftosaur-Device-Id", deviceIdProvider())
            .header("X-Liftosaur-Client", clientName)
            .build()

        return chain.proceed(request)
    }
}
