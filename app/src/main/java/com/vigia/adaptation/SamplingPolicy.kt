package com.vigia.adaptation

import android.hardware.SensorManager
import com.vigia.model.OperatingMode
import com.vigia.sensing.AccelerometerProvider

data class SamplingConfig(
    val sensorDelay: Int,
    val emissionIntervalMs: Long,
    val secondarySensors: Boolean,
    val label: String
)

class SamplingPolicy(private val accelerometer: AccelerometerProvider) {

    private var current: SamplingConfig = configFor(OperatingMode.NORMAL)
    val currentConfig get() = current

    fun apply(mode: OperatingMode) {
        val config = configFor(mode)
        if (config == current) return
        current = config
        accelerometer.changeDelay(config.sensorDelay)   // reconfiguración en caliente
    }

    private fun configFor(mode: OperatingMode) = when (mode) {
        OperatingMode.NORMAL -> SamplingConfig(
            SensorManager.SENSOR_DELAY_NORMAL, 1_000L, true, "1 Hz")
        OperatingMode.INTENSIVO -> SamplingConfig(
            SensorManager.SENSOR_DELAY_GAME, 200L, true, "5 Hz")
        OperatingMode.AHORRO -> SamplingConfig(
            SensorManager.SENSOR_DELAY_NORMAL, 5_000L, false, "0.2 Hz")
        OperatingMode.DESCONECTADO -> SamplingConfig(
            SensorManager.SENSOR_DELAY_NORMAL, 1_000L, true, "1 Hz (buffer)")
    }
}