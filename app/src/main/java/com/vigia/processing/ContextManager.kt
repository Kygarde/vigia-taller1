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

    val snapshots: Flow<ContextSnapshot> = combine(
        movement,
        battery.state,
        connectivity.state,
        screen.state,
        accelerometer.estaBocaAbajo,
        proximityState
    ) { args: Array<Any> ->
        val mov = args[0] as Float
        val bat = args[1] as BatteryState
        val net = args[2] as ConnectivityState
        val scr = args[3] as Boolean
        val bocaAbajo = args[4] as Boolean
        val cubierta = args[5] as Boolean

        ContextSnapshot(
            movementIndex = mov,
            batteryLevel = bat.level,
            batterySaver = bat.saver,
            wifiEnabled = net.wifi,
            mobileDataEnabled = net.mobile,
            screenOn = scr,
            estaBocaAbajo = bocaAbajo,
            proximidadCubierta = cubierta
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