package com.vigia

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import com.vigia.transport.BleScanner
import com.vigia.ui.HomeScreen
import com.vigia.ui.PantallaBloqueada
import com.vigia.ui.StudentScreen
import com.vigia.ui.TeacherScreen

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private val scanner by lazy { BleScanner(this) }

    /**
     * Contador que fuerza a rehacer los flujos de Bluetooth.
     *
     * Hace falta porque el escaneo puede arrancar antes de que el usuario conceda
     * los permisos: en ese caso startScan lanza SecurityException, el flujo queda
     * vivo pero sin escuchar nada, y no se recupera solo. Al conceder los permisos
     * o al volver a la app subimos este contador y los flujos se crean de nuevo.
     */
    private val cicloBluetooth = mutableIntStateOf(0)

    private val permisosNecesarios = buildList {
        if (android.os.Build.VERSION.SDK_INT >= 33)
            add(android.Manifest.permission.POST_NOTIFICATIONS)

        // Necesarios para anunciar el estado del equipo y para escuchar el de los demas.
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            add(android.Manifest.permission.BLUETOOTH_ADVERTISE)
            add(android.Manifest.permission.BLUETOOTH_SCAN)
            add(android.Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            // Antes de Android 12 el escaneo BLE exigia permiso de ubicacion.
            add(android.Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }.toTypedArray()

    /** Los que de verdad hacen falta para el Bluetooth (las notificaciones no cuentan). */
    private val permisosBle = permisosNecesarios
        .filter { it != android.Manifest.permission.POST_NOTIFICATIONS }

    private fun permisosBleConcedidos(): Boolean = permisosBle.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    // Registrado como campo: debe crearse antes de que la Activity arranque.
    private val pedirPermisos =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            // Con los permisos ya resueltos, rehacer escaneo y anuncio.
            cicloBluetooth.intValue++
            viewModel.reanunciarAula()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Solo la primera vez: al girar el equipo Android vuelve a entrar aqui.
        if (savedInstanceState == null) {
            if (!permisosBleConcedidos()) pedirPermisos.launch(permisosNecesarios)
            // Mantiene el sensado vivo aunque el usuario minimice la app.
            startForegroundService(Intent(this, MonitoringService::class.java))
        }

        setContent {
            // Leer el contador suscribe esta composicion a sus cambios.
            val ciclo = cicloBluetooth.intValue
            val permisosOk = remember(ciclo) { permisosBleConcedidos() }
            val btListo = remember(ciclo) { scanner.bluetoothListo }

            val pantalla by viewModel.pantalla.collectAsState()
            val sala by viewModel.sala.collectAsState()
            val codigo by viewModel.codigo.collectAsState()
            val miAula by viewModel.miAula.collectAsState()
            val bloqueado by viewModel.bloqueado.collectAsState()
            val examenTerminado by viewModel.examenTerminado.collectAsState()
            val pinAula by viewModel.pinAula.collectAsState()

            // Dentro del examen el boton atras no hace nada: el alumno no puede
            // irse por su cuenta y el docente cierra con "Finalizar examen". Si
            // bastara con atras, el bloqueo por salir de la app no tendria sentido.
            BackHandler(enabled = pantalla != Pantalla.INICIO || bloqueado) { }

            // El bloqueo NO es una pantalla del examen: es un estado de la app.
            // Va antes de toda la navegacion, para que siga puesto aunque el alumno
            // cierre la app y la vuelva a abrir desde cero.
            if (bloqueado) {
                // Aqui tambien se vigila el aula, pero solo para dejar de emitir:
                // el bloqueo no se levanta solo. Sin esto, un equipo bloqueado se
                // quedaria anunciando dias despues de terminado el examen.
                val aulasB by remember(ciclo, permisosOk) {
                    if (permisosOk) scanner.aulasAbiertas()
                    else kotlinx.coroutines.flow.flowOf(emptyMap())
                }.collectAsState(initial = emptyMap())
                val snapshotB by viewModel.snapshot.collectAsState()

                VigilarFinDelExamen(
                    aulaViva = sala in aulasB,
                    permisosOk = permisosOk,
                    pantallaEncendida = snapshotB.screenOn,
                    onTerminado = viewModel::avisarExamenTerminado
                )

                PantallaBloqueada(codigo, viewModel::desbloquear)
            } else when (pantalla) {

                Pantalla.INICIO -> {
                    // Escucha las balizas de aula solo mientras estamos en el inicio,
                    // y solo si ya hay permisos: si no, el flujo naceria sordo.
                    val aulas by remember(ciclo, permisosOk) {
                        if (permisosOk) scanner.aulasAbiertas()
                        else kotlinx.coroutines.flow.flowOf(emptyMap())
                    }.collectAsState(initial = emptyMap())

                    HomeScreen(
                        codigoGuardado = codigo,
                        salaGuardada = sala,
                        aulasAbiertas = aulas,
                        miAula = miAula,
                        examenTerminado = examenTerminado,
                        bluetoothListo = btListo,
                        permisosOk = permisosOk,
                        onPedirPermisos = { pedirPermisos.launch(permisosNecesarios) },
                        onEntrarComoAlumno = viewModel::unirseComoAlumno,
                        onEntrarComoDocente = viewModel::abrirPanelDocente
                    )
                }

                Pantalla.ALUMNO -> {
                    val snapshot by viewModel.snapshot.collectAsState()
                    val decision by viewModel.decision.collectAsState()
                    val samplingLabel by viewModel.samplingLabel.collectAsState()
                    val emitiendo by viewModel.emitiendo.collectAsState()

                    // Un aula existe mientras el docente la anuncia. Si la baliza
                    // desaparece, el examen termino: es el unico aviso que el alumno
                    // puede recibir, porque el advertising va en un solo sentido.
                    val aulas by remember(ciclo, permisosOk) {
                        if (permisosOk) scanner.aulasAbiertas()
                        else kotlinx.coroutines.flow.flowOf(emptyMap())
                    }.collectAsState(initial = emptyMap())

                    VigilarFinDelExamen(
                        aulaViva = sala in aulas,
                        permisosOk = permisosOk,
                        pantallaEncendida = snapshot.screenOn,
                        onTerminado = viewModel::avisarExamenTerminado
                    )

                    StudentScreen(
                        ctx = snapshot,
                        decision = decision,
                        samplingLabel = samplingLabel,
                        idAlumno = viewModel.idAlumno,
                        emitiendo = emitiendo,
                        sala = sala,
                        codigo = codigo
                    )
                }

                Pantalla.DOCENTE -> {
                    // Android estrangula el escaneo BLE con la pantalla apagada: el
                    // panel se quedaria ciego y los alumnos se caerian de la lista.
                    // El flag solo aplica mientras esta pantalla esta al frente.
                    DisposableEffect(Unit) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        onDispose {
                            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        }
                    }

                    val alumnos by remember(ciclo, permisosOk, sala) {
                        if (permisosOk) scanner.alumnos(sala)
                        else kotlinx.coroutines.flow.flowOf(emptyList())
                    }.collectAsState(initial = emptyList())

                    // Aulas anunciadas que no son la mia: otro panel activo cerca.
                    val aulas by remember(ciclo, permisosOk) {
                        if (permisosOk) scanner.aulasAbiertas()
                        else kotlinx.coroutines.flow.flowOf(emptyMap())
                    }.collectAsState(initial = emptyMap())

                    val estadoAnuncio by viewModel.estadoAnuncio.collectAsState()

                    TeacherScreen(
                        alumnos = alumnos,
                        bluetoothListo = btListo,
                        permisosOk = permisosOk,
                        estadoAnuncio = estadoAnuncio,
                        sala = sala,
                        pinAula = pinAula,
                        otrasAulas = aulas.keys - sala,
                        onPedirPermisos = { pedirPermisos.launch(permisosNecesarios) },
                        onFinalizar = viewModel::finalizarExamen
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.appVisible(true)
    }

    override fun onStop() {
        super.onStop()
        // onStop = la app dejo de estar visible. El ViewModel decide si eso fue
        // irse a otra app (bloquea) o solo apagar la pantalla (no bloquea).
        viewModel.appVisible(false)
    }

    override fun onResume() {
        super.onResume()
        // El usuario pudo conceder permisos o encender el Bluetooth desde Ajustes.
        cicloBluetooth.intValue++
        viewModel.reanunciarAula()
    }
}

/**
 * Libera el equipo cuando el aula deja de anunciarse.
 *
 * Dos condiciones que parecen detalles y no lo son:
 *
 * La espera es corta porque la baliza YA caduca a los 15 s en el escaner. Sumar otros
 * 25 daba casi 40 segundos entre que el docente finaliza y el alumno se entera.
 *
 * Y solo cuenta con la pantalla encendida: Android estrangula el escaneo BLE con la
 * pantalla apagada, asi que un alumno que bloquea su celular deja de ver la baliza
 * aunque el examen siga. Sin esta condicion lo estariamos echando del examen por
 * apagar la pantalla.
 */
@Composable
private fun VigilarFinDelExamen(
    aulaViva: Boolean,
    permisosOk: Boolean,
    pantallaEncendida: Boolean,
    onTerminado: () -> Unit
) {
    // No se echa a nadie por un aula que nunca llegamos a ver. El flujo de escaneo
    // se recrea en cada onResume y arranca con el conjunto vacio: sin esta guarda,
    // volver a la app echaria al alumno del examen ocho segundos despues.
    val vistaAlgunaVez = remember { mutableStateOf(false) }
    LaunchedEffect(aulaViva) { if (aulaViva) vistaAlgunaVez.value = true }

    LaunchedEffect(aulaViva, permisosOk, pantallaEncendida, vistaAlgunaVez.value) {
        if (aulaViva || !permisosOk || !pantallaEncendida || !vistaAlgunaVez.value) {
            return@LaunchedEffect
        }
        kotlinx.coroutines.delay(8_000)
        onTerminado()
    }
}
