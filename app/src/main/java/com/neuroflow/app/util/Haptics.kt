package com.neuroflow.app.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Tiny haptics helper: crisp ticks for the odometer, soft pulses for the breathing orb. */
object Haptics {

    @Suppress("DEPRECATION")
    private fun vibrator(context: Context): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    /** Crisp tick used whenever the Reel counter increments. */
    fun tick(context: Context) {
        val v = vibrator(context) ?: return
        if (!v.hasVibrator()) return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
            } else {
                v.vibrate(VibrationEffect.createOneShot(12L, 90))
            }
        }
    }

    /** Soft pulse (breathing orb, puzzle feedback). */
    fun pulse(context: Context, durationMs: Long = 40L, amplitude: Int = 120) {
        val v = vibrator(context) ?: return
        if (!v.hasVibrator()) return
        runCatching {
            v.vibrate(VibrationEffect.createOneShot(durationMs.coerceAtLeast(1L), amplitude.coerceIn(1, 255)))
        }
    }

    /** Heavier thud used for failures / speedbumps. */
    fun thud(context: Context) {
        val v = vibrator(context) ?: return
        if (!v.hasVibrator()) return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
            } else {
                v.vibrate(VibrationEffect.createOneShot(60L, 200))
            }
        }
    }
}
