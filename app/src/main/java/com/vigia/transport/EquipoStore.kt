package com.vigia.transport

import android.content.Context

/**
 * Ajustes del equipo que viajan en el anuncio BLE: quien es y en que aula esta.
 * Se guardan en el propio equipo, asi que sobreviven a cerrar la app.
 */
object EquipoStore {

    private const val PREFS = "vigia_prefs"
    private const val CLAVE_SALA = "codigo_sala"
    private const val CLAVE_NOMBRE = "nombre_alumno"

    const val SALA_POR_DEFECTO = 101
    const val SALA_MINIMA = 0
    const val SALA_MAXIMA = 255          // el codigo viaja en un solo byte

    /** Tope en bytes UTF-8. El anuncio BLE solo admite 31 bytes en total. */
    const val NOMBRE_MAX_BYTES = 10

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun leerSala(c: Context): Int = prefs(c).getInt(CLAVE_SALA, SALA_POR_DEFECTO)

    fun guardarSala(c: Context, sala: Int) {
        prefs(c).edit().putInt(CLAVE_SALA, sala.coerceIn(SALA_MINIMA, SALA_MAXIMA)).apply()
    }

    fun leerNombre(c: Context): String = prefs(c).getString(CLAVE_NOMBRE, "") ?: ""

    fun guardarNombre(c: Context, nombre: String) {
        prefs(c).edit().putString(CLAVE_NOMBRE, recortar(nombre.trim())).apply()
    }

    /** Recorta a NOMBRE_MAX_BYTES sin partir un caracter por la mitad. */
    fun recortar(nombre: String): String {
        var s = nombre
        while (s.toByteArray(Charsets.UTF_8).size > NOMBRE_MAX_BYTES && s.isNotEmpty()) {
            s = s.dropLast(1)
        }
        return s
    }
}
