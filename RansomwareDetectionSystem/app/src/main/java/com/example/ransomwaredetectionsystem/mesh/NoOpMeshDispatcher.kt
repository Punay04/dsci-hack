package com.example.ransomwaredetectionsystem.mesh

import android.util.Log

class NoOpMeshDispatcher : MeshDispatcher {
    override fun dispatch(signature: ThreatSignature) {
        Log.d("MeshNet", "Dispatch skipped (MVP). Signature: $signature")
    }
}
