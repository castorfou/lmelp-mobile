package com.lmelp.mobile.data.db

import androidx.room.Dao
import androidx.room.Query
import com.lmelp.mobile.data.model.CritiqueEntity

@Dao
interface CritiquesDao {

    @Query("SELECT * FROM critiques ORDER BY nom ASC")
    suspend fun getAllCritiques(): List<CritiqueEntity>

    @Query("SELECT * FROM critiques WHERE id = :id")
    suspend fun getCritiqueById(id: String): CritiqueEntity?
}
