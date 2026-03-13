package com.lmelp.mobile.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Query
import com.lmelp.mobile.data.model.EmissionEntity

/** Titre et date de la dernière émission (jointure emissions → episodes). */
data class DerniereEmissionRow(
    @ColumnInfo(name = "titre") val titre: String?,
    @ColumnInfo(name = "date") val date: String?
)

/** Meilleur livre (note moyenne) d'une émission, avec son url_babelio. */
data class TopLivreEmissionRow(
    @ColumnInfo(name = "emission_id")   val emissionId: String,
    @ColumnInfo(name = "emission_date") val emissionDate: String,
    @ColumnInfo(name = "livre_id")      val livreId: String,
    @ColumnInfo(name = "livre_titre")   val livreTitre: String,
    @ColumnInfo(name = "url_babelio")   val urlBabelio: String?,
    @ColumnInfo(name = "note_moyenne")  val noteMoyenne: Double
)

@Dao
interface EmissionsDao {

    @Query("SELECT * FROM emissions ORDER BY date DESC")
    suspend fun getAllEmissions(): List<EmissionEntity>

    @Query("SELECT * FROM emissions WHERE id = :id")
    suspend fun getEmissionById(id: String): EmissionEntity?

    @Query("SELECT COUNT(*) FROM emissions")
    suspend fun count(): Int

    @Query("""
        SELECT ep.titre, em.date
        FROM emissions em
        JOIN episodes ep ON ep.id = em.episode_id
        ORDER BY em.date DESC LIMIT 1
    """)
    suspend fun getDerniereEmission(): DerniereEmissionRow?

    @Query("""
        SELECT em.id AS emission_id,
               em.date AS emission_date,
               a.livre_id,
               l.titre AS livre_titre,
               l.url_babelio,
               AVG(a.note) AS note_moyenne
        FROM emissions em
        JOIN avis a ON a.emission_id = em.id
        JOIN livres l ON l.id = a.livre_id
        WHERE a.note IS NOT NULL AND a.section = 'programme'
        GROUP BY em.id, a.livre_id
        HAVING AVG(a.note) = (
            SELECT MAX(avg_inner) FROM (
                SELECT AVG(a2.note) AS avg_inner
                FROM avis a2
                WHERE a2.emission_id = em.id AND a2.note IS NOT NULL AND a2.section = 'programme'
                GROUP BY a2.livre_id
            )
        )
        ORDER BY em.date DESC
        LIMIT :limit
    """)
    suspend fun getTopLivreParEmission(limit: Int): List<TopLivreEmissionRow>
}
