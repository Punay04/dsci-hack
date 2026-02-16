package com.example.ransomwaredetectionsystem.mesh

interface MeshDispatcher {
    fun dispatch(signature: ThreatSignature)
}
