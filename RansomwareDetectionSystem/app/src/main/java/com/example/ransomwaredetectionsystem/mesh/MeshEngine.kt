package com.example.ransomwaredetectionsystem.mesh

import android.content.Context
import android.util.Log

/**
 * MeshEngine
 *
 * Responsibilities:
 * - Convert raw events → ThreatSignature
 * - Store unique signatures (bounded)
 * - Detect burst attacks independently
 * - Dispatch to transport layer
 */
class MeshEngine(context: Context) {

    private val threatStore = ThreatStore(context)
    private val dispatcher: MeshDispatcher = NoOpMeshDispatcher()

    // ---- Burst Detection State (independent of storage) ----

    private var currentBucket: Long = ThreatSignature.currentTimeBucket()
    private var bucketEventCount: Int = 0
    private val burstThreshold = 4

    fun handleEvent(
        eventType: String,
        riskScore: Int,
        source: String
    ) {

        Log.d("MeshNet", "Handling event: $eventType")

        val signature = ThreatSignature.create(
            eventType = eventType,
            riskScore = riskScore,
            source = source
        )

        // ---- Burst Detection (ALWAYS COUNT EVENTS) ----
        updateBurstCounter(signature.timeBucket)

        if (bucketEventCount >= burstThreshold) {
            Log.w("MeshNet", "⚠ Burst attack detected! Events in current minute: $bucketEventCount")
        }

        // ---- Duplicate Filter (Only for Storage Control) ----
        if (!isDuplicate(signature)) {
            Log.i("MeshNet", "New unique signature detected. Saving.")
            threatStore.save(signature)
        } else {
            Log.d("MeshNet", "Duplicate signature ignored (not stored).")
        }

        safeDispatch(signature)
    }

    // -----------------------------------------
    // Duplicate Filter (Storage Protection Only)
    // -----------------------------------------

    private fun isDuplicate(newSignature: ThreatSignature): Boolean {
        val existing = threatStore.getAll()

        return existing.any {
            it.eventType == newSignature.eventType &&
                    it.source == newSignature.source &&
                    it.timeBucket == newSignature.timeBucket
        }
    }

    // -----------------------------------------
    // Burst Detection Logic
    // -----------------------------------------

    private fun updateBurstCounter(eventBucket: Long) {
        if (eventBucket != currentBucket) {
            currentBucket = eventBucket
            bucketEventCount = 0
        }
        bucketEventCount++
    }

    // -----------------------------------------
    // Safe Dispatch (Future Cloud Integration)
    // -----------------------------------------

    private fun safeDispatch(signature: ThreatSignature) {
        try {
            dispatcher.dispatch(signature)
        } catch (e: Exception) {
            Log.e("MeshNet", "Dispatch failed", e)
        }
    }
}
