package com.lmelp.mobile.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Query
import com.lmelp.mobile.data.model.PalmaresEntity

data class PalmaresFiltreAvecUrlRow(
    val rank: Int,
    @ColumnInfo(name = "livre_id")        val livreId: String,
    val titre: String,
    @ColumnInfo(name = "auteur_nom")      val auteurNom: String?,
    @ColumnInfo(name = "note_moyenne")    val noteMoyenne: Double,
    @ColumnInfo(name = "nb_avis")         val nbAvis: Int,
    @ColumnInfo(name = "nb_critiques")    val nbCritiques: Int,
    @ColumnInfo(name = "calibre_in_library") val calibreInLibrary: Int = 0,
    @ColumnInfo(name = "calibre_lu")      val calibreLu: Int = 0,
    @ColumnInfo(name = "calibre_rating")  val calibreRating: Double? = null,
    @ColumnInfo(name = "url_cover")       val urlCover: String?
)

data class PalmaresAvecUrlRow(
    val rank: Int,
    @ColumnInfo(name = "livre_id")     val livreId: String,
    val titre: String,
    @ColumnInfo(name = "auteur_nom")   val auteurNom: String?,
    @ColumnInfo(name = "note_moyenne") val noteMoyenne: Double,
    @ColumnInfo(name = "url_babelio")  val urlBabelio: String?,
    @ColumnInfo(name = "url_cover")    val urlCover: String?
)

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

    @Query("""
        SELECT p.rank, p.livre_id, p.titre, p.auteur_nom, p.note_moyenne, l.url_babelio, l.url_cover
        FROM palmares p
        JOIN livres l ON l.id = p.livre_id
        WHERE p.nb_avis >= 2
          AND (COALESCE(p.calibre_in_library, 0) = 0 OR COALESCE(p.calibre_lu, 0) = 0)
        ORDER BY p.rank ASC
        LIMIT :limit
    """)
    suspend fun getTopPalmaresAvecUrl(limit: Int): List<PalmaresAvecUrlRow>

    @Query("""
        SELECT p.rank, p.livre_id, p.titre, p.auteur_nom, p.note_moyenne,
               p.nb_avis, p.nb_critiques, p.calibre_in_library, p.calibre_lu, p.calibre_rating,
               l.url_cover
        FROM palmares p
        LEFT JOIN livres l ON l.id = p.livre_id
        WHERE p.nb_avis >= 2
        AND (
            (:afficherLus = 1 AND p.calibre_lu = 1)
            OR (:afficherNonLus = 1 AND p.calibre_lu = 0)
        )
        ORDER BY p.rank ASC
    """)
    suspend fun getPalmaresFiltresAvecUrl(afficherLus: Int, afficherNonLus: Int): List<PalmaresFiltreAvecUrlRow>
}
