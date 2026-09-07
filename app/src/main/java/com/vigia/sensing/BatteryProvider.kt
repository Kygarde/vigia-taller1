package com.vigia.sensing

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow

data class BatteryState(val level: Int, val saver: Boolean)

/**
 * Nivel de bateria y modo ahorro del sistema. Es la entrada de la adaptacion A2.
 */
class BatteryProvider(private val context: Context) {

    private val powerManager = context.getSystemService(PowerManager::class.java)

    val state = callbackFlow {

        // Ultimo nivel conocido. ACTION_POWER_SAVE_MODE_CHANGED no trae el nivel,
        // asi que sin esto el porcentaje saltaria a 100 al activar el ahorro.
        var lastLevel = 100

        fun read(intent: Intent?): BatteryState {
            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            if (level >= 0 && scale > 0) {
                lastLevel = level * 100 / scale
            }
            return BatteryState(lastLevel, powerManager.isPowerSaveMode)
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) { trySend(read(intent)) }
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        }

        // ContextCompat + RECEIVER_NOT_EXPORTED: obligatorio desde Android 14 (targetSdk >= 34).
        // Con context.registerReceiver(receiver, filter) a secas la app crashea al arrancar.
        val sticky = ContextCompat.registerReceiver(
            context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED
        )
        trySend(read(sticky))

        awaitClose { context.unregisterReceiver(receiver) }
    }
}
