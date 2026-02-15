package com.example.ransomwaredetectionsystem.security.services

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.FileObserver
import android.os.IBinder
import android.os.Process
import androidx.core.app.NotificationCompat
import com.example.ransomwaredetectionsystem.MainActivity
import com.example.ransomwaredetectionsystem.R
import com.example.ransomwaredetectionsystem.security.domain.PermissionSignals
import com.example.ransomwaredetectionsystem.security.logging.SecurityLogger
import com.example.ransomwaredetectionsystem.security.logging.SecurityLogger.EventType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue

class SecurityMonitoringService : Service() {
    private data class FileSignal(
        val timestampMs: Long,
        val event: Int,
        val path: String?
    )

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val fileSignals = ConcurrentLinkedQueue<FileSignal>()
    private val observers = mutableListOf<FileObserver>()

    private lateinit var usageStatsManager: UsageStatsManager
    private lateinit var securityLogger: SecurityLogger

    override fun onCreate() {
        super.onCreate()
        usageStatsManager = getSystemService(UsageStatsManager::class.java)
        securityLogger = SecurityLogger.getInstance(applicationContext)

        createNotificationChannels()
        startForeground(
            NOTIFICATION_ID_MONITORING,
            buildServiceNotification("Monitoring approved apps for ransomware behavior")
        )
        startFileObservers()
        startMonitoringLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        observers.forEach { it.stopWatching() }
        observers.clear()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startFileObservers() {
        val targets = collectWatchTargets()
        targets.forEach { directory ->
            val observer = object : FileObserver(directory.absolutePath, OBSERVER_MASK) {
                override fun onEvent(event: Int, path: String?) {
                    if (event == 0) return
                    fileSignals.add(
                        FileSignal(
                            timestampMs = System.currentTimeMillis(),
                            event = event,
                            path = if (path == null) null else "${directory.absolutePath}/$path"
                        )
                    )
                }
            }
            observer.startWatching()
            observers += observer
        }
    }

    private fun collectWatchTargets(): List<File> {
        val candidates = listOfNotNull(
            getExternalFilesDir(null),
            filesDir,
            cacheDir,
            getExternalFilesDir("Documents"),
            getExternalFilesDir("Download"),
            getExternalFilesDir("Pictures")
        )
        return candidates.filter { it.exists() && it.canRead() }.distinctBy { it.absolutePath }
    }

    private fun startMonitoringLoop() {
        serviceScope.launch {
            while (isActive) {
                runCatching { evaluateRansomwareSignals() }
                delay(10_000L)
            }
        }
    }

    private suspend fun evaluateRansomwareSignals() {
        val monitoredPackages = getMonitoredPackages(applicationContext)
        if (monitoredPackages.isEmpty()) return

        val now = System.currentTimeMillis()
        val windowStart = now - SIGNAL_WINDOW_MS
        val recentSignals = fileSignals.filter { it.timestampMs >= windowStart }
        trimSignalQueue(windowStart)

        if (recentSignals.isEmpty()) return

        val rapidFileAccess = recentSignals.size >= 120
        val renameSignals = recentSignals.count { signal ->
            val eventType = signal.event and FileObserver.ALL_EVENTS
            eventType == FileObserver.MOVED_FROM || eventType == FileObserver.MOVED_TO
        } >= 40
        val heavyWrites = recentSignals.count { signal ->
            val eventType = signal.event and FileObserver.ALL_EVENTS
            eventType == FileObserver.CLOSE_WRITE || eventType == FileObserver.CREATE
        } >= 80
        val encryptionPattern = recentSignals.any { signal ->
            val path = signal.path.orEmpty().lowercase()
            ENCRYPTION_EXTENSIONS.any { extension -> path.endsWith(extension) }
        }

        if (!(rapidFileAccess || renameSignals || heavyWrites || encryptionPattern)) return

        val activeMonitoredPackage = resolveForegroundMonitoredPackage(monitoredPackages)
            ?: monitoredPackages.first()
        val reasons = buildList {
            if (rapidFileAccess) add("Rapid file access")
            if (renameSignals) add("Mass rename pattern")
            if (heavyWrites) add("Abnormal I/O write activity")
            if (encryptionPattern) add("Encryption file extension pattern")
        }

        revokePermissionsAndContain(activeMonitoredPackage, reasons.joinToString())
    }

    private fun trimSignalQueue(cutoffMs: Long) {
        while (true) {
            val head = fileSignals.peek() ?: break
            if (head.timestampMs >= cutoffMs) break
            fileSignals.poll()
        }
    }

    private suspend fun revokePermissionsAndContain(packageName: String, reason: String) {
        PermissionSignals.sensitivePermissions
            .filter { it.startsWith("android.permission.") }
            .forEach { permission ->
                runCatching {
                    if (packageManager.checkPermission(permission, packageName) ==
                        PackageManager.PERMISSION_GRANTED
                    ) {
                        packageManager.revokeRuntimePermission(
                            packageName,
                            permission,
                            Process.myUserHandle()
                        )
                    }
                }
            }

        runCatching {
            getSystemService(ActivityManager::class.java).killBackgroundProcesses(packageName)
        }
        removeMonitoredPackage(applicationContext, packageName)

        securityLogger.logEvent(
            eventType = EventType.MONITORING_ALERT,
            packageName = packageName,
            decision = "REVOKE_AND_KILL",
            details = "Ransomware behavior detected: $reason"
        )
        securityLogger.logEvent(
            eventType = EventType.BLOCKED_APP,
            packageName = packageName,
            details = "App contained after ransomware indicators"
        )
        sendUserAlert(packageName, reason)
    }

    private fun resolveForegroundMonitoredPackage(monitoredPackages: Set<String>): String? {
        val endTime = System.currentTimeMillis()
        val beginTime = endTime - 120_000L
        val usageEvents = usageStatsManager.queryEvents(beginTime, endTime)
        val event = UsageEvents.Event()
        var lastMonitoredForeground: String? = null

        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            val isForeground = event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                    event.eventType == UsageEvents.Event.ACTIVITY_RESUMED)
            if (isForeground && monitoredPackages.contains(event.packageName)) {
                lastMonitoredForeground = event.packageName
            }
        }
        return lastMonitoredForeground
    }

    private fun sendUserAlert(packageName: String, reason: String) {
        val manager = getSystemService(NotificationManager::class.java)
        val launchIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            2002,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alert = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Ransomware activity blocked")
            .setContentText("$packageName was blocked: $reason")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "Human Intent Verification Shield revoked permissions from $packageName. " +
                        "Reason: $reason"
                )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        manager.notify(ALERT_NOTIFICATION_ID, alert)
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)
        val monitorChannel = NotificationChannel(
            SERVICE_CHANNEL_ID,
            "Security Monitoring",
            NotificationManager.IMPORTANCE_LOW
        )
        val alertChannel = NotificationChannel(
            ALERT_CHANNEL_ID,
            "Security Alerts",
            NotificationManager.IMPORTANCE_HIGH
        )
        manager.createNotificationChannel(monitorChannel)
        manager.createNotificationChannel(alertChannel)
    }

    private fun buildServiceNotification(content: String): Notification {
        val launchIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            2001,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, SERVICE_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Security Monitoring Service")
            .setContentText(content)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    companion object {
        private const val PREFS_NAME = "security_monitoring_prefs"
        private const val KEY_MONITORED_PACKAGES = "monitored_packages"
        private const val SIGNAL_WINDOW_MS = 15_000L
        private const val OBSERVER_MASK =
            FileObserver.CREATE or FileObserver.CLOSE_WRITE or
                FileObserver.MOVED_FROM or FileObserver.MOVED_TO or
                FileObserver.DELETE
        private const val SERVICE_CHANNEL_ID = "security_monitoring_channel"
        private const val ALERT_CHANNEL_ID = "security_monitoring_alert_channel"
        private const val NOTIFICATION_ID_MONITORING = 9201
        private const val ALERT_NOTIFICATION_ID = 9202
        private val ENCRYPTION_EXTENSIONS = setOf(
            ".encrypted",
            ".locked",
            ".enc",
            ".crypt",
            ".crypto",
            ".wncry"
        )

        fun addMonitoredPackage(context: Context, packageName: String) {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val current = prefs.getStringSet(KEY_MONITORED_PACKAGES, emptySet())?.toMutableSet()
                ?: mutableSetOf()
            current += packageName
            prefs.edit().putStringSet(KEY_MONITORED_PACKAGES, current).apply()
        }

        fun removeMonitoredPackage(context: Context, packageName: String) {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val current = prefs.getStringSet(KEY_MONITORED_PACKAGES, emptySet())?.toMutableSet()
                ?: mutableSetOf()
            if (current.remove(packageName)) {
                prefs.edit().putStringSet(KEY_MONITORED_PACKAGES, current).apply()
            }
        }

        fun getMonitoredPackages(context: Context): Set<String> {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getStringSet(KEY_MONITORED_PACKAGES, emptySet()).orEmpty()
        }
    }
}
