package com.example.ransomwaredetectionsystem.mesh

import kotlin.math.floor

data class ThreatSignature(
    val eventType: String,
    val riskScore: Int,
    val source: String,
    val timeBucket: Long,
    val severity: Severity,
    val version: Int = 1
) {

    enum class Severity {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }

    companion object {

        fun create(
            eventType: String,
            riskScore: Int,
            source: String
        ): ThreatSignature {

            val bucket = currentTimeBucket()
            val severity = classifySeverity(riskScore)

            return ThreatSignature(
                eventType = eventType,
                riskScore = riskScore,
                source = source,
                timeBucket = bucket,
                severity = severity
            )
        }

        private fun currentTimeBucket(): Long {
            // 10-second window bucket
            return floor(System.currentTimeMillis() / 10_000.0).toLong()
        }

        private fun classifySeverity(score: Int): Severity {
            return when {
                score >= 15 -> Severity.CRITICAL
                score >= 10 -> Severity.HIGH
                score >= 5 -> Severity.MEDIUM
                else -> Severity.LOW
            }
        }
    }
}
