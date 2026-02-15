package com.example.ransomwaredetectionsystem.security.repository

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.example.ransomwaredetectionsystem.security.data.SecurityEventEntity
import com.example.ransomwaredetectionsystem.security.logging.SecurityLogger
import com.example.ransomwaredetectionsystem.security.services.PermissionMonitorService
import com.example.ransomwaredetectionsystem.security.services.SecurityMonitoringService
import kotlinx.coroutines.flow.Flow

class ShieldRepository(private val context: Context) {
    private val appContext = context.applicationContext
    private val logger = SecurityLogger.getInstance(appContext)
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun observeRecentEvents(limit: Int = 8): Flow<List<SecurityEventEntity>> {
        return logger.observeRecentEvents(limit)
    }

    fun startShield() {
        ContextCompat.startForegroundService(
            appContext,
            Intent(appContext, PermissionMonitorService::class.java)
        )
        ContextCompat.startForegroundService(
            appContext,
            Intent(appContext, SecurityMonitoringService::class.java)
        )
        prefs.edit().putBoolean(KEY_SHIELD_ENABLED, true).apply()
    }

    fun stopShield() {
        appContext.stopService(Intent(appContext, PermissionMonitorService::class.java))
        appContext.stopService(Intent(appContext, SecurityMonitoringService::class.java))
        prefs.edit().putBoolean(KEY_SHIELD_ENABLED, false).apply()
    }

    fun isShieldEnabled(): Boolean {
        return prefs.getBoolean(KEY_SHIELD_ENABLED, false)
    }

    fun accessibilitySettingsIntent(): Intent {
        return Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun usageAccessSettingsIntent(): Intent {
        return Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    companion object {
        private const val PREFS_NAME = "shield_repository_prefs"
        private const val KEY_SHIELD_ENABLED = "shield_enabled"
    }
}
