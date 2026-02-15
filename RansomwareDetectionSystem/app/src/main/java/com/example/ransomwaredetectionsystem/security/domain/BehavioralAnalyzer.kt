package com.example.ransomwaredetectionsystem.security.domain

import kotlin.math.abs
import kotlin.math.sqrt

data class InteractionSample(
    val pressDurationMs: Long,
    val responseDelayMs: Long,
    val moveEventCount: Int,
    val gestureDistancePx: Float,
    val eventIntervalsMs: List<Long>,
    val timestampMs: Long = System.currentTimeMillis()
)

data class BehavioralResult(
    val isGenuine: Boolean,
    val reasons: List<String>
)

class BehavioralAnalyzer {
    private val recentSamples = ArrayDeque<InteractionSample>()

    fun analyze(sample: InteractionSample): BehavioralResult {
        val reasons = mutableListOf<String>()

        if (sample.pressDurationMs < 500L) {
            reasons += "Interaction time too short"
        }
        if (sample.responseDelayMs < 500L) {
            reasons += "Response happened too quickly"
        }

        val nonZeroIntervals = sample.eventIntervalsMs.filter { it > 0L }
        val avgInterval = if (nonZeroIntervals.isNotEmpty()) {
            nonZeroIntervals.average()
        } else {
            Double.MAX_VALUE
        }
        if (sample.moveEventCount > 8 && avgInterval < 6.0) {
            reasons += "Gesture cadence indicates automation"
        }
        if (sample.moveEventCount > 24 && sample.gestureDistancePx < 6f) {
            reasons += "Unnatural gesture consistency detected"
        }
        if (isRepeatedPattern(sample)) {
            reasons += "Repeated timing pattern matches automated behavior"
        }

        trackSample(sample)
        return BehavioralResult(
            isGenuine = reasons.isEmpty(),
            reasons = reasons
        )
    }

    private fun isRepeatedPattern(sample: InteractionSample): Boolean {
        if (recentSamples.size < 3) return false

        val previousDurations = recentSamples.toList().takeLast(3).map { it.pressDurationMs.toDouble() }
        val mean = previousDurations.average()
        val variance = previousDurations
            .map { duration -> (duration - mean) * (duration - mean) }
            .average()
        val stdDev = sqrt(variance)

        val closeToMean = abs(sample.pressDurationMs - mean) < 30
        return stdDev < 25.0 && closeToMean
    }

    private fun trackSample(sample: InteractionSample) {
        if (recentSamples.size >= 10) {
            recentSamples.removeFirst()
        }
        recentSamples.addLast(sample)
    }
}
