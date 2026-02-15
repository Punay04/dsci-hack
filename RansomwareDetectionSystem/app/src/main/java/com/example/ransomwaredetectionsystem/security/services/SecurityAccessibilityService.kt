package com.example.ransomwaredetectionsystem.security.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import com.example.ransomwaredetectionsystem.security.domain.PermissionSignals

class SecurityAccessibilityService : AccessibilityService() {
    private lateinit var usageStatsManager: UsageStatsManager

    private val controlReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == PermissionMonitorService.ACTION_PAUSE_PERMISSION_FLOW) {
                performGlobalAction(GLOBAL_ACTION_BACK)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        usageStatsManager = getSystemService(UsageStatsManager::class.java)
        ContextCompat.registerReceiver(
            this,
            controlReceiver,
            IntentFilter(PermissionMonitorService.ACTION_PAUSE_PERMISSION_FLOW),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                AccessibilityEvent.TYPE_VIEW_CLICKED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 80
            flags = flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val packageName = event.packageName?.toString().orEmpty()
        if (packageName.isBlank()) return
        val eventText = event.text.orEmpty().joinToString(" ") { it.toString().lowercase() }

        if (isPermissionDialog(packageName, event.className?.toString(), eventText)) {
            val requestingPackage = resolveLastForegroundNonSystemPackage() ?: return
            val detectedPermissions = extractPermissionSignals(eventText)
            sendPermissionDialogDetected(requestingPackage, detectedPermissions)
        }

        if (looksLikeAccessibilityAbuse(packageName, eventText)) {
            val suspectPackage = resolveLastForegroundNonSystemPackage() ?: packageName
            sendAbuseSignal(PermissionMonitorService.ACTION_ACCESSIBILITY_ABUSE_DETECTED, suspectPackage)
        }

        if (looksLikeOverlayAbuse(packageName, eventText)) {
            val suspectPackage = resolveLastForegroundNonSystemPackage() ?: packageName
            sendAbuseSignal(PermissionMonitorService.ACTION_OVERLAY_ABUSE_DETECTED, suspectPackage)
        }
    }

    override fun onInterrupt() {
        // No-op: no spoken feedback channel is used.
    }

    override fun onDestroy() {
        unregisterReceiver(controlReceiver)
        super.onDestroy()
    }

    private fun isPermissionDialog(packageName: String, className: String?, eventText: String): Boolean {
        val isPermissionController = packageName in PERMISSION_CONTROLLER_PACKAGES ||
            packageName.contains("permissioncontroller", ignoreCase = true)
        val classHint = className?.contains("grantpermissions", ignoreCase = true) == true
        val textHint = eventText.contains("allow") &&
            (eventText.contains("permission") || eventText.contains("access"))
        return isPermissionController || classHint || textHint
    }

    private fun looksLikeAccessibilityAbuse(packageName: String, eventText: String): Boolean {
        if (packageName in TRUSTED_SETTINGS_PACKAGES) return false
        val mentionsAccessibility = eventText.contains("accessibility")
        val mentionsEnableAction = eventText.contains("turn on") ||
            eventText.contains("allow") ||
            eventText.contains("enable")
        return mentionsAccessibility && mentionsEnableAction
    }

    private fun looksLikeOverlayAbuse(packageName: String, eventText: String): Boolean {
        if (packageName in TRUSTED_SETTINGS_PACKAGES) return false
        return eventText.contains("display over other apps") ||
            eventText.contains("appear on top")
    }

    private fun extractPermissionSignals(eventText: String): Set<String> {
        val signals = mutableSetOf<String>()
        if (eventText.contains("files") || eventText.contains("storage") || eventText.contains("photos")) {
            signals += PermissionSignals.CAPABILITY_FULL_STORAGE
        }
        if (eventText.contains("accessibility")) {
            signals += PermissionSignals.CAPABILITY_ACCESSIBILITY
        }
        if (eventText.contains("display over other apps") || eventText.contains("appear on top")) {
            signals += PermissionSignals.CAPABILITY_OVERLAY
        }
        if (eventText.contains("device admin")) {
            signals += PermissionSignals.CAPABILITY_DEVICE_ADMIN
        }
        if (eventText.contains("media") || eventText.contains("music") || eventText.contains("audio")) {
            signals += PermissionSignals.CAPABILITY_MEDIA_CONTROL
        }
        if (eventText.contains("background")) {
            signals += PermissionSignals.CAPABILITY_BACKGROUND_SERVICE
        }
        if (signals.isEmpty()) {
            signals += PermissionSignals.CAPABILITY_FULL_STORAGE
        }
        return signals
    }

    private fun sendPermissionDialogDetected(packageName: String, permissions: Set<String>) {
        val intent = Intent(PermissionMonitorService.ACTION_PERMISSION_DIALOG_DETECTED).apply {
            setPackage(this@SecurityAccessibilityService.packageName)
            putExtra(PermissionMonitorService.EXTRA_PACKAGE_NAME, packageName)
            putStringArrayListExtra(
                PermissionMonitorService.EXTRA_PERMISSIONS,
                ArrayList(permissions)
            )
        }
        sendBroadcast(intent)
    }

    private fun sendAbuseSignal(action: String, packageName: String) {
        val intent = Intent(action).apply {
            setPackage(this@SecurityAccessibilityService.packageName)
            putExtra(PermissionMonitorService.EXTRA_PACKAGE_NAME, packageName)
        }
        sendBroadcast(intent)
    }

    private fun resolveLastForegroundNonSystemPackage(): String? {
        val end = System.currentTimeMillis()
        val begin = end - 90_000L
        val events = usageStatsManager.queryEvents(begin, end)
        val event = UsageEvents.Event()
        var lastPackage: String? = null

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val isForeground = event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                    event.eventType == UsageEvents.Event.ACTIVITY_RESUMED)
            val candidate = event.packageName.orEmpty()
            if (isForeground && candidate.isNotBlank() && candidate !in IGNORED_PACKAGES) {
                lastPackage = candidate
            }
        }
        return lastPackage
    }

    companion object {
        private val PERMISSION_CONTROLLER_PACKAGES = setOf(
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller",
            "com.miui.securitycenter",
            "com.samsung.android.permissioncontroller"
        )

        private val TRUSTED_SETTINGS_PACKAGES = setOf(
            "com.android.settings",
            "com.google.android.permissioncontroller",
            "com.android.permissioncontroller"
        )

        private val IGNORED_PACKAGES = setOf(
            "android",
            "com.android.systemui",
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller",
            "com.android.settings"
        )
    }
}
