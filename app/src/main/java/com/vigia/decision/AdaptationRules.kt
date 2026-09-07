package com.vigia.decision

/**
 * Todos los umbrales del sistema viven aqui.
 *
 * Valores ajustados para supervision de examenes: el equipo pasa la mayor parte
 * del tiempo apoyado en la mesa, y lo que interesa detectar es que alguien lo
 * tome y lo manipule. Escribir junto al equipo NO debe elevar el riesgo.
 */
object AdaptationRules {

    // --- Procesamiento ---

    /**
     * Muestras de la ventana deslizante. Con SENSOR_DELAY_NORMAL (~200 ms por
     * muestra), 10 muestras equivalen a unos 2 s de historia. Con 20 la ventana
     * tardaba ~4 s en llenarse y el cambio de modo se sentia lento en la demo.
     */
    const val WINDOW_SIZE = 10

    /** Peso de la muestra nueva en la media movil exponencial. Mas alto = mas reactivo. */
    const val EMA_ALPHA = 0.3f

    const val GRAVITY = 9.81f

    /**
     * Aceleracion lineal (m/s^2) que mapea a movementIndex = 1.0.
     * A 4.0 el rango util queda repartido asi:
     *   equipo en la mesa, alguien escribiendo al lado  ->  ~0.03 - 0.10
     *   equipo levantado o manipulado                   ->  ~0.25 - 0.50
     *   equipo agitado                                  ->  ~0.80 - 1.00
     */
    const val NORMALIZATION_CEILING = 4.0f

    // --- Riesgo ---

    /** Manipulacion evidente y sostenida del equipo. */
    const val MOVEMENT_ALERTA = 0.25f

    /** El equipo se movio, pero aun no es concluyente. */
    const val MOVEMENT_ATENCION = 0.10f

    /**
     * Tiempo que el movimiento debe mantenerse sobre MOVEMENT_ALERTA.
     * Filtra los golpes puntuales (un roce, la mesa que se mueve) y deja pasar
     * el uso real del equipo.
     */
    const val SUSTAINED_MS = 2_500L

    // --- Modo operativo ---

    /** Porcentaje de bateria que dispara el modo AHORRO. */
    const val BATTERY_LOW = 15

    /** Bloqueo entre cambios de modo. Evita que el modo oscile en el umbral. */
    const val HYSTERESIS_MS = 5_000L
}
