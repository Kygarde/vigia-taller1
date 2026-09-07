package com.vigia.decision

class RiskEvaluator {
    fun evaluateRisk(processedValue: Double): String {
        return when {
            processedValue > 80.0 -> "HIGH"
            processedValue > 50.0 -> "MEDIUM"
            else -> "LOW"
        }
    }
}