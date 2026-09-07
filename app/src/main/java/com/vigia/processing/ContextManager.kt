package com.vigia.processing

import android.content.Context
import com.vigia.model.ContextSnapshot
import com.vigia.sensing.*
import kotlinx.coroutines.flow.*

class ContextManager(context: Context) {

    val accelerometer = AccelerometerProvider(context)
    private val battery = BatteryProvider(context)
    private val connectivity = ConnectivityProvider(context)
    private val screen = ScreenStateProvider(context)
    private val processor = SignalProcessor()

    private val movement: Flow<Float> =
        accelerometer.samples.map { processor.process(it) }

    val snapshots: Flow<ContextSnapshot> = combine(
        movement,
        battery.state,
        connectivity.state,
        screen.state
    ) { mov, bat, net, scr ->
        ContextSnapshot(
            movementIndex = mov,
            batteryLevel = bat.level,
            batterySaver = bat.saver,
            wifiEnabled = net.wifi,
            mobileDataEnabled = net.mobile,
            screenOn = scr
        )
    }.sample(200)   // no emitir más rápido de 5 Hz

    fun start() = accelerometer.start()
    fun stop() = accelerometer.stop()
}