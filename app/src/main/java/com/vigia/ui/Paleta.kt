package com.vigia.ui

import androidx.compose.ui.graphics.Color

/**
 * Tres colores para toda la identidad: guinda, blanco y plomo.
 *
 * Aparte viven los colores de modo. Esos no son decoracion: verde, rojo y naranja
 * significan algo y tienen que leerse igual en el equipo del alumno y en el panel
 * del docente. Por eso no entran en la paleta de identidad.
 */
object Paleta {
    // Identidad
    val Guinda = Color(0xFF800020)
    val GuindaOscuro = Color(0xFF5C0017)
    val Blanco = Color(0xFFFFFFFF)
    val Plomo = Color(0xFF6E6E73)
    val PlomoClaro = Color(0xFFA8A8AD)
    val PlomoFondo = Color(0xFFF2F2F3)
    val SobreGuinda = Color(0xFFE8D5DA)

    // Semaforo de modo
    val Normal = Color(0xFF2E7D32)
    val Intensivo = Color(0xFFC62828)
    val Ahorro = Color(0xFFEF6C00)
    val Desconectado = Color(0xFF455A64)

    val Tinta = Color(0xFF202124)
    val Tenue = Color(0xFF5F6368)
    val Suave = Color(0xFFF1F3F4)
}
