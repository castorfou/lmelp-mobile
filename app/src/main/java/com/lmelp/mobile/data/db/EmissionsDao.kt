package com.lmelp.mobile.data.db

import androidx.room.Dao
import androidx.room.Query
import com.lmelp.mobile.data.model.EmissionEntity

@Dao
interface EmissionsDao {

    @Query("SELECT * FROM emissions ORDER BY date DESC")
    suspend fun getAllEmissions(): List<EmissionEntity>

    @Query("SELECT * FROM emissions WHERE id = :id")
    suspend fun getEmissionById(id: String): EmissionEntity?

    @Query("SELECT COUNT(*) FROM emissions")
    suspend fun count(): Int
}
