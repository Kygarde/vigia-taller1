package com.vigia.transport

import com.vigia.model.RiskLevel

/**
 * Estado acumulado de UNA sesion de examen, en el equipo del docente.
 *
 * Vive fuera del flujo de escaneo a proposito. El flujo se recrea cada vez que la
 * Activity vuelve al primer plano (el contador cicloBluetooth), y si el historial
 * viviera dentro del flujo pasarian dos cosas cada vez que el docente sale de la app
 * y regresa: todos los alumnos volverian a registrar "se unio al aula", y el contador
 * de incidencias se reiniciaria a cero. En un examen de dos horas eso es inservible.
 *
 * Se limpia solo cuando cambia el aula: eso si es otra sesion.
 */
object SesionExamen {

    private var salaActual: Int? = null

    val vistos = linkedMapOf<Int, StudentStatus>()
    val ausentes = mutableMapOf<Int, Long>()
    val incidencias = mutableMapOf<Int, Int>()
    val riesgoPrevio = mutableMapOf<Int, RiskLevel>()
    val salioPrevio = mutableMapOf<Int, Boolean>()

    /** Llamar al arrancar el escaneo. Si el aula cambio, empieza una sesion nueva. */
    fun asegurarSala(sala: Int) {
        if (salaActual != sala) {
            salaActual = sala
            reiniciar()
        }
    }

    fun reiniciar() {
        vistos.clear()
        ausentes.clear()
        incidencias.clear()
        riesgoPrevio.clear()
        salioPrevio.clear()
        Bitacora.limpiar()
        PadronStore.reabrir()
    }
}
