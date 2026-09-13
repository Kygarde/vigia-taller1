package com.vigia.transport

import android.content.Context

/**
 * Ajustes del equipo que viajan en el anuncio BLE: quien es y en que aula esta.
 *
 * Desde la version 5 el equipo se identifica con el CODIGO DE ALUMNO, no con un
 * nombre libre. El codigo es verificable contra la lista de matriculados; un
 * nombre escrito a mano no.
 */
object EquipoStore {

    private const val PREFS = "vigia_prefs"
    private const val CLAVE_SALA = "codigo_sala"
    private const val CLAVE_CODIGO = "codigo_alumno"

    const val SALA_POR_DEFECTO = 101
    const val SALA_MINIMA = 0
    const val SALA_MAXIMA = 255          // el codigo de aula viaja en un solo byte

    /** Tope en bytes UTF-8. Un codigo UNI son 9 caracteres: 8 digitos y una letra. */
    const val CODIGO_MAX_BYTES = 9

    private val FORMATO = Regex("^[0-9]{8}[A-Za-z]$")

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun leerSala(c: Context): Int = prefs(c).getInt(CLAVE_SALA, SALA_POR_DEFECTO)

    fun guardarSala(c: Context, sala: Int) {
        prefs(c).edit().putInt(CLAVE_SALA, sala.coerceIn(SALA_MINIMA, SALA_MAXIMA)).apply()
    }

    fun leerCodigo(c: Context): String = prefs(c).getString(CLAVE_CODIGO, "") ?: ""

    fun guardarCodigo(c: Context, codigo: String) {
        prefs(c).edit().putString(CLAVE_CODIGO, recortar(codigo.trim().uppercase())).apply()
    }

    /** Recorta a CODIGO_MAX_BYTES sin partir un caracter por la mitad. */
    fun recortar(codigo: String): String {
        var s = codigo
        while (s.toByteArray(Charsets.UTF_8).size > CODIGO_MAX_BYTES && s.isNotEmpty()) {
            s = s.dropLast(1)
        }
        return s
    }

    /** Si el formato no calza, el cotejo contra matriculados nunca va a funcionar. */
    fun codigoValido(codigo: String): Boolean = FORMATO.matches(codigo.trim())
}
