package dev.fquo.liftwear.api

import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Each API failure gets its own screen on the watch, so each must map to a
 * distinct typed error. "Premium required" and "no signal" must never collapse
 * into one generic message.
 */
class ErrorMappingTest {

    private lateinit var server: MockWebServer
    private lateinit var api: LiftosaurApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = LiftosaurApiFactory.create(
            apiKeyProvider = { "lftsk_test" },
            deviceIdProvider = { "test-device" },
            clientName = "liftwear-test/0.0.1",
            baseUrl = server.url("/api/v1/").toString(),
        )
    }

    @After
    fun tearDown() = server.close()

    private fun enqueue(code: Int, body: String) {
        server.enqueue(MockResponse.Builder().code(code).body(body).build())
    }

    private fun errorBody(code: String, message: String = "msg") =
        """{"error":{"code":"$code","message":"$message"}}"""

    private suspend fun captureError(block: suspend () -> Unit): LiftosaurError =
        try {
            block()
            error("expected a LiftosaurException")
        } catch (e: LiftosaurException) {
            e.error
        }

    @Test
    fun `401 maps to InvalidApiKey`() = runTest {
        enqueue(401, errorBody("unauthorized"))
        assertEquals(LiftosaurError.InvalidApiKey, captureError { api.getSettings() })
    }

    @Test
    fun `403 maps to PremiumRequired`() = runTest {
        enqueue(403, errorBody("premium_required"))
        assertEquals(LiftosaurError.PremiumRequired, captureError { api.getSettings() })
    }

    @Test
    fun `400 missing_set_input is distinguished from other bad requests`() = runTest {
        enqueue(400, errorBody("missing_set_input", "reps required"))
        val e = captureError { api.getSettings() }
        assertTrue(e is LiftosaurError.MissingSetInput)
        assertEquals("reps required", (e as LiftosaurError.MissingSetInput).message)

        enqueue(400, errorBody("something_else"))
        assertTrue(captureError { api.getSettings() } is LiftosaurError.BadRequest)
    }

    @Test
    fun `each 409 subtype maps to its own recovery path`() = runTest {
        enqueue(409, errorBody("workout_already_active"))
        assertEquals(LiftosaurError.Conflict.WorkoutAlreadyActive, captureError { api.getSettings() })

        enqueue(409, errorBody("workout_start_time_taken"))
        assertEquals(LiftosaurError.Conflict.StartTimeTaken, captureError { api.getSettings() })

        enqueue(409, errorBody("workout_mismatch"))
        assertEquals(LiftosaurError.Conflict.WorkoutMismatch, captureError { api.getSettings() })

        enqueue(409, errorBody("ambiguous_entry"))
        assertEquals(LiftosaurError.Conflict.AmbiguousEntry, captureError { api.getSettings() })

        enqueue(409, errorBody("brand_new_conflict"))
        assertTrue(captureError { api.getSettings() } is LiftosaurError.Conflict.Other)
    }

    @Test
    fun `422 carries the Liftoscript error details`() = runTest {
        enqueue(
            422,
            """{"error":{"code":"parse_error","message":"bad script",
               "details":[{"line":3,"offset":1,"message":"unexpected token"}]}}"""
        )
        val e = captureError { api.getSettings() } as LiftosaurError.ScriptError
        assertEquals("bad script", e.message)
        assertEquals(1, e.details.size)
        assertEquals(3, e.details[0].line)
    }

    @Test
    fun `5xx maps to Server and is retryable`() = runTest {
        enqueue(503, errorBody("unavailable"))
        val e = captureError { api.getSettings() } as LiftosaurError.Server
        assertEquals(503, e.status)
        assertTrue(e.isRetryable)
    }

    @Test
    fun `an unparseable error body still yields a typed error`() = runTest {
        // A proxy or captive portal can return HTML with a 403.
        enqueue(403, "<html>Forbidden</html>")
        assertEquals(LiftosaurError.PremiumRequired, captureError { api.getSettings() })
    }

    @Test
    fun `a missing api key fails fast without hitting the network`() = runTest {
        val unpaired = LiftosaurApiFactory.create(
            apiKeyProvider = { null },
            deviceIdProvider = { "d" },
            clientName = "t",
            baseUrl = server.url("/api/v1/").toString(),
        )
        assertEquals(LiftosaurError.InvalidApiKey, captureError { unpaired.getSettings() })
        assertEquals("no request should have been sent", 0, server.requestCount)
    }

    @Test
    fun `required headers are sent on every request`() = runTest {
        enqueue(200, """{"data":{"units":"kg","timers":{}}}""")
        api.getSettings()
        val recorded = server.takeRequest()
        assertEquals("Bearer lftsk_test", recorded.headers["Authorization"])
        assertEquals("test-device", recorded.headers["X-Liftosaur-Device-Id"])
        assertEquals("liftwear-test/0.0.1", recorded.headers["X-Liftosaur-Client"])
    }

    @Test
    fun `errors needing user action are not silently retried forever`() {
        assertTrue(LiftosaurError.PremiumRequired.requiresUserAction)
        assertTrue(LiftosaurError.InvalidApiKey.requiresUserAction)
        assertTrue(LiftosaurError.Conflict.WorkoutMismatch.requiresUserAction)
        assertTrue(LiftosaurError.MissingSetInput("x").requiresUserAction)

        // These should keep retrying instead of nagging the user mid-workout.
        assertFalse(LiftosaurError.Conflict.StartTimeTaken.requiresUserAction)
        assertTrue(LiftosaurError.Conflict.StartTimeTaken.isRetryable)
    }
}
