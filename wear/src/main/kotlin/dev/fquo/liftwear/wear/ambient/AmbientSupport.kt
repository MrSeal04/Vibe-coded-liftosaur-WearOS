package dev.fquo.liftwear.wear.ambient

import android.content.Context
import android.util.Log

/**
 * Whether this watch can actually deliver ambient callbacks.
 *
 * Wear Compose's `rememberAmbientModeManager()` goes through `com.google.wear.Sdk`, which
 * is not a dependency of this app at all: the foundation AAR merges
 * `<uses-library android:name="wear-sdk" android:required="false"/>` into the manifest and
 * the classes come from `/system/framework/wear-sdk.jar` on the device. So the app installs
 * and runs fine where that library is missing, and only finds out when it asks.
 *
 * Two things can go wrong, and neither is catchable where it happens - the call is made
 * inside a `DisposableEffect` in library code:
 *
 *  1. the shared library is absent, and `Sdk` fails to link (a `LinkageError`, not an
 *     exception);
 *  2. the library is present but declines to hand out an `AmbientManager` - its own log
 *     string is "getWearManager could not create manager for service" - and the library
 *     then dereferences the null.
 *
 * So the probe is made here, once per process, reflectively, before anything is composed.
 * Reflection rather than a compile-time reference because there is no artifact to compile
 * against; `wear-sdk.jar` ships as an `optional/` stub in some platform SDKs only.
 *
 * Verified present on the Wear OS 4 (API 33) and Wear OS 6 (API 36) system images, which
 * is the whole supported range - so this is a guard against surprises, not a routine path.
 */
object AmbientSupport {

    private const val TAG = "AmbientSupport"

    @Volatile private var cached: Boolean? = null

    fun isAvailable(context: Context): Boolean =
        cached ?: probe(context.applicationContext).also { cached = it }

    private fun probe(context: Context): Boolean = try {
        val sdk = Class.forName("com.google.wear.Sdk")
        val ambientManager = Class.forName("com.google.wear.services.ambient.AmbientManager")
        val getWearManager = sdk.getMethod("getWearManager", Context::class.java, Class::class.java)
        getWearManager.invoke(null, context, ambientManager) != null
    } catch (t: Throwable) {
        // Throwable, not Exception: the interesting failure is NoClassDefFoundError.
        Log.i(TAG, "Ambient mode unavailable on this device; staying interactive", t)
        false
    }
}
