package com.vigia.decision

import com.vigia.processing.SignalProcessor

class AdaptationEngine(
    private val signalProcessor: SignalProcessor = SignalProcessor(),
    private val riskEvaluator: RiskEvaluator = RiskEvaluator(),
    private val adaptationRules: AdaptationRules = AdaptationRules()
) {
    fun execute(rawData: List<Double>): String {
        val average = signalProcessor.processSignal(rawData)
        val risk = riskEvaluator.evaluateRisk(average)
        return adaptationRules.getAction(risk)
    }
}