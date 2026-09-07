package com.vigia

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.vigia.ui.StudentScreen

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val permissions = buildList {
        if (android.os.Build.VERSION.SDK_INT >= 33)
            add(android.Manifest.permission.POST_NOTIFICATIONS)

        // --- Reservado para la transmision BLE al docente (siguiente iteracion) ---
        // La app no transmite ni usa ubicacion, asi que no se piden estos permisos.
        // Quedan declarados en el manifiesto y listos para reactivar aqui.
        //
        // if (android.os.Build.VERSION.SDK_INT >= 31) {
        //     add(android.Manifest.permission.BLUETOOTH_ADVERTISE)
        //     add(android.Manifest.permission.BLUETOOTH_SCAN)
        //     add(android.Manifest.permission.BLUETOOTH_CONNECT)
        // }
        // add(android.Manifest.permission.ACCESS_FINE_LOCATION)
    }.toTypedArray()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (permissions.isNotEmpty()) {
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}
                .launch(permissions)
        }

        // Arranca el servicio en primer plano: mantiene el sensado
        // vivo aunque el usuario minimice la app.
        startForegroundService(Intent(this, MonitoringService::class.java))

        setContent {
            val snapshot by viewModel.snapshot.collectAsState()
            val decision by viewModel.decision.collectAsState()
            val samplingLabel by viewModel.samplingLabel.collectAsState()

            StudentScreen(
                ctx = snapshot,
                decision = decision,
                samplingLabel = samplingLabel
            )
        }
    }
}