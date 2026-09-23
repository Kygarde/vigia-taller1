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

    // NUEVO: Flujo reactivo (Flow) para exponer el estado de orientación espacial.
    // Permite que las capas superiores (processing/decision) consuman la condición
    // posicional del teléfono sin acoplarse al manejo de hardware ni romper la emisión de samples.
    private val _estaBocaAbajo = MutableSharedFlow<Boolean>(extraBufferCapacity = 64)
    val estaBocaAbajo = _estaBocaAbajo.asSharedFlow()

    private var currentDelay = SensorManager.SENSOR_DELAY_NORMAL
    private var running = false

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val values = event.values.copyOf()
            _samples.tryEmit(values)

            // NUEVO: Discriminación matemática de la orientación del dispositivo.
            // En reposo boca abajo, el eje Z mide la aceleración gravitatoria en sentido negativo (~ -9.8 m/s²).
            // Se define un umbral de -8.5 m/s² para tolerar superficies imperfectas o ligeras inclinaciones.
            // Esto emite 'true' si el teléfono está boca abajo y 'false' en cualquier otra posición.
            val zAxis = values[2]
            _estaBocaAbajo.tryEmit(zAxis < -8.5f)
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