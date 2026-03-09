package com.lmelp.mobile.data.db

import androidx.room.Dao
import androidx.room.Query
import com.lmelp.mobile.data.model.RecommendationAvecCalibreEntity
import com.lmelp.mobile.data.model.RecommendationEntity

@Dao
interface RecommendationsDao {

    @Query("SELECT * FROM recommendations ORDER BY rank ASC")
    suspend fun getAllRecommendations(): List<RecommendationEntity>

    @Query("SELECT * FROM recommendations ORDER BY rank ASC LIMIT :limit")
    suspend fun getTopRecommendations(limit: Int): List<RecommendationEntity>

    /**
     * Retourne les recommandations en excluant les livres déjà lus dans Calibre.
     * Un livre est exclu seulement si calibre_in_library = 1 ET calibre_lu = 1.
     */
    @Query("""
        SELECT r.*,
               COALESCE(p.calibre_in_library, 0) AS calibre_in_library,
               COALESCE(p.calibre_lu, 0) AS calibre_lu
        FROM recommendations r
        LEFT JOIN palmares p ON r.livre_id = p.livre_id
        WHERE COALESCE(p.calibre_in_library, 0) = 0
           OR COALESCE(p.calibre_lu, 0) = 0
        ORDER BY r.rank ASC
    """)
    suspend fun getRecommandationsNonLues(): List<RecommendationAvecCalibreEntity>
}
