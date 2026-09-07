package com.vigia

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import com.vigia.transport.BleScanner
import com.vigia.ui.HomeScreen
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
            val nombre by viewModel.nombre.collectAsState()

            BackHandler(enabled = pantalla != Pantalla.INICIO) { viewModel.salir() }

            when (pantalla) {

                Pantalla.INICIO -> {
                    // Escucha las balizas de aula solo mientras estamos en el inicio,
                    // y solo si ya hay permisos: si no, el flujo naceria sordo.
                    val aulas by remember(ciclo, permisosOk) {
                        if (permisosOk) scanner.aulasAbiertas()
                        else kotlinx.coroutines.flow.flowOf(emptySet())
                    }.collectAsState(initial = emptySet())

                    HomeScreen(
                        nombreGuardado = nombre,
                        salaGuardada = sala,
                        aulasAbiertas = aulas,
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

                    StudentScreen(
                        ctx = snapshot,
                        decision = decision,
                        samplingLabel = samplingLabel,
                        idAlumno = viewModel.idAlumno,
                        emitiendo = emitiendo,
                        sala = sala,
                        nombre = nombre,
                        onSalir = viewModel::salir
                    )
                }

                Pantalla.DOCENTE -> {
                    val alumnos by remember(ciclo, permisosOk, sala) {
                        if (permisosOk) scanner.alumnos(sala)
                        else kotlinx.coroutines.flow.flowOf(emptyList())
                    }.collectAsState(initial = emptyList())

                    val estadoAnuncio by viewModel.estadoAnuncio.collectAsState()

                    TeacherScreen(
                        alumnos = alumnos,
                        bluetoothListo = btListo,
                        permisosOk = permisosOk,
                        estadoAnuncio = estadoAnuncio,
                        sala = sala,
                        onPedirPermisos = { pedirPermisos.launch(permisosNecesarios) },
                        onVolver = viewModel::salir
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // El usuario pudo conceder permisos o encender el Bluetooth desde Ajustes.
        cicloBluetooth.intValue++
        viewModel.reanunciarAula()
    }
}
