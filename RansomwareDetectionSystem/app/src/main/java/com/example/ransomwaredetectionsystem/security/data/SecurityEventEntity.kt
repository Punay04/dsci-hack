package com.example.ransomwaredetectionsystem.security.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "security_events")
data class SecurityEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val eventType: String,
    val packageName: String?,
    val riskScore: Int?,
    val riskLevel: String?,
    val decision: String?,
    val details: String
)
