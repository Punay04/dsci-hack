package com.example.ransomwaredetectionsystem.security.viewmodel

import androidx.lifecycle.ViewModel
import com.example.ransomwaredetectionsystem.security.domain.BehavioralAnalyzer
import com.example.ransomwaredetectionsystem.security.domain.BehavioralResult
import com.example.ransomwaredetectionsystem.security.domain.InteractionSample

class IntentVerificationViewModel : ViewModel() {
    private val analyzer = BehavioralAnalyzer()

    fun evaluateBehavior(sample: InteractionSample): BehavioralResult {
        return analyzer.analyze(sample)
    }
}
