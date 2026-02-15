package com.example.ransomwaredetectionsystem.security.domain

data class PermissionRequestContext(
    val requestId: String,
    val packageName: String,
    val requestedPermissions: Set<String>,
    val requestedAtMs: Long,
    val isBackgroundRequest: Boolean,
    val isFirstTimeRequest: Boolean,
    val source: String
)

enum class PermissionDecision {
    ALLOW_NORMALLY,
    GRANT_TEMPORARY_PERMISSION,
    BLOCK_PERMISSION
}

data class PermissionDecisionResult(
    val decision: PermissionDecision,
    val reason: String
)
