package com.vigia.transport

import android.content.Context
import com.vigia.data.EventoEntity
import com.vigia.data.VigiaDatabase
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
    private var appContext: Context? = null
    private var salaActual: Int = 0

    /** Configura el contexto de la aplicación y el aula activa para Room. */
    fun inicializar(context: Context, sala: Int) {
        appContext = context.applicationContext
        salaActual = sala
    }

    fun registrar(codigo: String, evento: Evento) {
        val ahora = System.currentTimeMillis()
        _lineas.value = _lineas.value + Linea(ahora, codigo, evento)

        // Persistencia asíncrona en Room Database
        appContext?.let { ctx ->
            scope.launch {
                val dao = VigiaDatabase.getDatabase(ctx).bitacoraDao()
                dao.insertar(
                    EventoEntity(
                        sala = salaActual,
                        codigoAlumno = codigo,
                        tipoEvento = evento.name,
                        descripcion = evento.texto,
                        timestamp = ahora
                    )
                )
            }
        }
    }

    fun limpiar() {
        _lineas.value = emptyList()
        appContext?.let { ctx ->
            scope.launch {
                VigiaDatabase.getDatabase(ctx).bitacoraDao().limpiarSala(salaActual)
            }
        }
    }

    fun hora(t: Long): String = formato.format(Date(t))

    fun csv(): String = buildString {
        appendLine("hora,codigo,evento")
        _lineas.value.forEach { appendLine("${hora(it.hora)},${it.codigo},${it.evento.name}") }
    }
}