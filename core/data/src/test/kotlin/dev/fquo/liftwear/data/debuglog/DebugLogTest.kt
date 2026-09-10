package dev.fquo.liftwear.data.debuglog

import dev.fquo.liftwear.api.EventLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.Executor

/**
 * The debug log is only worth anything if it is still there, in order and in one piece, when
 * somebody finally reads it - including after the process that wrote it has crashed.
 */
class DebugLogTest {

    @get:Rule
    val tmp = TemporaryFolder()

    /** Runs each write on the calling thread, so the tests can read the file straight away. */
    private val direct = Executor { it.run() }

    private fun debugLog(maxFileBytes: Long = DebugLog.MAX_FILE_BYTES, maxFiles: Int = DebugLog.MAX_FILES) =
        DebugLog(tmp.root, now = { 1_757_490_000_000L }, maxFileBytes = maxFileBytes, maxFiles = maxFiles, executor = direct)

    private fun exported(log: DebugLog): String = log.export(File(tmp.root, "out/export.txt")).readText()

    @Test
    fun `an entry carries its time, level, area and message`() {
        val log = debugLog()
        log.log("Outbox", "queued set e1/s1", EventLog.Level.Warn)

        val line = exported(log).trimEnd()
        assertTrue(
            line,
            Regex("""\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3} W Outbox   queued set e1/s1""").matches(line),
        )
    }

    @Test
    fun `a stack trace follows its entry, indented`() {
        val log = debugLog()
        log.log("Crash", "boom", EventLog.Level.Error, IllegalStateException("bad state"))

        val lines = exported(log).lines().filter { it.isNotEmpty() }
        assertTrue(lines[0], lines[0].endsWith("E Crash    boom"))
        assertTrue(lines[1], lines[1].startsWith("    java.lang.IllegalStateException: bad state"))
        assertTrue("every trace line is indented", lines.drop(1).all { it.startsWith("    ") })
    }

    @Test
    fun `a multi-line message stays one entry`() {
        val log = debugLog()
        log.log("App", "first\nsecond")

        val lines = exported(log).lines().filter { it.isNotEmpty() }
        assertEquals(2, lines.size)
        assertEquals("    second", lines[1])
    }

    @Test
    fun `the oldest entries go once the cap is reached, and what remains is in order`() {
        val log = debugLog(maxFileBytes = 1_000, maxFiles = 3)
        repeat(200) { i -> log.log("Test", "line %04d %s".format(i, "x".repeat(40))) }

        val files = tmp.root.listFiles { f -> f.name.startsWith("debug") }!!
        assertTrue("at most maxFiles files", files.size <= 3)
        assertTrue("within the size cap", files.sumOf { it.length() } <= 3_000)

        val numbers = Regex("line (\\d{4})").findAll(exported(log)).map { it.groupValues[1].toInt() }.toList()
        assertEquals("the newest entry survives", 199, numbers.last())
        assertTrue("the oldest entry is gone", numbers.first() > 0)
        assertEquals("oldest first, with nothing missing in between", (numbers.first()..199).toList(), numbers)
    }

    @Test
    fun `logNow reaches disk even when the writer thread never runs`() {
        // A writer that never gets scheduled, as when the process is already going down.
        val stuck = Executor { }
        val log = DebugLog(tmp.root, now = { 1_757_490_000_000L }, executor = stuck)
        log.log("Test", "queued but never written")

        log.logNow("Crash", "written anyway", error = RuntimeException("x"))

        val text = File(tmp.root, "debug.log").readText()
        assertTrue(text, text.contains("written anyway"))
        assertFalse(text, text.contains("queued but never written"))
    }

    @Test
    fun `an export can carry a header`() {
        val log = debugLog()
        log.log("App", "hello")

        val lines = log.export(File(tmp.root, "out/h.txt"), header = "LiftWear\nsecond line").readText().lines()
        assertEquals("# LiftWear", lines[0])
        assertEquals("# second line", lines[1])
        assertTrue(lines[2], lines[2].endsWith("hello"))
    }

    @Test
    fun `clear removes every file`() {
        val log = debugLog(maxFileBytes = 200, maxFiles = 3)
        repeat(20) { log.log("Test", "x".repeat(50)) }

        log.clear()

        assertEquals(0L, log.sizeBytes())
    }
}
