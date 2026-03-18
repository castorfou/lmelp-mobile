package com.lmelp.mobile.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Query
import com.lmelp.mobile.data.model.RecommendationAvecCalibreEntity
import com.lmelp.mobile.data.model.RecommendationEntity

data class RecommandationNonLueAvecUrlRow(
    val rank: Int,
    @ColumnInfo(name = "livre_id")      val livreId: String,
    val titre: String,
    @ColumnInfo(name = "auteur_nom")    val auteurNom: String?,
    @ColumnInfo(name = "score_hybride") val scoreHybride: Double,
    @ColumnInfo(name = "masque_mean")   val masqueMean: Double?,
    @ColumnInfo(name = "calibre_in_library") val calibreInLibrary: Int = 0,
    @ColumnInfo(name = "calibre_lu")    val calibreLu: Int = 0,
    @ColumnInfo(name = "url_cover")     val urlCover: String?
)

data class RecommendationAvecUrlRow(
    val rank: Int,
    @ColumnInfo(name = "livre_id")      val livreId: String,
    val titre: String,
    @ColumnInfo(name = "auteur_nom")    val auteurNom: String?,
    @ColumnInfo(name = "score_hybride") val scoreHybride: Double,
    @ColumnInfo(name = "url_babelio")   val urlBabelio: String?,
    @ColumnInfo(name = "url_cover")     val urlCover: String?
)

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

    @Query("""
        SELECT r.rank, r.livre_id, r.titre, r.auteur_nom, r.score_hybride, l.url_babelio, l.url_cover
        FROM recommendations r
        JOIN livres l ON l.id = r.livre_id
        ORDER BY r.rank ASC
        LIMIT :limit
    """)
    suspend fun getTopRecommendationsAvecUrl(limit: Int): List<RecommendationAvecUrlRow>

    @Query("""
        SELECT r.rank, r.livre_id, r.titre, r.auteur_nom, r.score_hybride, l.url_babelio, l.url_cover
        FROM recommendations r
        JOIN livres l ON l.id = r.livre_id
        LEFT JOIN palmares p ON r.livre_id = p.livre_id
        WHERE COALESCE(p.calibre_in_library, 0) = 0
           OR COALESCE(p.calibre_lu, 0) = 0
        ORDER BY r.rank ASC
        LIMIT :limit
    """)
    suspend fun getTopRecommandationsNonLuesAvecUrl(limit: Int): List<RecommendationAvecUrlRow>

    @Query("""
        SELECT r.rank, r.livre_id, r.titre, r.auteur_nom, r.score_hybride, r.masque_mean,
               COALESCE(p.calibre_in_library, 0) AS calibre_in_library,
               COALESCE(p.calibre_lu, 0) AS calibre_lu,
               l.url_cover
        FROM recommendations r
        LEFT JOIN palmares p ON r.livre_id = p.livre_id
        LEFT JOIN livres l ON l.id = r.livre_id
        WHERE COALESCE(p.calibre_in_library, 0) = 0
           OR COALESCE(p.calibre_lu, 0) = 0
        ORDER BY r.rank ASC
    """)
    suspend fun getRecommandationsNonLuesAvecUrl(): List<RecommandationNonLueAvecUrlRow>
}
