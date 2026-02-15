package com.example.ransomwaredetectionsystem.security.domain

import android.Manifest

object PermissionSignals {
    const val CAPABILITY_ACCESSIBILITY = "capability.ACCESSIBILITY_SERVICE"
    const val CAPABILITY_DEVICE_ADMIN = "capability.DEVICE_ADMIN"
    const val CAPABILITY_BACKGROUND_SERVICE = "capability.BACKGROUND_SERVICE"
    const val CAPABILITY_FULL_STORAGE = "capability.FULL_STORAGE_ACCESS"
    const val CAPABILITY_MEDIA_CONTROL = Manifest.permission.MEDIA_CONTENT_CONTROL
    const val CAPABILITY_OVERLAY = Manifest.permission.SYSTEM_ALERT_WINDOW

    val storagePermissions = setOf(
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.MANAGE_EXTERNAL_STORAGE,
        CAPABILITY_FULL_STORAGE
    )

    val sensitivePermissions = setOf(
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.CAMERA,
        Manifest.permission.READ_SMS,
        Manifest.permission.READ_PHONE_STATE,
        CAPABILITY_OVERLAY,
        CAPABILITY_MEDIA_CONTROL,
        CAPABILITY_ACCESSIBILITY,
        CAPABILITY_DEVICE_ADMIN,
        CAPABILITY_BACKGROUND_SERVICE,
        CAPABILITY_FULL_STORAGE,
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.MANAGE_EXTERNAL_STORAGE
    )
}
