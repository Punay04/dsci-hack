package com.example.ransomwaredetectionsystem.mesh

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Receives detection events from other modules
 * and forwards them to MeshEngine.
 */
class ThreatEventReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {

        val eventType = intent.getStringExtra("eventType") ?: return
        val riskScore = intent.getIntExtra("riskScore", 1)
        val source = intent.getStringExtra("source") ?: "UNKNOWN"

        Log.d("MeshNet", "Event received: $eventType")

        val engine = MeshEngine(context)
        engine.handleEvent(eventType, riskScore, source)
    }
}
