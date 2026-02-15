package com.example.ransomwaredetectionsystem.security.domain

class RiskScorer {
    fun score(input: PermissionRiskInput): RiskResult {
        var total = 0
        val reasons = mutableListOf<String>()

        if (input.isDangerousCombination) {
            total += 40
            reasons += "Dangerous permission combination detected"
        }
        if (input.isUnknownOrSideloaded) {
            total += 25
            reasons += "Unknown or sideloaded app"
        }
        if (input.isFirstTimeRequest) {
            total += 15
            reasons += "First-time permission request"
        }
        if (input.isBackgroundRequest) {
            total += 20
            reasons += "Permission requested from background context"
        }
        if (input.hasMultipleSensitivePermissions) {
            total += 20
            reasons += "Multiple sensitive permissions requested"
        }

        val level = when {
            total >= 60 -> RiskLevel.HIGH
            total >= 30 -> RiskLevel.MEDIUM
            else -> RiskLevel.LOW
        }
        return RiskResult(score = total, level = level, reasons = reasons)
    }

    fun hasDangerousCombination(permissions: Set<String>): Boolean {
        val normalized = permissions.map { it.trim() }.toSet()
        val hasStorage = normalized.any { it in PermissionSignals.storagePermissions }
        val hasAccessibility = PermissionSignals.CAPABILITY_ACCESSIBILITY in normalized
        val hasDeviceAdmin = PermissionSignals.CAPABILITY_DEVICE_ADMIN in normalized
        val hasOverlay = PermissionSignals.CAPABILITY_OVERLAY in normalized
        val hasBackground = PermissionSignals.CAPABILITY_BACKGROUND_SERVICE in normalized
        val hasMediaControl = PermissionSignals.CAPABILITY_MEDIA_CONTROL in normalized
        val hasFullStorage = PermissionSignals.CAPABILITY_FULL_STORAGE in normalized || hasStorage

        return (hasStorage && hasAccessibility) ||
            (hasDeviceAdmin && hasOverlay) ||
            (hasFullStorage && hasBackground) ||
            (hasAccessibility && hasMediaControl) ||
            (hasOverlay && hasAccessibility)
    }
}
