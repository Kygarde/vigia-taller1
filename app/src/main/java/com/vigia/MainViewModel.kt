package com.vigia

import android.app.Application
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vigia.BuildConfig
import com.vigia.adaptation.SamplingPolicy
import com.vigia.decision.AdaptationEngine
import com.vigia.model.*
import com.vigia.processing.ContextManager
import com.vigia.transport.BleAdvertiser
import com.vigia.transport.EstadoAnuncio
import com.vigia.transport.PacketCodec
import com.vigia.transport.EquipoStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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

    /** Codigo del alumno guardado para la sesion. */
    private val _codigo = MutableStateFlow(EquipoStore.leerCodigo(app))
    val codigo = _codigo.asStateFlow()

    /** true solo mientras el alumno esta dentro del examen. */
    private val _unido = MutableStateFlow(false)
    val unido = _unido.asStateFlow()

    /**
     * Pantalla activa. Al estar aqui y no en un remember de la Activity, la app
     * no pierde el sitio cuando Android recrea la Activity al girar el equipo.
     */
    private val _pantalla = MutableStateFlow(Pantalla.INICIO)
    val pantalla = _pantalla.asStateFlow()

    fun unirseComoAlumno(codigo: String, sala: Int) {
        val app = getApplication<Application>()
        EquipoStore.guardarCodigo(app, codigo)
        EquipoStore.guardarSala(app, sala)
        _codigo.value = EquipoStore.leerCodigo(app)
        _sala.value = EquipoStore.leerSala(app)
        ultimoPaquete = null
        _unido.value = true
        _pantalla.value = Pantalla.ALUMNO
    }

    /** Estado real del anuncio, reportado por el propio Bluetooth. */
    val estadoAnuncio = advertiser.estado

    /**
     * El docente abre su panel. No anuncia estado, pero si emite la baliza del aula:
     * es la unica forma de que los alumnos sepan que ese salon existe.
     */
    /**
     * Aula que ESTE equipo esta anunciando, o anuncio hace muy poco.
     *
     * Al salir del panel el anuncio se detiene, pero la baliza que ya salio sigue
     * viajando y el escaner la sigue viendo hasta que caduca. Sin esta memoria, el
     * docente que cierra y reabre su panel se bloquearia a si mismo con su propio eco.
     */
    private val _miAula = MutableStateFlow<Int?>(null)
    val miAula = _miAula.asStateFlow()

    private var olvidarMiAula: Job? = null

    /** Un poco mas que la caducidad del escaner (15 s), para cubrir el eco completo. */
    private val MEMORIA_MI_AULA_MS = 20_000L

    fun abrirPanelDocente(sala: Int) {
        // Cinturon de seguridad: aunque el boton no exista en la variante de alumno,
        // esta puerta queda cerrada por dentro.
        if (!BuildConfig.ES_DOCENTE) return
        val app = getApplication<Application>()
        EquipoStore.guardarSala(app, sala)
        _sala.value = EquipoStore.leerSala(app)
        dejarDeAnunciar()
        advertiser.publish(PacketCodec.encodeAula(sala))
        olvidarMiAula?.cancel()
        _miAula.value = sala
        _pantalla.value = Pantalla.DOCENTE
    }

    /** Vuelve al inicio y deja de anunciar. */
    fun salir() {
        dejarDeAnunciar()
        _pantalla.value = Pantalla.INICIO

        // El aula sigue siendo "mia" mientras mi baliza pueda seguir en el aire.
        olvidarMiAula?.cancel()
        if (_miAula.value != null) {
            olvidarMiAula = viewModelScope.launch {
                delay(MEMORIA_MI_AULA_MS)
                _miAula.value = null
            }
        }
    }

    private fun dejarDeAnunciar() {
        _unido.value = false
        ultimoPaquete = null
        _emitiendo.value = false
        runCatching { advertiser.stop() }
    }

    /** Reintenta el anuncio del aula: se usa al volver de conceder permisos. */
    fun reanunciarAula() {
        if (_pantalla.value == Pantalla.DOCENTE &&
            advertiser.estado.value !is EstadoAnuncio.Anunciando
        ) {
            advertiser.publish(PacketCodec.encodeAula(_sala.value))
        }
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

        val paquete = PacketCodec.encodeAlumno(idAlumno, _sala.value, _codigo.value, ctx, d)
        // Reanunciar lo mismo solo gasta bateria: el anuncio anterior sigue vigente.
        if (ultimoPaquete?.contentEquals(paquete) == true) return

        ultimaEmision = ahora
        ultimoPaquete = paquete

        // Deliberadamente NO se toca engine.linkAvailable aqui: el modo DESCONECTADO
        // tiene prioridad sobre todos los demas, asi que un fallo puntual del anuncio
        // dejaria la app clavada en DESCONECTADO y ocultaria A1 y A2 por completo.
        advertiser.publish(paquete)
        _emitiendo.value = advertiser.estado.value !is EstadoAnuncio.Detenido
    }

    override fun onCleared() {
        contextManager.stop()
        runCatching { advertiser.stop() }
    }
}
