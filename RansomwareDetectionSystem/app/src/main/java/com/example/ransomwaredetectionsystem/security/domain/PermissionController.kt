package com.example.ransomwaredetectionsystem.security.domain

import com.example.ransomwaredetectionsystem.security.logging.SecurityLogger
import com.example.ransomwaredetectionsystem.security.logging.SecurityLogger.EventType

class PermissionController(
    private val securityLogger: SecurityLogger
) {
    suspend fun decide(
        request: PermissionRequestContext,
        risk: RiskResult,
        intentVerified: Boolean,
        behavior: BehavioralResult
    ): PermissionDecisionResult {
        if (!behavior.isGenuine) {
            val reason = "Behavioral verification failed: ${behavior.reasons.joinToString()}"
            securityLogger.logEvent(
                eventType = EventType.SUSPICIOUS_BEHAVIOR,
                packageName = request.packageName,
                riskScore = risk.score,
                riskLevel = risk.level.name,
                decision = PermissionDecision.BLOCK_PERMISSION.name,
                details = reason
            )
            return PermissionDecisionResult(PermissionDecision.BLOCK_PERMISSION, reason)
        }

        if (risk.level == RiskLevel.HIGH && !intentVerified) {
            val reason = "High risk request without successful human intent verification"
            securityLogger.logEvent(
                eventType = EventType.PERMISSION_DECISION,
                packageName = request.packageName,
                riskScore = risk.score,
                riskLevel = risk.level.name,
                decision = PermissionDecision.BLOCK_PERMISSION.name,
                details = reason
            )
            return PermissionDecisionResult(PermissionDecision.BLOCK_PERMISSION, reason)
        }

        if (risk.level == RiskLevel.HIGH && intentVerified) {
            val reason = "High risk request approved with verified human intent"
            securityLogger.logEvent(
                eventType = EventType.PERMISSION_DECISION,
                packageName = request.packageName,
                riskScore = risk.score,
                riskLevel = risk.level.name,
                decision = PermissionDecision.GRANT_TEMPORARY_PERMISSION.name,
                details = reason
            )
            return PermissionDecisionResult(PermissionDecision.GRANT_TEMPORARY_PERMISSION, reason)
        }

        if (risk.level == RiskLevel.LOW) {
            val reason = "Low risk request allowed"
            securityLogger.logEvent(
                eventType = EventType.PERMISSION_DECISION,
                packageName = request.packageName,
                riskScore = risk.score,
                riskLevel = risk.level.name,
                decision = PermissionDecision.ALLOW_NORMALLY.name,
                details = reason
            )
            return PermissionDecisionResult(PermissionDecision.ALLOW_NORMALLY, reason)
        }

        val mediumRiskReason = if (intentVerified) {
            "Medium risk request approved with verification"
        } else {
            "Medium risk request blocked due to missing intent verification"
        }
        val mediumDecision = if (intentVerified) {
            PermissionDecision.GRANT_TEMPORARY_PERMISSION
        } else {
            PermissionDecision.BLOCK_PERMISSION
        }
        securityLogger.logEvent(
            eventType = EventType.PERMISSION_DECISION,
            packageName = request.packageName,
            riskScore = risk.score,
            riskLevel = risk.level.name,
            decision = mediumDecision.name,
            details = mediumRiskReason
        )
        return PermissionDecisionResult(mediumDecision, mediumRiskReason)
    }
}
