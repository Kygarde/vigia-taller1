package com.vigia

import android.app.Application
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vigia.adaptation.SamplingPolicy
import com.vigia.decision.AdaptationEngine
import com.vigia.model.*
import com.vigia.processing.ContextManager
import com.vigia.transport.BleAdvertiser
import com.vigia.transport.PacketCodec
import com.vigia.transport.EquipoStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

/** Pantalla activa. Vive en el ViewModel para sobrevivir a la rotacion. */
enum class Pantalla { INICIO, ALUMNO, DOCENTE }

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val contextManager = ContextManager(app)
    private val engine = AdaptationEngine()
    private val samplingPolicy = SamplingPolicy(contextManager.accelerometer)
    private val advertiser = BleAdvertiser(app)

    /** Identificador estable del equipo, derivado del ANDROID_ID. No identifica a la persona. */
    val idAlumno: Int = run {
        val android = Settings.Secure.getString(app.contentResolver, Settings.Secure.ANDROID_ID)
        (android?.hashCode() ?: 0).absoluteValue % 10_000
    }

    private val _snapshot = MutableStateFlow(ContextSnapshot())
    val snapshot = _snapshot.asStateFlow()

    private val _decision = MutableStateFlow(AdaptationDecision())
    val decision = _decision.asStateFlow()

    private val _samplingLabel = MutableStateFlow("1 Hz")
    val samplingLabel = _samplingLabel.asStateFlow()

    /** true cuando el equipo esta anunciando su estado por BLE. */
    private val _emitiendo = MutableStateFlow(false)
    val emitiendo = _emitiendo.asStateFlow()

    /** Codigo de aula: separa este salon de otros que esten cerca. */
    private val _sala = MutableStateFlow(EquipoStore.leerSala(app))
    val sala = _sala.asStateFlow()

    /** Nombre que el docente ve en su panel. Vacio = se muestra el numero de equipo. */
    private val _nombre = MutableStateFlow(EquipoStore.leerNombre(app))
    val nombre = _nombre.asStateFlow()

    /** true solo mientras el alumno esta dentro del examen. */
    private val _unido = MutableStateFlow(false)
    val unido = _unido.asStateFlow()

    /**
     * Pantalla activa. Al estar aqui y no en un remember de la Activity, la app
     * no pierde el sitio cuando Android recrea la Activity al girar el equipo.
     */
    private val _pantalla = MutableStateFlow(Pantalla.INICIO)
    val pantalla = _pantalla.asStateFlow()

    /** El alumno entra al examen: recien aqui el equipo empieza a anunciarse. */
    fun unirseComoAlumno(nombre: String, sala: Int) {
        val app = getApplication<Application>()
        EquipoStore.guardarNombre(app, nombre)
        EquipoStore.guardarSala(app, sala)
        _nombre.value = EquipoStore.leerNombre(app)
        _sala.value = EquipoStore.leerSala(app)
        ultimoPaquete = null
        _unido.value = true
        _pantalla.value = Pantalla.ALUMNO
    }

    /** true si la baliza del aula esta saliendo al aire. */
    private val _aulaAnunciada = MutableStateFlow(false)
    val aulaAnunciada = _aulaAnunciada.asStateFlow()

    /**
     * El docente abre su panel. No anuncia estado, pero si emite la baliza del aula:
     * es la unica forma de que los alumnos sepan que ese salon existe.
     */
    fun abrirPanelDocente(sala: Int) {
        val app = getApplication<Application>()
        EquipoStore.guardarSala(app, sala)
        _sala.value = EquipoStore.leerSala(app)
        dejarDeAnunciar()
        _aulaAnunciada.value =
            runCatching { advertiser.publish(PacketCodec.encodeAula(sala)) }.isSuccess
        _pantalla.value = Pantalla.DOCENTE
    }

    /** Vuelve al inicio y deja de anunciar. */
    fun salir() {
        dejarDeAnunciar()
        _pantalla.value = Pantalla.INICIO
    }

    private fun dejarDeAnunciar() {
        _unido.value = false
        ultimoPaquete = null
        _emitiendo.value = false
        _aulaAnunciada.value = false
        runCatching { advertiser.stop() }
    }

    private var ultimaEmision = 0L
    private var ultimoPaquete: ByteArray? = null

    init {
        contextManager.start()
        viewModelScope.launch {
            contextManager.snapshots.collect { ctx ->
                _snapshot.value = ctx
                val d = engine.decide(ctx)            // DECISION
                _decision.value = d
                samplingPolicy.apply(d.mode)          // ADAPTACION
                _samplingLabel.value = samplingPolicy.currentConfig.label
                emitirSiCorresponde(ctx, d)           // TRANSPORTE
            }
        }
    }

    /**
     * Anuncia el estado por BLE respetando el intervalo del modo activo.
     * Ese intervalo lo fija SamplingPolicy: 1 s en NORMAL, 200 ms en INTENSIVO,
     * 5 s en AHORRO. Cambiarlo es parte de la adaptacion A1.
     */
    private fun emitirSiCorresponde(ctx: ContextSnapshot, d: AdaptationDecision) {
        if (!_unido.value) return            // fuera del examen no se anuncia nada

        val intervalo = samplingPolicy.currentConfig.emissionIntervalMs
        val ahora = System.currentTimeMillis()
        if (ahora - ultimaEmision < intervalo) return

        val paquete = PacketCodec.encodeAlumno(idAlumno, _sala.value, _nombre.value, ctx, d)
        // Reanunciar lo mismo solo gasta bateria: el anuncio anterior sigue vigente.
        if (ultimoPaquete?.contentEquals(paquete) == true) return

        ultimaEmision = ahora
        ultimoPaquete = paquete

        // Sin permiso de Bluetooth o con el Bluetooth apagado esto lanza excepcion:
        // la app debe seguir funcionando igual, solo sin transmitir.
        // Deliberadamente NO se toca engine.linkAvailable aqui: el modo DESCONECTADO
        // tiene prioridad sobre todos los demas, asi que un fallo puntual del anuncio
        // dejaria la app clavada en DESCONECTADO y ocultaria A1 y A2 por completo.
        _emitiendo.value = runCatching { advertiser.publish(paquete) }.isSuccess
    }

    override fun onCleared() {
        contextManager.stop()
        runCatching { advertiser.stop() }
    }
}
