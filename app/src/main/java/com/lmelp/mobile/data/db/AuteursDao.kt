package com.lmelp.mobile.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Query
import com.lmelp.mobile.data.model.AuteurEntity

/** Livre d'un auteur avec note moyenne (depuis palmares), date de dernière émission et données Calibre. */
data class LivreParAuteurRow(
    @ColumnInfo(name = "livre_id") val livreId: String,
    val titre: String,
    @ColumnInfo(name = "note_moyenne") val noteMoyenne: Double?,
    @ColumnInfo(name = "derniere_emission_date") val derniereEmissionDate: String?,
    @ColumnInfo(name = "calibre_in_library", defaultValue = "0") val calibreInLibrary: Int = 0,
    @ColumnInfo(name = "calibre_lu", defaultValue = "0") val calibreLu: Int = 0,
    @ColumnInfo(name = "calibre_rating") val calibreRating: Double? = null
)

@Dao
interface AuteursDao {

    @Query("SELECT * FROM auteurs WHERE id = :id")
    suspend fun getAuteurById(id: String): AuteurEntity?

    @Query("""
        SELECT l.id as livre_id, l.titre,
               p.note_moyenne,
               MAX(em.date) as derniere_emission_date,
               COALESCE(p.calibre_in_library, 0) as calibre_in_library,
               COALESCE(p.calibre_lu, 0) as calibre_lu,
               p.calibre_rating
        FROM livres l
        LEFT JOIN palmares p ON p.livre_id = l.id
        LEFT JOIN avis a ON a.livre_id = l.id
        LEFT JOIN emissions em ON em.id = a.emission_id
        WHERE l.auteur_id = :auteurId
        GROUP BY l.id
    """)
    suspend fun getLivresParAuteur(auteurId: String): List<LivreParAuteurRow>
}
