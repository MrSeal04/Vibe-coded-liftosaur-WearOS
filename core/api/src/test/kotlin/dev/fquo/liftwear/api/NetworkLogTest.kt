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
 * The network half of the debug log. What it must say is what a sync bug needs - which call,
 * which status, which error code - and what it must never say is the key.
 */
class NetworkLogTest {

    private lateinit var server: MockWebServer
    private lateinit var api: LiftosaurApi
    private val lines = mutableListOf<Pair<EventLog.Level, String>>()

    private val recorder = object : EventLog {
        override fun log(area: String, message: String, level: EventLog.Level, error: Throwable?) {
            synchronized(lines) { lines += level to "$area $message" }
        }
    }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = LiftosaurApiFactory.create(
            apiKeyProvider = { KEY },
            deviceIdProvider = { DEVICE },
            clientName = "liftwear-test/0.0.1",
            baseUrl = server.url("/api/v1/").toString(),
            log = recorder,
        )
    }

    @After
    fun tearDown() = server.close()

    private fun enqueue(code: Int, body: String) {
        server.enqueue(MockResponse.Builder().code(code).body(body).build())
    }

    @Test
    fun `a request is logged with its method, path and status`() = runTest {
        enqueue(200, """{"data":{"units":"kg","timers":{}}}""")
        api.getSettings()

        val (level, line) = lines.single()
        assertEquals(EventLog.Level.Info, level)
        assertTrue(line, line.startsWith("Net GET /api/v1/settings 200 "))
    }

    @Test
    fun `a refused request keeps the API's error code`() = runTest {
        enqueue(409, """{"error":{"code":"workout_already_active","message":"m"}}""")
        runCatching { api.getSettings() }

        val (level, line) = lines.single()
        assertEquals(EventLog.Level.Warn, level)
        assertTrue(line, line.contains(" 409 ") && line.contains("workout_already_active"))
    }

    @Test
    fun `the key and the request headers are never written`() = runTest {
        enqueue(200, """{"data":{"units":"kg","timers":{}}}""")
        api.getSettings()
        enqueue(401, """{"error":{"code":"unauthorized","message":"bad key"}}""")
        runCatching { api.getSettings() }

        val everything = lines.joinToString("\n") { it.second }
        assertEquals(2, lines.size)
        assertFalse(everything, everything.contains(KEY))
        assertFalse(everything, everything.contains("Bearer"))
        assertFalse(everything, everything.contains(DEVICE))
    }

    @Test
    fun `a dead connection is logged as a failure`() = runTest {
        server.close()
        runCatching { api.getSettings() }

        val (level, line) = lines.single()
        assertEquals(EventLog.Level.Warn, level)
        assertTrue(line, line.contains("failed after"))
    }

    private companion object {
        const val KEY = "lftsk_not_a_real_key_but_long_enough"
        const val DEVICE = "test-device-3f9a"
    }
}
