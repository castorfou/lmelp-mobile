package com.lmelp.mobile.data.db

import androidx.room.Dao
import androidx.room.Query
import com.lmelp.mobile.data.model.RecommendationEntity

@Dao
interface RecommendationsDao {

    @Query("SELECT * FROM recommendations ORDER BY rank ASC")
    suspend fun getAllRecommendations(): List<RecommendationEntity>

    @Query("SELECT * FROM recommendations ORDER BY rank ASC LIMIT :limit")
    suspend fun getTopRecommendations(limit: Int): List<RecommendationEntity>
}
