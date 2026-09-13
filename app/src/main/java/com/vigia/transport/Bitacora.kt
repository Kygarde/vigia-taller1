package com.vigia.transport

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class Evento(val texto: String) {
    UNIDO("se unió al aula"),
    ALERTA("entró en ALERTA"),
    SIN_SENAL("dejó de emitir"),
    REGRESO("volvió a emitir"),
    SALIO_APP("salió de la aplicación"),
    NO_REGISTRADO("equipo no registrado en el padrón")
}

object Bitacora {

    data class Linea(val hora: Long, val codigo: String, val evento: Evento)

    private val _lineas = MutableStateFlow<List<Linea>>(emptyList())
    val lineas = _lineas.asStateFlow()

    private val formato = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun registrar(codigo: String, evento: Evento) {
        _lineas.value = _lineas.value + Linea(System.currentTimeMillis(), codigo, evento)
    }

    fun limpiar() { _lineas.value = emptyList() }

    fun hora(t: Long): String = formato.format(Date(t))

    fun csv(): String = buildString {
        appendLine("hora,codigo,evento")
        _lineas.value.forEach { appendLine("${hora(it.hora)},${it.codigo},${it.evento.name}") }
    }
}