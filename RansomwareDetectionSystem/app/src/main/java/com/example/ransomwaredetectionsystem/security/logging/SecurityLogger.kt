package com.example.ransomwaredetectionsystem.security.logging

import android.content.Context
import com.example.ransomwaredetectionsystem.data.AppDatabase
import com.example.ransomwaredetectionsystem.security.data.SecurityEventEntity
import kotlinx.coroutines.flow.Flow

class SecurityLogger private constructor(
    context: Context
) {
    enum class EventType {
        PERMISSION_REQUEST,
        RISK_SCORE,
        USER_DECISION,
        SUSPICIOUS_BEHAVIOR,
        BLOCKED_APP,
        PERMISSION_DECISION,
        MONITORING_ALERT
    }

    private val securityEventDao = AppDatabase.getDatabase(context).securityEventDao()

    suspend fun logEvent(
        eventType: EventType,
        packageName: String?,
        riskScore: Int? = null,
        riskLevel: String? = null,
        decision: String? = null,
        details: String
    ) {
        securityEventDao.insert(
            SecurityEventEntity(
                timestamp = System.currentTimeMillis(),
                eventType = eventType.name,
                packageName = packageName,
                riskScore = riskScore,
                riskLevel = riskLevel,
                decision = decision,
                details = details
            )
        )
    }

    fun observeRecentEvents(limit: Int = 25): Flow<List<SecurityEventEntity>> {
        return securityEventDao.observeRecent(limit)
    }

    suspend fun pruneOldEvents(retentionMs: Long = 30L * 24L * 60L * 60L * 1000L) {
        val cutoff = System.currentTimeMillis() - retentionMs
        securityEventDao.deleteOlderThan(cutoff)
    }

    companion object {
        @Volatile
        private var INSTANCE: SecurityLogger? = null

        fun getInstance(context: Context): SecurityLogger {
            return INSTANCE ?: synchronized(this) {
                val instance = SecurityLogger(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}
