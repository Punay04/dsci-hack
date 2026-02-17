package com.example.ransomwaredetectionsystem.mesh

import org.json.JSONObject

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

    fun toJson(): JSONObject {
        val json = JSONObject()
        json.put("eventType", eventType)
        json.put("riskScore", riskScore)
        json.put("source", source)
        json.put("timeBucket", timeBucket)
        json.put("severity", severity.name)
        json.put("version", version)
        return json
    }

    companion object {

        fun currentTimeBucket(): Long {
            return System.currentTimeMillis() / 60000
        }

        private fun calculateSeverity(score: Int): Severity {
            return when {
                score >= 8 -> Severity.CRITICAL
                score >= 5 -> Severity.HIGH
                score >= 3 -> Severity.MEDIUM
                else -> Severity.LOW
            }
        }

        fun create(
            eventType: String,
            riskScore: Int,
            source: String
        ): ThreatSignature {
            return ThreatSignature(
                eventType = eventType,
                riskScore = riskScore,
                source = source,
                timeBucket = currentTimeBucket(),
                severity = calculateSeverity(riskScore),
                version = 1
            )
        }

        fun fromJson(json: JSONObject): ThreatSignature {
            return ThreatSignature(
                eventType = json.getString("eventType"),
                riskScore = json.getInt("riskScore"),
                source = json.getString("source"),
                timeBucket = json.getLong("timeBucket"),
                severity = Severity.valueOf(json.getString("severity")),
                version = json.getInt("version")
            )
        }
    }
}
