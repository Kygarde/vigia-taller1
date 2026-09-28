package com.vigia.sensing

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class ProximityProvider(context: Context) {

    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val sensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)

    private val _cerca = MutableSharedFlow<Boolean>(extraBufferCapacity = 64)
    val cerca = _cerca.asSharedFlow()

    private var running = false
    private val maxRange = sensor?.maximumRange ?: 5f

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val distancia = event.values[0]
            // Si la distancia es menor al rango máximo, está cubierto contra la mesa
            _cerca.tryEmit(distancia < maxRange)
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    fun start() {
        if (running || sensor == null) return
        sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        running = true
    }

    fun stop() {
        if (!running) return
        sensorManager.unregisterListener(listener)
        running = false
    }
}