package dev.fquo.liftwear.data.debuglog

import dev.fquo.liftwear.api.EventLog
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * The on-watch debug log: a few megabytes of what the app did, kept across crashes, reboots and
 * whole training sessions.
 *
 * logcat on a Galaxy Watch 4 is a 1 MiB ring shared with the whole system, measured at about
 * fifteen minutes deep - and a bug noticed mid-session is read an hour later at best. So this
 * writes to the app's own storage instead: [maxFiles] files of [maxFileBytes] each, the oldest
 * dropped as the newest fills.
 *
 * Every write goes through one background thread, so logging from the main thread costs a queue
 * insert rather than disk IO. The exception is [logNow], for a crash handler: the process is
 * about to die, and the line has to reach disk before it does.
 */
class DebugLog(
    private val dir: File,
    private val now: () -> Long = System::currentTimeMillis,
    private val maxFileBytes: Long = MAX_FILE_BYTES,
    private val maxFiles: Int = MAX_FILES,
    private val executor: Executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "liftwear-debuglog").apply { isDaemon = true }
    },
) : EventLog {

    private val lock = Any()

    /** Guarded by [lock]: SimpleDateFormat is not thread-safe. */
    private val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    override fun log(area: String, message: String, level: EventLog.Level, error: Throwable?) {
        // The time is taken here, not on the writer thread, so a backlog cannot skew it.
        val at = now()
        executor.execute { write(at, area, message, level, error) }
    }

    /**
     * Writes on the calling thread, after giving the queue a moment to empty.
     *
     * For the uncaught-exception handler only. Bounded, so a writer thread that is itself the
     * one crashing costs half a second rather than a hang.
     */
    fun logNow(
        area: String,
        message: String,
        level: EventLog.Level = EventLog.Level.Error,
        error: Throwable? = null,
    ) {
        flush(CRASH_FLUSH_MILLIS)
        write(now(), area, message, level, error)
    }

    /** Waits for everything queued so far to reach disk. False if it did not in time. */
    fun flush(timeoutMillis: Long = FLUSH_MILLIS): Boolean {
        val done = CountDownLatch(1)
        executor.execute { done.countDown() }
        return done.await(timeoutMillis, TimeUnit.MILLISECONDS)
    }

    /**
     * The whole log as one file, oldest entry first, for adb or the phone.
     *
     * @param header lines written at the top, each prefixed with `# `.
     */
    fun export(target: File, header: String? = null): File {
        flush()
        synchronized(lock) {
            target.parentFile?.mkdirs()
            target.outputStream().buffered().use { out ->
                header?.lineSequence()?.forEach { out.write("# $it\n".toByteArray(Charsets.UTF_8)) }
                for (file in files().asReversed()) {
                    if (file.exists()) file.inputStream().use { it.copyTo(out) }
                }
            }
        }
        return target
    }

    /** Approximate: whatever is still queued is not counted. */
    fun sizeBytes(): Long = synchronized(lock) { files().sumOf { if (it.exists()) it.length() else 0L } }

    fun clear() {
        flush()
        synchronized(lock) { files().forEach { it.delete() } }
    }

    /** Newest first: element 0 is the file being written to. */
    private fun files(): List<File> =
        (0 until maxFiles).map { i -> File(dir, if (i == 0) "debug.log" else "debug.$i.log") }

    private fun write(at: Long, area: String, message: String, level: EventLog.Level, error: Throwable?) {
        synchronized(lock) {
            // A full disk must not take the app down with it; losing a log line is the lesser harm.
            runCatching {
                val bytes = format(at, area, message, level, error).toByteArray(Charsets.UTF_8)
                dir.mkdirs()
                val current = files().first()
                if (current.exists() && current.length() + bytes.size > maxFileBytes) rotate()
                current.appendBytes(bytes)
            }
        }
    }

    private fun rotate() {
        val files = files()
        files.last().delete()
        for (i in files.size - 2 downTo 0) {
            if (files[i].exists()) files[i].renameTo(files[i + 1])
        }
    }

    private fun format(at: Long, area: String, message: String, level: EventLog.Level, error: Throwable?): String =
        buildString {
            append(timestamp.format(Date(at))).append(' ')
            append(level.letter).append(' ')
            append(area.padEnd(AREA_WIDTH)).append(' ')
            // Continuation lines are indented, so every entry still starts at column 0 with a date.
            append(message.replace("\n", "\n$INDENT")).append('\n')
            if (error != null) {
                error.stackTraceToString().take(MAX_TRACE_CHARS).trimEnd().lineSequence()
                    .forEach { append(INDENT).append(it).append('\n') }
            }
        }

    companion object {
        /** Four of these is 2 MB: dozens of sessions of normal logging. */
        const val MAX_FILE_BYTES = 512L * 1024
        const val MAX_FILES = 4

        private const val AREA_WIDTH = 8
        private const val INDENT = "    "
        private const val MAX_TRACE_CHARS = 8_000
        private const val FLUSH_MILLIS = 2_000L
        private const val CRASH_FLUSH_MILLIS = 500L
    }
}

/**
 * Writes an uncaught exception to the log before the process dies, then hands it on - so the
 * system still records the crash, and the next start can read back that it happened.
 */
fun DebugLog.captureUncaughtExceptions() {
    val previous = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, error ->
        runCatching { logNow("Crash", "uncaught exception on thread ${thread.name}", EventLog.Level.Error, error) }
        previous?.uncaughtException(thread, error)
    }
}
