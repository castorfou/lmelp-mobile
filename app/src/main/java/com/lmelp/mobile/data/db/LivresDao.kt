package com.lmelp.mobile.data.db

import androidx.room.Dao
import androidx.room.Query
import com.lmelp.mobile.data.model.AvisEntity
import com.lmelp.mobile.data.model.LivreEntity

@Dao
interface LivresDao {

    @Query("SELECT * FROM livres WHERE id = :id")
    suspend fun getLivreById(id: String): LivreEntity?

    @Query("""
        SELECT l.* FROM livres l
        JOIN emission_livres el ON el.livre_id = l.id
        WHERE el.emission_id = :emissionId
    """)
    suspend fun getLivresByEmission(emissionId: String): List<LivreEntity>

    @Query("SELECT * FROM avis WHERE livre_id = :livreId ORDER BY created_at DESC")
    suspend fun getAvisByLivre(livreId: String): List<AvisEntity>
}
