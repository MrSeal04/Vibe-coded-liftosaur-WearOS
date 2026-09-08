package dev.fquo.liftwear.wear.rest

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * The two buzzes that matter.
 *
 * They are deliberately different shapes, not different lengths of the same shape: mid-set,
 * the wrist is the only channel available, and a lifter has to be able to tell "nearly" from
 * "now" without looking at anything.
 */
object RestHaptics {

    /** T-10s or so. One soft tap - information, not a summons. */
    private val WARNING_TIMINGS = longArrayOf(0, 60)
    private val WARNING_AMPLITUDES = intArrayOf(0, 90)

    /** T-0. Three firm pulses, unmistakable through a sleeve. */
    private val DONE_TIMINGS = longArrayOf(0, 180, 120, 180, 120, 260)
    private val DONE_AMPLITUDES = intArrayOf(0, 255, 0, 255, 0, 255)

    fun warning(context: Context) = vibrate(context, WARNING_TIMINGS, WARNING_AMPLITUDES)

    fun done(context: Context) = vibrate(context, DONE_TIMINGS, DONE_AMPLITUDES)

    private fun vibrate(context: Context, timings: LongArray, amplitudes: IntArray) {
        val vibrator = vibrator(context) ?: return
        if (!vibrator.hasVibrator()) return
        val effect = if (vibrator.hasAmplitudeControl()) {
            VibrationEffect.createWaveform(timings, amplitudes, -1)
        } else {
            // Without amplitude control the pattern still reads as one tap vs three pulses,
            // which is the distinction that carries the meaning.
            VibrationEffect.createWaveform(timings, -1)
        }
        vibrator.vibrate(effect)
    }

    private fun vibrator(context: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(VibratorManager::class.java))?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }
}
