package com.vigia.processing

import com.vigia.decision.AdaptationRules
import kotlin.math.abs
import kotlin.math.sqrt

class SignalProcessor {

    private val window = ArrayDeque<Float>()
    private var ema = 0f

    /** Convierte una muestra cruda del acelerómetro en movementIndex 0.0 .. 1.0 */
    fun process(sample: FloatArray): Float {
        val magnitude = sqrt(
            sample[0] * sample[0] + sample[1] * sample[1] + sample[2] * sample[2]
        )
        val linear = abs(magnitude - AdaptationRules.GRAVITY)

        window.addLast(linear)
        if (window.size > AdaptationRules.WINDOW_SIZE) window.removeFirst()

        val avg = window.average().toFloat()
        ema = AdaptationRules.EMA_ALPHA * avg + (1 - AdaptationRules.EMA_ALPHA) * ema

        return (ema / AdaptationRules.NORMALIZATION_CEILING).coerceIn(0f, 1f)
    }

    fun reset() { window.clear(); ema = 0f }
}