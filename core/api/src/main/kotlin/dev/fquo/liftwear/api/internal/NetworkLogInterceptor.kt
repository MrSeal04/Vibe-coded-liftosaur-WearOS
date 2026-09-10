package dev.fquo.liftwear.api.internal

import dev.fquo.liftwear.api.EventLog
import dev.fquo.liftwear.api.warn
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * One line per request: method, path, status and how long it took.
 *
 * Sits inside [ErrorInterceptor], so it sees the raw status code and the raw failure rather than
 * the typed error - the typed error is what the caller logs. Headers are never written (one of
 * them is the bearer token), and neither is a success body. An error body is, cut short, because
 * the API's error code is usually the whole diagnosis.
 */
internal class NetworkLogInterceptor(private val log: EventLog) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val target = "${request.method} ${request.url.encodedPath}" +
            (request.url.encodedQuery?.let { "?$it" } ?: "")
        val started = System.nanoTime()

        val response = try {
            chain.proceed(request)
        } catch (e: IOException) {
            log.warn(AREA, "$target failed after ${elapsedMillis(started)}ms: ${e.javaClass.simpleName}: ${e.message}")
            throw e
        }

        if (response.isSuccessful) {
            log.log(AREA, "$target ${response.code} ${elapsedMillis(started)}ms")
        } else {
            val body = runCatching { response.peekBody(MAX_ERROR_CHARS.toLong()).string() }.getOrNull()
                ?.replace('\n', ' ')
                ?.take(MAX_ERROR_CHARS)
            log.warn(AREA, "$target ${response.code} ${elapsedMillis(started)}ms ${body.orEmpty()}".trimEnd())
        }
        return response
    }

    private fun elapsedMillis(started: Long) = (System.nanoTime() - started) / 1_000_000

    private companion object {
        const val AREA = "Net"
        const val MAX_ERROR_CHARS = 300
    }
}
