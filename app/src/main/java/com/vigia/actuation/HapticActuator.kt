package com.vigia.actuation

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Actuador físico multimodal: vibración continua + alerta acústica disuasiva.
 */
class HapticActuator(context: Context) {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        manager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    private var toneGenerator: ToneGenerator? = runCatching {
        ToneGenerator(AudioManager.STREAM_ALARM, 100)
    }.getOrNull()

    private var sonando = false

    /**
     * Inicia una alarma física persistente:
     * - Vibración en bucle (patrón de pulsos de advertencia).
     * - Tono acústico disuasivo continuo.
     */
    fun iniciarAlertaSostenida() {
        if (sonando) return
        sonando = true

        // 1. Vibración sostenida en bucle
        vibrator?.let { v ->
            if (v.hasVibrator()) {
                val timings = longArrayOf(0, 400, 200, 400, 200) // tiempo apagado/encendido
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val amplitudes = intArrayOf(0, 255, 0, 255, 0)
                    // repeat = 0 hace que se repita indefinidamente hasta llamar a detenerAlerta()
                    v.vibrate(VibrationEffect.createWaveform(timings, amplitudes, 0))
                } else {
                    @Suppress("DEPRECATION")
                    v.vibrate(timings, 0)
                }
            }
        }

        // 2. Tono acústico disuasivo (beep agudo continuo)
        runCatching {
            toneGenerator?.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 15_000)
        }
    }

    /**
     * Detiene inmediatamente la vibración y el sonido cuando el alumno normaliza el celular.
     */
    fun detenerAlerta() {
        if (!sonando) return
        sonando = false
        runCatching { vibrator?.cancel() }
        runCatching { toneGenerator?.stopTone() }
    }
}