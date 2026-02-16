package com.example.ransomwaredetectionsystem.mesh

import android.content.Context
import android.util.Log

class MeshEngine(context: Context) {

    private val threatStore = ThreatStore(context)
    private val dispatcher: MeshDispatcher = NoOpMeshDispatcher()

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

        if (!isDuplicate(signature)) {
            Log.i("MeshNet", "New unique signature detected. Saving.")
            threatStore.save(signature)
        } else {
            Log.d("MeshNet", "Duplicate signature ignored.")
            return
        }

        if (isBurstAttack(signature)) {
            Log.w("MeshNet", "⚠ Potential ransomware burst detected")
        }

        safeDispatch(signature)
    }

    private fun isDuplicate(newSignature: ThreatSignature): Boolean {
        val existing = threatStore.getAll()

        return existing.any {
            it.eventType == newSignature.eventType &&
                    it.source == newSignature.source &&
                    it.timeBucket == newSignature.timeBucket
        }
    }

    private fun isBurstAttack(signature: ThreatSignature): Boolean {
        val all = threatStore.getAll()

        val sameBucket = all.filter {
            it.timeBucket == signature.timeBucket &&
                    it.source == signature.source
        }

        return sameBucket.size >= 10
    }

    private fun safeDispatch(signature: ThreatSignature) {
        try {
            dispatcher.dispatch(signature)
        } catch (e: Exception) {
            Log.e("MeshNet", "Dispatch failed", e)
        }
    }
}
