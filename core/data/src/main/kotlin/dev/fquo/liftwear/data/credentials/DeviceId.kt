package dev.fquo.liftwear.data.credentials

import android.content.Context
import java.util.UUID

/**
 * A stable per-install id for `X-Liftosaur-Device-Id`.
 *
 * Deliberately plain SharedPreferences rather than the encrypted store: it is not a
 * secret, and OkHttp's interceptor needs it synchronously on whatever thread the call
 * happens to be on. Regenerating it on reinstall is fine - the header identifies a
 * device, not a user.
 */
object DeviceId {

    private const val PREFS = "liftwear_device"
    private const val KEY = "device_id"

    @Volatile private var cached: String? = null

    fun get(context: Context): String =
        cached ?: synchronized(this) {
            cached ?: load(context.applicationContext).also { cached = it }
        }

    private fun load(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(KEY, null)?.let { return it }
        val fresh = UUID.randomUUID().toString()
        prefs.edit().putString(KEY, fresh).apply()
        return fresh
    }
}
