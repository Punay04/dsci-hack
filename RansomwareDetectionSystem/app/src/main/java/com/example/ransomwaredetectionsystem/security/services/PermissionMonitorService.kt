package com.example.ransomwaredetectionsystem.security.services

import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.admin.DevicePolicyManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.Process
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.ransomwaredetectionsystem.MainActivity
import com.example.ransomwaredetectionsystem.R
import com.example.ransomwaredetectionsystem.security.domain.BehavioralResult
import com.example.ransomwaredetectionsystem.security.domain.PermissionController
import com.example.ransomwaredetectionsystem.security.domain.PermissionDecision
import com.example.ransomwaredetectionsystem.security.domain.PermissionRequestContext
import com.example.ransomwaredetectionsystem.security.domain.PermissionRiskInput
import com.example.ransomwaredetectionsystem.security.domain.PermissionSignals
import com.example.ransomwaredetectionsystem.security.domain.RiskLevel
import com.example.ransomwaredetectionsystem.security.domain.RiskResult
import com.example.ransomwaredetectionsystem.security.domain.RiskScorer
import com.example.ransomwaredetectionsystem.security.logging.SecurityLogger
import com.example.ransomwaredetectionsystem.security.logging.SecurityLogger.EventType
import com.example.ransomwaredetectionsystem.security.ui.IntentVerificationActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class PermissionMonitorService : Service() {
    private data class PendingRiskRequest(
        val request: PermissionRequestContext,
        val riskResult: RiskResult
    )

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val pendingRequests = ConcurrentHashMap<String, PendingRiskRequest>()
    private val riskScorer = RiskScorer()

    private lateinit var appOpsManager: AppOpsManager
    private lateinit var packageManagerRef: PackageManager
    private lateinit var usageStatsManager: UsageStatsManager
    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var securityLogger: SecurityLogger
    private lateinit var permissionController: PermissionController

    private val prefs by lazy {
        applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val permissionSignalReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_PERMISSION_DIALOG_DETECTED -> {
                    val pkg = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: return
                    val permissions = intent.getStringArrayListExtra(EXTRA_PERMISSIONS)?.toSet().orEmpty()
                    evaluatePermissionRequest(pkg, permissions, source = "accessibility")
                }

                ACTION_ACCESSIBILITY_ABUSE_DETECTED -> {
                    val pkg = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: return
                    evaluatePermissionRequest(
                        packageName = pkg,
                        rawPermissions = setOf(PermissionSignals.CAPABILITY_ACCESSIBILITY),
                        source = "accessibility_abuse"
                    )
                }

                ACTION_OVERLAY_ABUSE_DETECTED -> {
                    val pkg = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: return
                    evaluatePermissionRequest(
                        packageName = pkg,
                        rawPermissions = setOf(PermissionSignals.CAPABILITY_OVERLAY),
                        source = "overlay_abuse"
                    )
                }

                ACTION_VERIFICATION_RESULT -> {
                    handleVerificationResult(intent)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        appOpsManager = getSystemService(AppOpsManager::class.java)
        packageManagerRef = packageManager
        usageStatsManager = getSystemService(UsageStatsManager::class.java)
        devicePolicyManager = getSystemService(DevicePolicyManager::class.java)
        securityLogger = SecurityLogger.getInstance(applicationContext)
        permissionController = PermissionController(securityLogger)

        registerSignalReceiver()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Human Intent Verification Shield active"))
        startPeriodicForegroundScan()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_FORCE_SCAN) {
            serviceScope.launch { scanForegroundAppForPermissionChanges() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        unregisterReceiver(permissionSignalReceiver)
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun registerSignalReceiver() {
        val filter = IntentFilter().apply {
            addAction(ACTION_PERMISSION_DIALOG_DETECTED)
            addAction(ACTION_VERIFICATION_RESULT)
            addAction(ACTION_ACCESSIBILITY_ABUSE_DETECTED)
            addAction(ACTION_OVERLAY_ABUSE_DETECTED)
        }
        ContextCompat.registerReceiver(
            this,
            permissionSignalReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    private fun evaluatePermissionRequest(
        packageName: String,
        rawPermissions: Set<String>,
        source: String
    ) {
        if (packageName == this.packageName) return
        val requestId = UUID.randomUUID().toString()
        val enrichedPermissions = enrichSignals(packageName, rawPermissions)
        val isBackgroundRequest = resolveForegroundPackageName() != packageName
        val isFirstRequest = markAndCheckFirstTimeRequest(packageName, enrichedPermissions)
        val isUnknownOrSideloaded = isUnknownOrSideloadedApp(packageName)
        val dangerousCombination = riskScorer.hasDangerousCombination(enrichedPermissions)
        val sensitiveCount = enrichedPermissions.count { it in PermissionSignals.sensitivePermissions }

        val riskInput = PermissionRiskInput(
            packageName = packageName,
            permissions = enrichedPermissions,
            isDangerousCombination = dangerousCombination,
            isUnknownOrSideloaded = isUnknownOrSideloaded,
            isFirstTimeRequest = isFirstRequest,
            isBackgroundRequest = isBackgroundRequest,
            hasMultipleSensitivePermissions = sensitiveCount >= 2
        )
        val riskResult = riskScorer.score(riskInput)
        val request = PermissionRequestContext(
            requestId = requestId,
            packageName = packageName,
            requestedPermissions = enrichedPermissions,
            requestedAtMs = System.currentTimeMillis(),
            isBackgroundRequest = isBackgroundRequest,
            isFirstTimeRequest = isFirstRequest,
            source = source
        )
        pendingRequests[requestId] = PendingRiskRequest(request, riskResult)

        serviceScope.launch {
            securityLogger.logEvent(
                eventType = EventType.PERMISSION_REQUEST,
                packageName = packageName,
                riskScore = riskResult.score,
                riskLevel = riskResult.level.name,
                details = "Request from $source with ${enrichedPermissions.joinToString()}"
            )
            securityLogger.logEvent(
                eventType = EventType.RISK_SCORE,
                packageName = packageName,
                riskScore = riskResult.score,
                riskLevel = riskResult.level.name,
                details = riskResult.reasons.joinToString()
            )
        }

        val requiresVerification = riskResult.level != RiskLevel.LOW || dangerousCombination
        if (requiresVerification) {
            pausePermissionDialog()
            launchIntentVerification(request, riskResult)
        } else {
            pendingRequests.remove(requestId)
            serviceScope.launch {
                securityLogger.logEvent(
                    eventType = EventType.PERMISSION_DECISION,
                    packageName = packageName,
                    riskScore = riskResult.score,
                    riskLevel = riskResult.level.name,
                    decision = PermissionDecision.ALLOW_NORMALLY.name,
                    details = "Low risk request allowed without additional verification"
                )
            }
        }
    }

    private fun handleVerificationResult(intent: Intent) {
        val requestId = intent.getStringExtra(EXTRA_REQUEST_ID) ?: return
        val pending = pendingRequests.remove(requestId) ?: return
        val intentVerified = intent.getBooleanExtra(EXTRA_INTENT_VERIFIED, false)
        val behaviorGenuine = intent.getBooleanExtra(EXTRA_BEHAVIOR_GENUINE, false)
        val behaviorReasons = intent.getStringArrayListExtra(EXTRA_BEHAVIOR_REASONS).orEmpty()
        val behaviorResult = BehavioralResult(
            isGenuine = behaviorGenuine,
            reasons = behaviorReasons
        )

        serviceScope.launch {
            securityLogger.logEvent(
                eventType = EventType.USER_DECISION,
                packageName = pending.request.packageName,
                riskScore = pending.riskResult.score,
                riskLevel = pending.riskResult.level.name,
                details = "Intent verified=$intentVerified, behavior genuine=$behaviorGenuine"
            )

            val decision = permissionController.decide(
                request = pending.request,
                risk = pending.riskResult,
                intentVerified = intentVerified,
                behavior = behaviorResult
            )

            when (decision.decision) {
                PermissionDecision.BLOCK_PERMISSION -> {
                    blockPermissionRequest(pending.request, decision.reason)
                }

                PermissionDecision.GRANT_TEMPORARY_PERMISSION -> {
                    SecurityMonitoringService.addMonitoredPackage(
                        applicationContext,
                        pending.request.packageName
                    )
                    ContextCompat.startForegroundService(
                        applicationContext,
                        Intent(applicationContext, SecurityMonitoringService::class.java)
                    )
                }

                PermissionDecision.ALLOW_NORMALLY -> {
                    // No-op: permission flow continues normally.
                }
            }
        }
    }

    private suspend fun blockPermissionRequest(
        request: PermissionRequestContext,
        reason: String
    ) {
        request.requestedPermissions
            .filter { it.startsWith("android.permission.") }
            .forEach { permission ->
                runCatching {
                    if (packageManagerRef.checkPermission(permission, request.packageName) ==
                        PackageManager.PERMISSION_GRANTED
                    ) {
                        packageManagerRef.revokeRuntimePermission(
                            request.packageName,
                            permission,
                            Process.myUserHandle()
                        )
                    }
                }.onFailure {
                    securityLogger.logEvent(
                        eventType = EventType.BLOCKED_APP,
                        packageName = request.packageName,
                        details = "Failed to revoke $permission: ${it.message}"
                    )
                }
            }

        runCatching {
            val activityManager = getSystemService(ActivityManager::class.java)
            activityManager.killBackgroundProcesses(request.packageName)
        }

        pausePermissionDialog()
        securityLogger.logEvent(
            eventType = EventType.BLOCKED_APP,
            packageName = request.packageName,
            details = "Blocked permission request: $reason"
        )
    }

    private fun launchIntentVerification(
        request: PermissionRequestContext,
        risk: RiskResult
    ) {
        val intent = Intent(this, IntentVerificationActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            putExtra(EXTRA_REQUEST_ID, request.requestId)
            putExtra(EXTRA_PACKAGE_NAME, request.packageName)
            putStringArrayListExtra(EXTRA_PERMISSIONS, ArrayList(request.requestedPermissions))
            putExtra(EXTRA_RISK_SCORE, risk.score)
            putExtra(EXTRA_RISK_LEVEL, risk.level.name)
            putStringArrayListExtra(EXTRA_RISK_REASONS, ArrayList(risk.reasons))
        }
        startActivity(intent)
    }

    private fun pausePermissionDialog() {
        sendBroadcast(Intent(ACTION_PAUSE_PERMISSION_FLOW).setPackage(packageName))
    }

    private fun enrichSignals(packageName: String, rawPermissions: Set<String>): Set<String> {
        val normalized = rawPermissions.toMutableSet()

        if (isAccessibilityEnabledForPackage(packageName)) {
            normalized += PermissionSignals.CAPABILITY_ACCESSIBILITY
        }
        if (hasOverlayCapability(packageName)) {
            normalized += PermissionSignals.CAPABILITY_OVERLAY
        }
        if (isDeviceAdmin(packageName)) {
            normalized += PermissionSignals.CAPABILITY_DEVICE_ADMIN
        }
        if (hasForegroundServicePermission(packageName)) {
            normalized += PermissionSignals.CAPABILITY_BACKGROUND_SERVICE
        }
        if (hasFullStorageAccess(packageName)) {
            normalized += PermissionSignals.CAPABILITY_FULL_STORAGE
        }
        return normalized
    }

    private fun isAccessibilityEnabledForPackage(packageName: String): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.contains(packageName, ignoreCase = true)
    }

    private fun hasOverlayCapability(packageName: String): Boolean {
        val uid = runCatching {
            packageManagerRef.getApplicationInfo(packageName, 0).uid
        }.getOrNull() ?: return false

        val mode = appOpsManager.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW,
            uid,
            packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun hasForegroundServicePermission(packageName: String): Boolean {
        val packageInfo = getPackageInfoCompat(packageName) ?: return false
        val requested = packageInfo.requestedPermissions?.toSet().orEmpty()
        return requested.contains(android.Manifest.permission.FOREGROUND_SERVICE)
    }

    private fun hasFullStorageAccess(packageName: String): Boolean {
        val hasManageExternalStorage = packageManagerRef.checkPermission(
            android.Manifest.permission.MANAGE_EXTERNAL_STORAGE,
            packageName
        ) == PackageManager.PERMISSION_GRANTED

        val uid = runCatching {
            packageManagerRef.getApplicationInfo(packageName, 0).uid
        }.getOrNull() ?: return hasManageExternalStorage

        val appOpsMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            appOpsManager.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_MANAGE_EXTERNAL_STORAGE,
                uid,
                packageName
            )
        } else {
            AppOpsManager.MODE_IGNORED
        }
        return hasManageExternalStorage || appOpsMode == AppOpsManager.MODE_ALLOWED
    }

    private fun isDeviceAdmin(packageName: String): Boolean {
        val admins = devicePolicyManager.activeAdmins ?: return false
        return admins.any { it.packageName == packageName }
    }

    private fun markAndCheckFirstTimeRequest(packageName: String, permissions: Set<String>): Boolean {
        var isFirstTime = false
        val editor = prefs.edit()
        permissions.forEach { permission ->
            val key = "$packageName::$permission"
            if (!prefs.contains(key)) {
                isFirstTime = true
                editor.putLong(key, System.currentTimeMillis())
            }
        }
        editor.apply()
        return isFirstTime
    }

    private fun isUnknownOrSideloadedApp(packageName: String): Boolean {
        val installer = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                packageManagerRef.getInstallSourceInfo(packageName).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                packageManagerRef.getInstallerPackageName(packageName)
            }
        }.getOrNull()

        return installer == null || installer !in TRUSTED_INSTALLERS
    }

    private fun getPackageInfoCompat(packageName: String): PackageInfo? {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManagerRef.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                packageManagerRef.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
            }
        }.getOrNull()
    }

    private fun startPeriodicForegroundScan() {
        serviceScope.launch {
            while (isActive) {
                runCatching { scanForegroundAppForPermissionChanges() }
                delay(POLLING_INTERVAL_MS)
            }
        }
    }

    private fun scanForegroundAppForPermissionChanges() {
        val foregroundPackage = resolveForegroundPackageName() ?: return
        if (foregroundPackage == packageName) return

        val packageInfo = getPackageInfoCompat(foregroundPackage) ?: return
        val requestedPermissions = packageInfo.requestedPermissions?.toSet().orEmpty()
        if (requestedPermissions.isEmpty()) return

        val grantedSensitive = requestedPermissions
            .filter { permission ->
                permission in PermissionSignals.sensitivePermissions &&
                    packageManagerRef.checkPermission(permission, foregroundPackage) ==
                    PackageManager.PERMISSION_GRANTED
            }
            .toSet()
        if (grantedSensitive.isEmpty()) return

        val knownGranted = getKnownGrantedPermissions(foregroundPackage)
        val newlyGranted = grantedSensitive - knownGranted
        if (newlyGranted.isNotEmpty()) {
            setKnownGrantedPermissions(foregroundPackage, grantedSensitive)
            evaluatePermissionRequest(
                packageName = foregroundPackage,
                rawPermissions = newlyGranted,
                source = "usage_stats_scan"
            )
        }
    }

    private fun resolveForegroundPackageName(windowMs: Long = 90_000L): String? {
        val endTime = System.currentTimeMillis()
        val beginTime = endTime - windowMs
        val usageEvents = usageStatsManager.queryEvents(beginTime, endTime)
        val event = UsageEvents.Event()
        var latestPackage: String? = null

        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            val isForeground = event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                    event.eventType == UsageEvents.Event.ACTIVITY_RESUMED)
            if (isForeground && !event.packageName.isNullOrBlank()) {
                latestPackage = event.packageName
            }
        }
        return latestPackage
    }

    private fun getKnownGrantedPermissions(packageName: String): Set<String> {
        val encoded = prefs.getString("$packageName::$KNOWN_GRANTED_SUFFIX", null) ?: return emptySet()
        return encoded.split(",").filter { it.isNotBlank() }.toSet()
    }

    private fun setKnownGrantedPermissions(packageName: String, granted: Set<String>) {
        prefs.edit()
            .putString("$packageName::$KNOWN_GRANTED_SUFFIX", granted.joinToString(","))
            .apply()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Permission Shield Monitor",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(content: String): Notification {
        val launchIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            1001,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Human Intent Verification Shield")
            .setContentText(content)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val ACTION_PERMISSION_DIALOG_DETECTED =
            "com.example.ransomwaredetectionsystem.security.PERMISSION_DIALOG_DETECTED"
        const val ACTION_ACCESSIBILITY_ABUSE_DETECTED =
            "com.example.ransomwaredetectionsystem.security.ACCESSIBILITY_ABUSE_DETECTED"
        const val ACTION_OVERLAY_ABUSE_DETECTED =
            "com.example.ransomwaredetectionsystem.security.OVERLAY_ABUSE_DETECTED"
        const val ACTION_VERIFICATION_RESULT =
            "com.example.ransomwaredetectionsystem.security.VERIFICATION_RESULT"
        const val ACTION_PAUSE_PERMISSION_FLOW =
            "com.example.ransomwaredetectionsystem.security.PAUSE_PERMISSION_FLOW"
        const val ACTION_FORCE_SCAN =
            "com.example.ransomwaredetectionsystem.security.FORCE_SCAN"

        const val EXTRA_REQUEST_ID = "extra_request_id"
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
        const val EXTRA_PERMISSIONS = "extra_permissions"
        const val EXTRA_RISK_SCORE = "extra_risk_score"
        const val EXTRA_RISK_LEVEL = "extra_risk_level"
        const val EXTRA_RISK_REASONS = "extra_risk_reasons"
        const val EXTRA_INTENT_VERIFIED = "extra_intent_verified"
        const val EXTRA_BEHAVIOR_GENUINE = "extra_behavior_genuine"
        const val EXTRA_BEHAVIOR_REASONS = "extra_behavior_reasons"

        private const val PREFS_NAME = "permission_monitor_prefs"
        private const val POLLING_INTERVAL_MS = 20_000L
        private const val CHANNEL_ID = "permission_monitor_channel"
        private const val NOTIFICATION_ID = 9001
        private const val KNOWN_GRANTED_SUFFIX = "known_granted"

        private val TRUSTED_INSTALLERS = setOf(
            "com.android.vending",
            "com.google.android.packageinstaller",
            "com.samsung.android.packageinstaller",
            "com.miui.packageinstaller"
        )
    }
}
