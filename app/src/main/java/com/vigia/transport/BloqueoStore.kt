package com.vigia.transport

import android.content.Context

/**
 * Marca de que el equipo salio de la aplicacion durante el examen.
 *
 * Se guarda en el propio equipo: cerrar y reabrir la app NO lo limpia. Solo el
 * docente puede levantarlo, y tiene que hacerlo presencialmente, porque el
 * advertising BLE es de una sola via: el alumno emite, el docente escucha. No hay
 * canal de vuelta para desbloquear a distancia.
 */
object BloqueoStore {

    private const val PREFS = "vigia_prefs"
    private const val CLAVE = "bloqueado"

    /**
     * Lo escribe el docente en el equipo del alumno para reabrirlo.
     *
     * Vive aqui como constante para que sea facil de cambiar antes de un examen.
     * No es autenticacion: es una tranca para que el alumno no se desbloquee solo.
     */
    const val PIN_DOCENTE = "2468"

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun leer(c: Context): Boolean = prefs(c).getBoolean(CLAVE, false)

    fun guardar(c: Context, valor: Boolean) {
        prefs(c).edit().putBoolean(CLAVE, valor).apply()
    }
}
