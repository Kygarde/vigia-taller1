package com.vigia.decision

import com.vigia.model.ContextSnapshot
import com.vigia.model.RiskLevel

class RiskEvaluator {

    private var aboveSince: Long? = null
    private var notFaceDownSince: Long? = null

    fun evaluate(ctx: ContextSnapshot): RiskLevel {
        val now = ctx.timestamp

        // Control de permanencia fuera de la posición boca abajo (tolerancia de 3 segundos)
        if (!ctx.estaBocaAbajo) {
            if (notFaceDownSince == null) notFaceDownSince = now
            val tiempoFuera = now - (notFaceDownSince ?: now)
            if (tiempoFuera >= AdaptationRules.BOCA_ARRIBA_GRACE_MS) {
                return RiskLevel.NO_BOCA_ABAJO
            }
        } else {
            notFaceDownSince = null
        }

        // Lógica existente de detección de movimiento
        if (ctx.movementIndex > AdaptationRules.MOVEMENT_ALERTA) {
            if (aboveSince == null) aboveSince = now
            val sustained = now - (aboveSince ?: now)
            if (sustained >= AdaptationRules.SUSTAINED_MS && ctx.screenOn) {
                return RiskLevel.ALERTA
            }
        } else {
            aboveSince = null
        }

        return if (ctx.movementIndex > AdaptationRules.MOVEMENT_ATENCION)
            RiskLevel.ATENCION else RiskLevel.NORMAL
    }
}