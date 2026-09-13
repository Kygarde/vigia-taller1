package com.vigia.transport

/**
 * Padrón del examen: los equipos que estaban presentes cuando el docente pasó lista.
 */
object PadronStore {

    private val ids = mutableSetOf<Int>()
    var cerrado: Boolean = false; private set
    var sinEquipo: Int = 0; private set
    var presentes: Int = 0; private set

    fun cerrar(idsConectados: Set<Int>, presentesEnAula: Int, sinEquipoEnAula: Int) {
        ids.clear()
        ids.addAll(idsConectados)
        presentes = presentesEnAula
        sinEquipo = sinEquipoEnAula
        cerrado = true
    }

    fun reabrir() {
        ids.clear(); cerrado = false; sinEquipo = 0; presentes = 0
    }

    /** Con el padrón abierto no se rechaza a nadie. */
    fun aceptado(id: Int): Boolean = !cerrado || id in ids

    val tamano: Int get() = ids.size
}