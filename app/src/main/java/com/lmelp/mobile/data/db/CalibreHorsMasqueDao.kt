package com.lmelp.mobile.data.db

import androidx.room.Dao
import androidx.room.Query
import com.lmelp.mobile.data.model.CalibreHorsMasqueEntity

@Dao
interface CalibreHorsMasqueDao {

    @Query("""
        SELECT * FROM calibre_hors_masque
        ORDER BY
            CASE WHEN calibre_rating IS NULL THEN 1 ELSE 0 END,
            calibre_rating DESC,
            titre ASC
    """)
    suspend fun getAll(): List<CalibreHorsMasqueEntity>

    @Query("""
        SELECT * FROM calibre_hors_masque
        ORDER BY
            CASE WHEN date_lecture IS NULL THEN 1 ELSE 0 END,
            date_lecture DESC,
            titre ASC
    """)
    suspend fun getAllParDate(): List<CalibreHorsMasqueEntity>

    @Query("SELECT * FROM calibre_hors_masque WHERE auteur_nom = :auteurNom ORDER BY calibre_rating DESC, titre ASC")
    suspend fun getByAuteurNom(auteurNom: String): List<CalibreHorsMasqueEntity>
}
