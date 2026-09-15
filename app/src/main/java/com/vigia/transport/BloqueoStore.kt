package com.vigia.transport

import android.content.Context

/**
 * Marca de que el equipo salio de la aplicacion durante el examen.
 *
 * Se guarda en el propio equipo: cerrar y reabrir la app NO lo limpia, y alejarse del
 * salon tampoco. Solo el docente lo levanta, con el PIN que el mismo eligio al abrir
 * el aula, y tiene que hacerlo presencialmente: el advertising BLE es de una sola via
 * —el alumno emite, el docente escucha— asi que no hay canal de vuelta.
 *
 * El PIN no se guarda: se guarda su huella, la misma que viajo en la baliza del aula.
 * Asi el equipo del alumno puede validar sin haber conocido nunca los cuatro digitos.
 */
object BloqueoStore {

    private const val PREFS = "vigia_prefs"
    private const val CLAVE = "bloqueado"
    private const val CLAVE_HUELLA = "huella_pin_aula"

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun leer(c: Context): Boolean = prefs(c).getBoolean(CLAVE, false)

    fun guardar(c: Context, valor: Boolean) {
        prefs(c).edit().putBoolean(CLAVE, valor).apply()
    }

    /** Se guarda al unirse: es la huella del PIN del aula a la que entro. */
    fun guardarHuella(c: Context, huella: Int) {
        prefs(c).edit().putInt(CLAVE_HUELLA, huella).apply()
    }

    fun leerHuella(c: Context): Int = prefs(c).getInt(CLAVE_HUELLA, -1)
}
