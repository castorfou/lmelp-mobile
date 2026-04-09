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

/** Livre enrichi avec les données Calibre depuis la table palmares (LEFT JOIN). */
data class LivreAvecCalibreRow(
    val id: String,
    val titre: String,
    @ColumnInfo(name = "auteur_id") val auteurId: String?,
    @ColumnInfo(name = "auteur_nom") val auteurNom: String?,
    val editeur: String?,
    @ColumnInfo(name = "url_babelio") val urlBabelio: String?,
    @ColumnInfo(name = "url_cover") val urlCover: String?,
    @ColumnInfo(name = "created_at") val createdAt: String?,
    @ColumnInfo(name = "updated_at") val updatedAt: String?,
    @ColumnInfo(name = "calibre_in_library", defaultValue = "0") val calibreInLibrary: Int = 0,
    @ColumnInfo(name = "calibre_lu", defaultValue = "0") val calibreLu: Int = 0,
    @ColumnInfo(name = "calibre_rating") val calibreRating: Double? = null
)

@Dao
interface LivresDao {

    @Query("SELECT * FROM livres WHERE id = :id")
    suspend fun getLivreById(id: String): LivreEntity?

    @Query("""
        SELECT l.id, l.titre, l.auteur_id, l.auteur_nom, l.editeur,
               l.url_babelio, l.url_cover, l.created_at, l.updated_at,
               COALESCE(p.calibre_in_library, 0) as calibre_in_library,
               COALESCE(p.calibre_lu, 0) as calibre_lu,
               p.calibre_rating
        FROM livres l
        LEFT JOIN palmares p ON p.livre_id = l.id
        WHERE l.id = :id
    """)
    suspend fun getLivreAvecCalibreById(id: String): LivreAvecCalibreRow?

    @Query("""
        SELECT l.* FROM livres l
        JOIN emission_livres el ON el.livre_id = l.id
        WHERE el.emission_id = :emissionId
    """)
    suspend fun getLivresByEmission(emissionId: String): List<LivreEntity>

    @Query("""
        SELECT l.id, l.titre, l.auteur_id, l.auteur_nom, l.editeur,
               l.url_babelio, l.url_cover, l.created_at, l.updated_at,
               COALESCE(p.calibre_in_library, 0) as calibre_in_library,
               COALESCE(p.calibre_lu, 0) as calibre_lu,
               p.calibre_rating
        FROM livres l
        JOIN emission_livres el ON el.livre_id = l.id
        LEFT JOIN palmares p ON p.livre_id = l.id
        WHERE el.emission_id = :emissionId
    """)
    suspend fun getLivresAvecCalibreByEmission(emissionId: String): List<LivreAvecCalibreRow>

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
