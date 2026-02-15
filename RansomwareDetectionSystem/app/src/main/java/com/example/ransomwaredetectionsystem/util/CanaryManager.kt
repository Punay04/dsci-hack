package com.example.ransomwaredetectionsystem.util

import android.content.Context
import com.example.ransomwaredetectionsystem.data.AppDatabase
import com.example.ransomwaredetectionsystem.data.CanaryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

class CanaryManager(private val context: Context) {
    private val db = AppDatabase.getDatabase(context)
    private val canaryDao = db.canaryDao()

    private val folderNames = listOf("Documents", "MediaCache", "BackupData", "SystemConfig", "UserAppData", "ProjectAssets", "SecureVault")
    private val fileNames = listOf("salary_data.xlsx", "id_scan.pdf", "passwords_backup.txt", "confidential.docx", "financial_report.csv")

    val canaryBaseDir: String
        get() = (context.getExternalFilesDir(null) ?: context.filesDir).absolutePath

    suspend fun generateCanaries(): Int = withContext(Dispatchers.IO) {
        val rootDir = context.getExternalFilesDir(null) ?: context.filesDir
        val canaries = mutableListOf<CanaryEntity>()

        // Generate 5-10 folders
        val numFolders = (5..10).random()
        val selectedFolders = folderNames.shuffled().take(numFolders)

        selectedFolders.forEach { folderName ->
            val folder = File(rootDir, ".$folderName") // Hidden folder
            if (!folder.exists()) folder.mkdirs()

            // Create 1-2 files per folder
            val numFiles = (1..2).random()
            fileNames.shuffled().take(numFiles).forEach { fileName ->
                val file = File(folder, ".$fileName") // Hidden file
                if (!file.exists()) {
                    file.writeText("Dummy content for $fileName: " + System.currentTimeMillis())
                }

                val hash = calculateHash(file)
                canaries.add(
                    CanaryEntity(
                        filePath = file.absolutePath,
                        fileSize = file.length(),
                        fileHash = hash,
                        lastModified = file.lastModified()
                    )
                )
            }
        }
        canaryDao.deleteAll()
        canaryDao.insertCanaries(canaries)
        return@withContext canaries.size
    }

    suspend fun getCanaryCount(): Int = withContext(Dispatchers.IO) {
        return@withContext canaryDao.getAllCanaries().size
    }

    suspend fun getCanaryPaths(): List<String> = withContext(Dispatchers.IO) {
        return@withContext canaryDao.getAllCanaries().map { it.filePath }
    }

    suspend fun checkThreats(): Boolean = withContext(Dispatchers.IO) {
        val storedCanaries = canaryDao.getAllCanaries()
        if (storedCanaries.isEmpty()) return@withContext false

        for (stored in storedCanaries) {
            val file = File(stored.filePath)
            if (!file.exists()) return@withContext true // File deleted
            if (file.length() != stored.fileSize) return@withContext true // File size changed
            if (calculateHash(file) != stored.fileHash) return@withContext true // Hash changed
        }
        return@withContext false
    }

    private fun calculateHash(file: File): String {
        val bytes = file.readBytes()
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
}
