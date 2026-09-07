package com.vigia.processing

class SignalProcessor {
    fun processSignal(data: List<Double>): Double {
        if (data.isEmpty()) return 0.0
        return data.average()
    }
}