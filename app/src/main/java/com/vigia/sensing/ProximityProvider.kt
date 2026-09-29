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
            _cerca.tryEmit(distancia < maxRange)
        }
        override fun onAccuracyChanged(s: Sensor?, accuracy: Int) {}
    }

    fun start() {
        val s = sensor ?: return
        if (running) return
        sensorManager?.registerListener(listener, s, SensorManager.SENSOR_DELAY_NORMAL)
        running = true
    }

    fun stop() {
        if (!running) return
        sensorManager?.unregisterListener(listener)
        running = false
    }
}