package com.example.ransomwaredetectionsystem.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CanaryDao {
    @Query("SELECT * FROM canary_files")
    suspend fun getAllCanaries(): List<CanaryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCanaries(canaries: List<CanaryEntity>)

    @Query("DELETE FROM canary_files")
    suspend fun deleteAll()
}
