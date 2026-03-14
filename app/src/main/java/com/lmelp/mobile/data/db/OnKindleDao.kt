package com.lmelp.mobile.data.db

import androidx.room.Dao
import androidx.room.Query
import com.lmelp.mobile.data.model.OnKindleEntity

@Dao
interface OnKindleDao {

    @Query("""
        SELECT * FROM onkindle
        WHERE (:afficherLus = 1 AND calibre_lu = 1) OR (:afficherNonLus = 1 AND calibre_lu = 0)
    """)
    suspend fun getOnKindleFiltres(afficherLus: Int, afficherNonLus: Int): List<OnKindleEntity>

    @Query("SELECT * FROM onkindle WHERE url_babelio IS NOT NULL LIMIT :limit")
    suspend fun getTopOnKindleAvecUrl(limit: Int): List<OnKindleEntity>
}
