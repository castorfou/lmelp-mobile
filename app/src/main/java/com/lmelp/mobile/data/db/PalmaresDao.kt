package com.lmelp.mobile.data.db

import androidx.room.Dao
import androidx.room.Query
import com.lmelp.mobile.data.model.PalmaresEntity

@Dao
interface PalmaresDao {

    @Query("SELECT * FROM palmares WHERE nb_avis >= 2 ORDER BY rank ASC")
    suspend fun getAllPalmares(): List<PalmaresEntity>

    @Query("SELECT * FROM palmares WHERE nb_avis >= 2 ORDER BY rank ASC LIMIT :limit")
    suspend fun getTopPalmares(limit: Int): List<PalmaresEntity>

    @Query("""
        SELECT * FROM palmares
        WHERE nb_avis >= 2
        AND (
            (:afficherLus = 1 AND calibre_lu = 1)
            OR (:afficherNonLus = 1 AND calibre_lu = 0)
        )
        ORDER BY rank ASC
    """)
    suspend fun getPalmaresFiltres(afficherLus: Int, afficherNonLus: Int): List<PalmaresEntity>
}
