package com.vigia

import android.app.Application
import android.os.PowerManager
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vigia.BuildConfig
import com.vigia.adaptation.SamplingPolicy
import com.vigia.decision.AdaptationEngine
import com.vigia.model.*
import com.vigia.processing.ContextManager
import com.vigia.transport.BleAdvertiser
import com.vigia.transport.BloqueoStore
import com.vigia.transport.EstadoAnuncio
import com.vigia.transport.PacketCodec
import com.vigia.transport.SesionExamen
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

    /**
     * true si el equipo salio de la app durante el examen y quedo bloqueado.
     * Sobrevive a cerrar y reabrir: solo el docente lo levanta, con su PIN.
     */
    private val _bloqueado = MutableStateFlow(BloqueoStore.leer(app))
    val bloqueado = _bloqueado.asStateFlow()

    private val powerManager = app.getSystemService(PowerManager::class.java)
    private var enPrimerPlano = true

    /**
     * true solo cuando se confirmo que el alumno se fue a OTRA aplicacion.
     *
     * Distinto de enPrimerPlano: la app tambien deja de estar al frente cuando se
     * apaga la pantalla, y eso no es irse. El panel del docente muestra esto, asi que
     * la diferencia no es cosmetica: es un senalamiento contra el alumno.
     */
    private var fueraDeLaApp = false

    /**
     * La Activity avisa cuando la app deja de estar visible.
     *
     * Se usa onStop y no onPause: onPause tambien se dispara con el dialogo de
     * permisos o al bajar la barra de notificaciones, y serian falsos positivos.
     *
     * Y se espera antes de decidir: si lo que paso fue que se apago la pantalla,
     * para entonces isInteractive ya es false y no se bloquea. Bloquear el celular
     * NO es salir de la app.
     */
    fun appVisible(visible: Boolean) {
        enPrimerPlano = visible
        if (!_unido.value) return

        if (visible) {
            if (fueraDeLaApp) {
                fueraDeLaApp = false
                forzarEmision()
            }
            return
        }

        viewModelScope.launch {
            delay(ESPERA_ANTES_DE_BLOQUEAR_MS)
            // Apagar la pantalla NO es salir de la aplicacion. Por eso se confirma
            // con isInteractive antes de marcar nada: si la pantalla ya esta apagada,
            // el alumno bloqueo su celular, no se fue a otro lado.
            if (!enPrimerPlano && powerManager.isInteractive) {
                fueraDeLaApp = true
                _bloqueado.value = true
                BloqueoStore.guardar(getApplication(), true)
                forzarEmision()
            }
        }
    }

    /** Solo con el PIN que el docente eligio para ESTA aula, y presencialmente. */
    fun desbloquear(pin: String): Boolean {
        val esperada = BloqueoStore.leerHuella(getApplication())
        if (esperada == -1) return false
        if (PacketCodec.huellaPin(pin, _sala.value) != esperada) return false
        _bloqueado.value = false
        fueraDeLaApp = false
        BloqueoStore.guardar(getApplication(), false)
        // Si seguia dentro del examen, vuelve a su pantalla, no al inicio.
        if (_unido.value) _pantalla.value = Pantalla.ALUMNO
        forzarEmision()
        return true
    }

    /**
     * Publica el estado YA, sin esperar al intervalo del modo ni a que el sensor
     * entregue una muestra nueva.
     *
     * Hace falta porque emitirSiCorresponde solo se llama desde el flujo del
     * acelerometro, y varios fabricantes estrangulan los sensores en segundo plano.
     * Un cambio como "salio de la app" no puede quedar esperando ese latido.
     */
    private fun forzarEmision() {
        ultimaEmision = 0L
        ultimoPaquete = null
        emitirSiCorresponde(_snapshot.value, _decision.value)
    }

    /**
     * El alumno entra al examen.
     *
     * Se queda con la huella del PIN que venia en la baliza de esa aula: es lo que le
     * permitira validar el PIN del docente si termina bloqueado, sin haber conocido
     * nunca los cuatro digitos.
     */
    fun unirseComoAlumno(codigo: String, sala: Int, huellaPin: Int) {
        if (_bloqueado.value) return      // un equipo bloqueado no vuelve al examen
        _examenTerminado.value = false
        val app = getApplication<Application>()
        EquipoStore.guardarCodigo(app, codigo)
        EquipoStore.guardarSala(app, sala)
        BloqueoStore.guardarHuella(app, huellaPin)
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

    /** Margen para distinguir "se apago la pantalla" de "se fue a otra app". */
    private val ESPERA_ANTES_DE_BLOQUEAR_MS = 700L

    /** Cuanto se repite el aviso de cierre, para que no se pierda ningun alumno. */
    private val AVISO_DE_CIERRE_MS = 12_000L

    /** PIN de desbloqueo del examen en curso. Lo elige el docente al abrir el aula. */
    private val _pinAula = MutableStateFlow("")
    val pinAula = _pinAula.asStateFlow()

    fun abrirPanelDocente(sala: Int, pin: String) {
        // Cinturon de seguridad: aunque el boton no exista en la variante de alumno,
        // esta puerta queda cerrada por dentro.
        if (!BuildConfig.ES_DOCENTE) return
        val app = getApplication<Application>()
        EquipoStore.guardarSala(app, sala)
        _sala.value = EquipoStore.leerSala(app)
        _pinAula.value = pin
        dejarDeAnunciar()
        advertiser.publish(PacketCodec.encodeAula(sala, PacketCodec.huellaPin(pin, sala)))
        olvidarMiAula?.cancel()
        _miAula.value = sala
        _pantalla.value = Pantalla.DOCENTE
    }

    /**
     * true cuando al alumno se le acabo el examen porque el aula dejo de anunciarse.
     * Es lo unico que le dice que termino: el docente no tiene canal de vuelta.
     */
    private val _examenTerminado = MutableStateFlow(false)
    val examenTerminado = _examenTerminado.asStateFlow()

    /**
     * La baliza del aula desaparecio: el docente cerro el examen.
     *
     * NO levanta el bloqueo, a proposito. Si bastara con que el aula dejara de verse,
     * al alumno bloqueado le alcanzaria con alejarse treinta segundos del salon para
     * volver limpio, y el mecanismo entero seria decorativo. El bloqueo lo levanta el
     * docente con su PIN, presencialmente, que es lo que dice el protocolo.
     */
    fun avisarExamenTerminado() {
        if (!_unido.value) return
        dejarDeAnunciar()

        // Un equipo bloqueado deja de emitir —el examen acabo, no hay a quien
        // reportarle— pero sigue bloqueado. Son dos cosas distintas: una es gastar
        // radio y bateria para nada, la otra es la sancion, que solo levanta el PIN.
        if (_bloqueado.value) return

        _examenTerminado.value = true
        _pantalla.value = Pantalla.INICIO
    }

    /** El docente cierra el examen: deja de anunciar y borra la sesion. */
    fun finalizarExamen() {
        if (!BuildConfig.ES_DOCENTE) return
        SesionExamen.finalizar()
        val salaCerrada = _sala.value
        _pinAula.value = ""          // el PIN muere con el examen
        _unido.value = false
        ultimoPaquete = null
        _emitiendo.value = false
        _pantalla.value = Pantalla.INICIO

        // En vez de callarse de golpe, anuncia el cierre unos segundos: asi los
        // alumnos salen en el acto en vez de esperar a que caduque la baliza.
        advertiser.publish(PacketCodec.encodeCierre(salaCerrada))
        viewModelScope.launch {
            delay(AVISO_DE_CIERRE_MS)
            runCatching { advertiser.stop() }
        }

        // El aula sigue contando como "mia" mientras mi baliza pueda seguir en el
        // aire: si el docente reabre el mismo codigo de inmediato, no debe toparse
        // con su propio eco.
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
            advertiser.publish(
                PacketCodec.encodeAula(
                    _sala.value,
                    PacketCodec.huellaPin(_pinAula.value, _sala.value)
                )
            )
        }
    }

    private var ultimaEmision = 0L
    private var ultimoPaquete: ByteArray? = null

    init {
        // Un equipo bloqueado sigue reportandose aunque cierren y reabran la app.
        // Si dejara de emitir, el docente lo veria como "sin senal" —que puede ser
        // alcance o bateria— en vez de "bloqueado", que es un senalamiento concreto.
        // Cerrar la app no puede ser la forma facil de borrar el rastro.
        if (_bloqueado.value && _codigo.value.isNotBlank()) _unido.value = true

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

        // El contexto lleva si la app esta al frente; el bloqueo va aparte.
        // Un equipo bloqueado SIGUE emitiendo: si dejara de hacerlo, el docente lo
        // veria como "sin senal" en vez de "salio de la app", que es otra cosa.
        val ctxConFoco = ctx.copy(appEnPrimerPlano = !fueraDeLaApp)
        val paquete = PacketCodec.encodeAlumno(
            idAlumno, _sala.value, _codigo.value, ctxConFoco, d, _bloqueado.value
        )
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
