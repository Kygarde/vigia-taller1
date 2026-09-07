package com.vigia.model

data class ContextSnapshot(
    val timestamp: Long = System.currentTimeMillis(),
    val movementIndex: Float = 0f,   // 0.0 .. 1.0
    val batteryLevel: Int = 100,     // 0 .. 100
    val batterySaver: Boolean = false,
    val wifiEnabled: Boolean = false,
    val mobileDataEnabled: Boolean = false,
    val screenOn: Boolean = true
)

enum class RiskLevel { NORMAL, ATENCION, ALERTA }

enum class OperatingMode { NORMAL, INTENSIVO, AHORRO, DESCONECTADO }

data class AdaptationDecision(
    val mode: OperatingMode = OperatingMode.NORMAL,
    val risk: RiskLevel = RiskLevel.NORMAL,
    val reason: String = "Estado inicial",
    val timestamp: Long = System.currentTimeMillis()
)