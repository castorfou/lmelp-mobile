package com.lmelp.mobile.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Query
import com.lmelp.mobile.data.model.CritiqueEntity

data class AvisParCritiqueRow(
    @ColumnInfo(name = "livreId") val livreId: String,
    @ColumnInfo(name = "livreTitre") val livreTitre: String?,
    @ColumnInfo(name = "auteurNom") val auteurNom: String?,
    val note: Double?,
    @ColumnInfo(name = "emissionId") val emissionId: String,
    @ColumnInfo(name = "emissionDate") val emissionDate: String?
)

@Dao
interface CritiquesDao {

    @Query("SELECT * FROM critiques ORDER BY nom ASC")
    suspend fun getAllCritiques(): List<CritiqueEntity>

    @Query("SELECT * FROM critiques WHERE id = :id")
    suspend fun getCritiqueById(id: String): CritiqueEntity?

    @Query("""
        SELECT a.livre_id as livreId, a.livre_titre as livreTitre,
               a.auteur_nom as auteurNom, a.note, a.emission_id as emissionId,
               em.date as emissionDate
        FROM avis a
        JOIN emissions em ON em.id = a.emission_id
        WHERE a.critique_id = :critiqueId
        ORDER BY a.note DESC
    """)
    suspend fun getAvisByCritique(critiqueId: String): List<AvisParCritiqueRow>
}
