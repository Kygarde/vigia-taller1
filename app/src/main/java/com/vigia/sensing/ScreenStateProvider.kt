package com.vigia.sensing

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow

/**
 * Pantalla encendida o apagada. RiskEvaluator solo eleva a ALERTA con la pantalla
 * encendida: agitar el celular con la pantalla apagada no es un indicio util.
 */
class ScreenStateProvider(private val context: Context) {

    private val powerManager = context.getSystemService(PowerManager::class.java)

    val state = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                trySend(intent?.action == Intent.ACTION_SCREEN_ON)
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }

        // Mismo motivo que en BatteryProvider: Android 14 exige declarar el flag.
        ContextCompat.registerReceiver(
            context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED
        )
        trySend(powerManager.isInteractive)

        awaitClose { context.unregisterReceiver(receiver) }
    }
}
