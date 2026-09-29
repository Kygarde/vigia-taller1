package com.vigia.processing

import android.content.Context
import com.vigia.model.ContextSnapshot
import com.vigia.sensing.*
import kotlinx.coroutines.flow.*

class ContextManager(context: Context) {

    val accelerometer = AccelerometerProvider(context)
    val proximity = ProximityProvider(context)
    private val battery = BatteryProvider(context)
    private val connectivity = ConnectivityProvider(context)
    private val screen = ScreenStateProvider(context)
    private val processor = SignalProcessor()

    private val movement: Flow<Float> =
        accelerometer.samples.map { processor.process(it) }

    // Flujo inicial seguro para proximidad (por defecto asume que está cubierto/apoyado)
    private val proximityState: Flow<Boolean> = proximity.cerca
        .onStart { emit(true) }

    // Fusión física: acelerómetro en Z + sensor de proximidad
    private data class PosicionFisica(val bocaAbajo: Boolean, val cubierta: Boolean)
    private val posicionFisica: Flow<PosicionFisica> = combine(
        accelerometer.estaBocaAbajo,
        proximityState
    ) { bocaAbajo, cubierta ->
        PosicionFisica(bocaAbajo, cubierta)
    }

    // Combinación de 5 flujos tipados (máximo estándar y seguro de Coroutines)
    val snapshots: Flow<ContextSnapshot> = combine(
        movement,
        battery.state,
        connectivity.state,
        screen.state,
        posicionFisica
    ) { mov, bat, net, scr, pos ->
        ContextSnapshot(
            movementIndex = mov,
            batteryLevel = bat.level,
            batterySaver = bat.saver,
            wifiEnabled = net.wifi,
            mobileDataEnabled = net.mobile,
            screenOn = scr,
            estaBocaAbajo = pos.bocaAbajo,
            proximidadCubierta = pos.cubierta
        )
    }.sample(200)   // no emitir más rápido de 5 Hz

    fun start() {
        accelerometer.start()
        proximity.start()
    }

    fun stop() {
        accelerometer.stop()
        proximity.stop()
    }
}