package com.vigia.decision

import com.vigia.model.ContextSnapshot
import com.vigia.model.RiskLevel

class RiskEvaluator {

    private var aboveSince: Long? = null

    fun evaluate(ctx: ContextSnapshot): RiskLevel {
        val now = ctx.timestamp

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