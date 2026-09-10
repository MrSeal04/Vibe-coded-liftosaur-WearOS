package dev.fquo.liftwear.api

/**
 * Where LiftWear writes down what it did, to be read after something has gone wrong.
 *
 * Declared at the bottom of the module graph so the network layer can write to it; the
 * persistent implementation is `DebugLog` in :core:data. [None] is the default everywhere, so
 * tests and the phone app pay nothing for it.
 *
 * **Never hand it the API key, a request header or a response body.** A log is the kind of
 * file that gets attached to an issue, and this repository is public.
 */
interface EventLog {

    fun log(area: String, message: String, level: Level = Level.Info, error: Throwable? = null)

    enum class Level(val letter: Char) { Info('I'), Warn('W'), Error('E') }

    companion object {
        val None: EventLog = object : EventLog {
            override fun log(area: String, message: String, level: Level, error: Throwable?) = Unit
        }
    }
}

fun EventLog.warn(area: String, message: String, error: Throwable? = null) =
    log(area, message, EventLog.Level.Warn, error)
