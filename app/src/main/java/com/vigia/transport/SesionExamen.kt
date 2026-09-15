package com.vigia.transport

import com.vigia.model.RiskLevel

/**
 * Estado acumulado de UNA sesion de examen, en el equipo del docente.
 *
 * Vive fuera del flujo de escaneo a proposito. El flujo se recrea cada vez que la
 * Activity vuelve al primer plano, y si el historial viviera dentro pasarian dos
 * cosas cada vez que el docente sale de la app y regresa: todos los alumnos volverian
 * a registrar "se unio al aula", y el contador de incidencias se reiniciaria a cero.
 * En un examen de dos horas eso es inservible.
 *
 * Se limpia en tres casos:
 *   - el docente finaliza el examen (lo normal)
 *   - cambia el numero de aula (es otro salon)
 *   - paso demasiado tiempo desde el ultimo evento (es otro dia)
 */
object SesionExamen {

    /**
     * Sin actividad por este tiempo, lo que sigue es un examen nuevo.
     *
     * Cubre el caso de olvidarse de finalizar: sin esto, el docente que prueba hoy el
     * aula 101 y manana abre la misma aula arranca con la bitacora de ayer en pantalla.
     */
    private const val CADUCIDAD_SESION_MS = 4L * 60 * 60 * 1000   // 4 horas

    private var salaActual: Int? = null
    private var ultimoMovimiento: Long = 0L

    val vistos = linkedMapOf<Int, StudentStatus>()
    val ausentes = mutableMapOf<Int, Long>()
    val incidencias = mutableMapOf<Int, Int>()
    val riesgoPrevio = mutableMapOf<Int, RiskLevel>()
    val salioPrevio = mutableMapOf<Int, Boolean>()

    /** Llamar al arrancar el escaneo. Decide si continua la sesion o empieza otra. */
    fun asegurarSala(sala: Int) {
        val ahora = System.currentTimeMillis()
        val otraSala = salaActual != sala
        val vencida = ultimoMovimiento > 0 && (ahora - ultimoMovimiento) > CADUCIDAD_SESION_MS

        if (otraSala || vencida) {
            salaActual = sala
            limpiar()
        }
        ultimoMovimiento = ahora
    }

    /** El docente cerro el examen: se borra todo y la proxima apertura empieza limpia. */
    fun finalizar() {
        salaActual = null
        ultimoMovimiento = 0L
        limpiar()
    }

    /** Cuantos equipos y cuantas incidencias hubo, para el resumen de cierre. */
    fun resumen(): Triple<Int, Int, Int> =
        Triple(vistos.size, incidencias.values.sum(), Bitacora.lineas.value.size)

    private fun limpiar() {
        vistos.clear()
        ausentes.clear()
        incidencias.clear()
        riesgoPrevio.clear()
        salioPrevio.clear()
        Bitacora.limpiar()
        PadronStore.reabrir()
    }
}
