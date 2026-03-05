package com.lmelp.mobile.data.db

import androidx.room.Dao
import androidx.room.Query
import com.lmelp.mobile.data.model.AvisCritiquesEntity

@Dao
interface AvisCritiquesDao {

    @Query("SELECT * FROM avis_critiques WHERE emission_id = :emissionId LIMIT 1")
    suspend fun getByEmissionId(emissionId: String): AvisCritiquesEntity?
}
