package com.vigia.decision

import com.vigia.model.ContextSnapshot
import com.vigia.model.RiskLevel

class RiskEvaluator {

    private var aboveSince: Long? = null
    private var notFaceDownSince: Long? = null

    fun evaluate(ctx: ContextSnapshot): RiskLevel {
        val now = ctx.timestamp

        // Condición física conjunta:
        // El equipo está en infracción de postura si el acelerómetro detecta que no está boca abajo
        // O si el sensor de proximidad detecta que fue levantado/despejado de la superficie.
        val fueraDePosicionFisica = !ctx.estaBocaAbajo || !ctx.proximidadCubierta

        if (fueraDePosicionFisica) {
            if (notFaceDownSince == null) notFaceDownSince = now
            val tiempoFuera = now - (notFaceDownSince ?: now)
            if (tiempoFuera >= AdaptationRules.BOCA_ARRIBA_GRACE_MS) {
                return RiskLevel.NO_BOCA_ABAJO
            }
        } else {
            notFaceDownSince = null
        }

        // Lógica de manipulación física continua (acelerómetro + pantalla activa)
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