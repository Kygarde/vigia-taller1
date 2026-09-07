package com.vigia

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.vigia.transport.BleScanner
import com.vigia.ui.HomeScreen
import com.vigia.ui.StudentScreen
import com.vigia.ui.TeacherScreen

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private val scanner by lazy { BleScanner(this) }

    private val permissions = buildList {
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Solo la primera vez: al girar el equipo Android vuelve a entrar aqui y no
        // hace falta volver a pedir permisos ni relanzar el servicio.
        if (savedInstanceState == null) {
            if (permissions.isNotEmpty()) {
                registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}
                    .launch(permissions)
            }
            // Mantiene el sensado vivo aunque el usuario minimice la app.
            startForegroundService(Intent(this, MonitoringService::class.java))
        }

        setContent {
            // La pantalla activa vive en el ViewModel: sobrevive a la rotacion.
            val pantalla by viewModel.pantalla.collectAsState()
            val sala by viewModel.sala.collectAsState()
            val nombre by viewModel.nombre.collectAsState()

            // El boton atras del sistema devuelve al inicio en vez de cerrar la app.
            BackHandler(enabled = pantalla != Pantalla.INICIO) { viewModel.salir() }

            when (pantalla) {

                Pantalla.INICIO -> {
                    // Escucha las balizas de aula solo mientras estamos en el inicio.
                    val aulas by remember { scanner.aulasAbiertas() }
                        .collectAsState(initial = emptySet())

                    HomeScreen(
                        nombreGuardado = nombre,
                        salaGuardada = sala,
                        aulasAbiertas = aulas,
                        bluetoothListo = scanner.bluetoothListo,
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
                    // El escaneo arranca al entrar aqui y se detiene solo al salir:
                    // el Flow se cancela cuando esta pantalla deja de estar en composicion.
                    val alumnos by remember(sala) { scanner.alumnos(sala) }
                        .collectAsState(initial = emptyList())

                    val aulaAnunciada by viewModel.aulaAnunciada.collectAsState()

                    TeacherScreen(
                        alumnos = alumnos,
                        bluetoothListo = scanner.bluetoothListo,
                        sala = sala,
                        aulaAnunciada = aulaAnunciada,
                        onVolver = viewModel::salir
                    )
                }
            }
        }
    }
}
