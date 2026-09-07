package com.vigia.decision

class AdaptationRules {
    fun getAction(riskLevel: String): String {
        return when (riskLevel) {
            "HIGH" -> "ALERT_AND_THROTTLE"
            "MEDIUM" -> "MONITOR_CLOSELY"
            else -> "NORMAL_OPERATION"
        }
    }
}