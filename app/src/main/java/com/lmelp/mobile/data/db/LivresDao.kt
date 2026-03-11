package com.lmelp.mobile.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Query
import com.lmelp.mobile.data.model.AvisEntity
import com.lmelp.mobile.data.model.LivreEntity

/** Résultat de la requête note moyenne + section par livre pour une émission. */
data class LivreNoteSection(
    @ColumnInfo(name = "livre_id") val livreId: String,
    @ColumnInfo(name = "avg_note") val avgNote: Double?,
    val section: String?
)

/** Avis enrichi avec le titre et la date de l'émission associée. */
data class AvisAvecEmissionRow(
    @Embedded val avis: AvisEntity,
    @ColumnInfo(name = "emission_titre") val emissionTitre: String?,
    @ColumnInfo(name = "emission_date") val emissionDate: String?
)

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

    @Query("SELECT livre_id, AVG(note) as avg_note, section FROM avis WHERE emission_id = :emissionId GROUP BY livre_id")
    suspend fun getNotesParLivreForEmission(emissionId: String): List<LivreNoteSection>

    @Query("""
        SELECT a.*, ep.titre as emission_titre, em.date as emission_date
        FROM avis a
        JOIN emissions em ON em.id = a.emission_id
        JOIN episodes ep ON ep.id = em.episode_id
        WHERE a.livre_id = :livreId
    """)
    suspend fun getAvisAvecEmissionByLivre(livreId: String): List<AvisAvecEmissionRow>
}
