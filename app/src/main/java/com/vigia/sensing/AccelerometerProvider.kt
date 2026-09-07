package com.vigia.sensing

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class AccelerometerProvider(context: Context) {

    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val sensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val _samples = MutableSharedFlow<FloatArray>(extraBufferCapacity = 64)
    val samples = _samples.asSharedFlow()

    private var currentDelay = SensorManager.SENSOR_DELAY_NORMAL
    private var running = false

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            _samples.tryEmit(event.values.copyOf())
        }
        override fun onAccuracyChanged(s: Sensor?, accuracy: Int) {}
    }

    fun start() {
        if (running || sensor == null) return
        sensorManager.registerListener(listener, sensor, currentDelay)
        running = true
    }

    fun stop() {
        if (!running) return
        sensorManager.unregisterListener(listener)
        running = false
    }

    /** Re-registra el listener con otra frecuencia. Es la adaptacion A1. */
    fun changeDelay(newDelay: Int) {
        if (newDelay == currentDelay) return
        currentDelay = newDelay
        if (running) { stop(); start() }
    }
}
