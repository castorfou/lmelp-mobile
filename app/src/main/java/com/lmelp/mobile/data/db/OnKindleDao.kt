package com.lmelp.mobile.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Query
import com.lmelp.mobile.data.model.OnKindleEntity

data class OnKindleAvecConseilRow(
    @ColumnInfo(name = "livre_id")      val livreId: String,
    val titre: String,
    @ColumnInfo(name = "auteur_nom")    val auteurNom: String?,
    @ColumnInfo(name = "url_babelio")   val urlBabelio: String?,
    @ColumnInfo(name = "url_cover")     val urlCover: String?,
    @ColumnInfo(name = "calibre_lu")    val calibreLu: Int,
    @ColumnInfo(name = "calibre_rating") val calibreRating: Double?,
    @ColumnInfo(name = "note_moyenne")  val noteMoyenne: Double?,
    @ColumnInfo(name = "nb_avis")       val nbAvis: Int,
    @ColumnInfo(name = "score_hybride") val scoreHybride: Double?
)

@Dao
interface OnKindleDao {

    @Query("""
        SELECT ok.livre_id, ok.titre, ok.auteur_nom, ok.url_babelio, ok.url_cover,
               ok.calibre_lu, ok.calibre_rating, ok.note_moyenne, ok.nb_avis,
               r.score_hybride
        FROM onkindle ok
        LEFT JOIN recommendations r ON r.livre_id = ok.livre_id
        WHERE (:afficherLus = 1 AND ok.calibre_lu = 1) OR (:afficherNonLus = 1 AND ok.calibre_lu = 0)
    """)
    suspend fun getOnKindleAvecConseil(afficherLus: Int, afficherNonLus: Int): List<OnKindleAvecConseilRow>

    @Query("SELECT * FROM onkindle WHERE url_babelio IS NOT NULL LIMIT :limit")
    suspend fun getTopOnKindleAvecUrl(limit: Int): List<OnKindleEntity>
}
