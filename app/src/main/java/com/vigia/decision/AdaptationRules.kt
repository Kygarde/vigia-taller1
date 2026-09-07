package com.vigia.decision

/**
 * Todos los umbrales del sistema viven aqui.
 *
 * Calibrado para supervision de examenes: el equipo pasa casi todo el tiempo
 * apoyado en la mesa, y hay que detectar que alguien lo tome y lo manipule.
 * Escribir junto al equipo NO debe elevar el riesgo a ALERTA.
 *
 * El compromiso es entre sensibilidad y falsas alarmas: una ventana corta
 * reacciona rapido pero deja pasar mas ruido; una larga es estable pero diluye
 * los gestos breves, que es justo lo que interesa detectar aqui.
 */
object AdaptationRules {

    // --- Procesamiento ---

    /**
     * Muestras de la ventana deslizante. A ~5 muestras/s en modo NORMAL, 4
     * muestras son ~0.8 s de historia. Con ventanas largas el promedio diluia
     * los picos y agitar el equipo no llegaba a cruzar el umbral.
     */
    const val WINDOW_SIZE = 4

    /**
     * Peso de la muestra nueva en la media movil exponencial.
     * Alto = reacciona rapido; bajo = suaviza mas y responde tarde.
     */
    const val EMA_ALPHA = 0.6f

    const val GRAVITY = 9.81f

    /**
     * Aceleracion lineal (m/s^2) que mapea a movementIndex = 1.0.
     * Cuanto mas bajo, mas sensible es todo el sistema.
     */
    const val NORMALIZATION_CEILING = 2.0f

    // --- Riesgo ---

    /** El equipo esta siendo manipulado. */
    const val MOVEMENT_ALERTA = 0.25f

    /** El equipo se movio, pero aun no es concluyente. */
    const val MOVEMENT_ATENCION = 0.12f

    /**
     * Tiempo que el movimiento debe mantenerse sobre MOVEMENT_ALERTA.
     * 1.2 s filtra el golpe puntual (un roce, la mesa que se mueve) pero deja
     * pasar el gesto de tomar el equipo y usarlo.
     */
    const val SUSTAINED_MS = 1_200L

    // --- Modo operativo ---

    /** Porcentaje de bateria que dispara el modo AHORRO. */
    const val BATTERY_LOW = 15

    /** Bloqueo entre cambios de modo. Evita que el modo oscile en el umbral. */
    const val HYSTERESIS_MS = 5_000L
}
