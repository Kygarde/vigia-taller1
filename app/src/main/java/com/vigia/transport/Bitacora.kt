package com.vigia.transport

import android.content.Context
import com.vigia.data.BitacoraDbHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class Evento(val texto: String) {
    UNIDO("se unió al aula"),
    ALERTA("entró en ALERTA"),
    SIN_SENAL("dejó de emitir"),
    REGRESO("volvió a emitir"),
    SALIO_APP("salió de la aplicación"),
    NO_REGISTRADO("equipo no registrado en el padrón"),
    NO_BOCA_ABAJO("teléfono volteado o levantado (>3s)")
}

object Bitacora {

    data class Linea(val hora: Long, val codigo: String, val evento: Evento)

    private val _lineas = MutableStateFlow<List<Linea>>(emptyList())
    val lineas = _lineas.asStateFlow()

    private val formato = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val scope = CoroutineScope(Dispatchers.IO)
    private var dbHelper: BitacoraDbHelper? = null
    private var salaActual: Int = 0

    /** Configura el aula y la base de datos SQLite */
    fun inicializar(context: Context, sala: Int) {
        salaActual = sala
        dbHelper = BitacoraDbHelper.getInstance(context)
    }

    fun registrar(codigo: String, evento: Evento) {
        val ahora = System.currentTimeMillis()
        _lineas.value = _lineas.value + Linea(ahora, codigo, evento)

        // Persiste asíncronamente en la Base de Datos SQLite
        dbHelper?.let { helper ->
            scope.launch {
                helper.insertarEvento(salaActual, codigo, evento.name, ahora)
            }
        }
    }

    fun limpiar() {
        _lineas.value = emptyList()
        dbHelper?.let { helper ->
            scope.launch {
                helper.limpiarSala(salaActual)
            }
        }
    }

    fun hora(t: Long): String = formato.format(Date(t))

    fun csv(): String = buildString {
        appendLine("hora,codigo,evento")
        _lineas.value.forEach { appendLine("${hora(it.hora)},${it.codigo},${it.evento.name}") }
    }
}