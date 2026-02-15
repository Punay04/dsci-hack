package com.example.ransomwaredetectionsystem.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "canary_files")
data class CanaryEntity(
    @PrimaryKey val filePath: String,
    val fileSize: Long,
    val fileHash: String,
    val lastModified: Long
)
