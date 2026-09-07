package com.vigia.decision

object AdaptationRules {

    // --- Procesamiento ---
    const val WINDOW_SIZE = 20
    const val EMA_ALPHA = 0.2f
    const val GRAVITY = 9.81f
    const val NORMALIZATION_CEILING = 6.0f   // m/s² que mapea a movementIndex = 1.0

    // --- Riesgo ---
    const val MOVEMENT_ALERTA = 0.40f
    const val MOVEMENT_ATENCION = 0.20f
    const val SUSTAINED_MS = 3_000L          // tiempo sostenido para ALERTA

    // --- Modo operativo ---
    const val BATTERY_LOW = 15               // %
    const val HYSTERESIS_MS = 5_000L         // bloqueo entre cambios de modo
}