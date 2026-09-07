package com.vigia.decision

import com.vigia.model.AdaptationDecision
import com.vigia.model.ContextSnapshot
import com.vigia.model.OperatingMode
import com.vigia.model.RiskLevel

class AdaptationEngine {

    private val riskEvaluator = RiskEvaluator()
    private var currentMode = OperatingMode.NORMAL
    private var lastChangeAt = 0L

    var linkAvailable: Boolean = true   // lo actualiza el transporte BLE

    fun decide(ctx: ContextSnapshot): AdaptationDecision {
        val risk = riskEvaluator.evaluate(ctx)

        val (target, reason) = when {
            !linkAvailable ->
                OperatingMode.DESCONECTADO to "Enlace no disponible: almacenando localmente"

            ctx.batterySaver || ctx.batteryLevel < AdaptationRules.BATTERY_LOW ->
                OperatingMode.AHORRO to "Batería ${ctx.batteryLevel}%: reduciendo frecuencia"

            risk == RiskLevel.ALERTA ->
                OperatingMode.INTENSIVO to "Movimiento sostenido: aumentando frecuencia"

            else ->
                OperatingMode.NORMAL to "Contexto estable"
        }

        val now = ctx.timestamp
        val locked = (now - lastChangeAt) < AdaptationRules.HYSTERESIS_MS

        if (target != currentMode && !locked) {
            currentMode = target
            lastChangeAt = now
            return AdaptationDecision(currentMode, risk, reason, now)
        }

        return AdaptationDecision(currentMode, risk, reason, now)
    }
}