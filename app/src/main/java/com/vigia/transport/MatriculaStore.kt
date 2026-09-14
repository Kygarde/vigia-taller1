package com.vigia.transport

import android.content.Context

/**
 * Lista de matriculados del aula, en el equipo del docente.
 *
 * El docente la pega desde UniVirtual: un codigo por linea, o separados por comas o
 * punto y coma. No hay integracion con UniVirtual — eso exigiria API institucional,
 * credenciales y red, que es justo lo que este proyecto decidio no usar.
 */
object MatriculaStore {

    private const val PREFS = "vigia_prefs"
    private const val CLAVE = "matriculados"

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Acepta lineas, comas o punto y coma. Devuelve cuantos codigos validos cargo. */
    fun cargar(c: Context, texto: String): Int {
        val codigos = texto
            .split('\n', ',', ';', ' ', '\t')
            .map { it.trim().uppercase() }
            .filter { EquipoStore.codigoValido(it) }
            .toSet()
        prefs(c).edit().putStringSet(CLAVE, codigos).apply()
        return codigos.size
    }

    fun matriculados(c: Context): Set<String> =
        prefs(c).getStringSet(CLAVE, emptySet()) ?: emptySet()

    fun hayLista(c: Context): Boolean = matriculados(c).isNotEmpty()

    fun limpiar(c: Context) { prefs(c).edit().remove(CLAVE).apply() }

    /** Las tres categorias que el docente necesita ver. */
    data class Cotejo(
        val presentes: Set<String>,
        val noConectados: Set<String>,
        val noMatriculados: Set<String>
    )

    fun cotejar(c: Context, codigosConectados: Set<String>): Cotejo {
        val lista = matriculados(c)
        if (lista.isEmpty()) return Cotejo(codigosConectados, emptySet(), emptySet())
        val normalizados = codigosConectados.map { it.uppercase() }.toSet()
        return Cotejo(
            presentes = lista intersect normalizados,
            noConectados = lista - normalizados,
            noMatriculados = normalizados - lista
        )
    }
}