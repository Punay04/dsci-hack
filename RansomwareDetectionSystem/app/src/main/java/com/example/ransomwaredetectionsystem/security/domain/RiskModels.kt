package com.example.ransomwaredetectionsystem.security.domain

enum class RiskLevel {
    LOW,
    MEDIUM,
    HIGH
}

data class PermissionRiskInput(
    val packageName: String,
    val permissions: Set<String>,
    val isDangerousCombination: Boolean,
    val isUnknownOrSideloaded: Boolean,
    val isFirstTimeRequest: Boolean,
    val isBackgroundRequest: Boolean,
    val hasMultipleSensitivePermissions: Boolean
)

data class RiskResult(
    val score: Int,
    val level: RiskLevel,
    val reasons: List<String>
)
