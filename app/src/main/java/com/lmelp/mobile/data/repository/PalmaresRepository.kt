package com.lmelp.mobile.data.repository

import com.lmelp.mobile.data.db.CalibreHorsMasqueDao
import com.lmelp.mobile.data.db.MonPalmaresRow
import com.lmelp.mobile.data.db.PalmaresFiltreAvecUrlRow
import com.lmelp.mobile.data.db.PalmaresDao
import com.lmelp.mobile.data.model.CalibreHorsMasqueEntity
import com.lmelp.mobile.data.model.CalibreHorsMasqueUi
import com.lmelp.mobile.data.model.MonPalmaresItemUi
import com.lmelp.mobile.data.model.PalmaresEntity
import com.lmelp.mobile.data.model.PalmaresUi

class PalmaresRepository(
    private val palmaresDao: PalmaresDao,
    private val horsMasqueDao: CalibreHorsMasqueDao? = null
) {

    suspend fun getAllPalmares(): List<PalmaresUi> {
        return palmaresDao.getAllPalmares().map { it.toUi() }
    }

    suspend fun getPalmaresFiltres(afficherLus: Boolean, afficherNonLus: Boolean): List<PalmaresUi> {
        return palmaresDao.getPalmaresFiltresAvecUrl(
            afficherLus = if (afficherLus) 1 else 0,
            afficherNonLus = if (afficherNonLus) 1 else 0
        ).map { it.toUi() }
    }

    suspend fun getMonPalmares(): List<PalmaresUi> =
        palmaresDao.getMonPalmares().map { it.toUi() }

    suspend fun getMonPalmaresParDate(): List<PalmaresUi> =
        palmaresDao.getMonPalmaresParDate().map { it.toUi() }

    /** Livres hors Masque triés par note décroissante, sans note en fin. */
    suspend fun getMonPalmaresHorsMasque(): List<MonPalmaresItemUi> =
        horsMasqueDao?.getAll().orEmpty().map { it.toItemUi() }

    /** Livres hors Masque triés par date lecture décroissante, sans date en fin. */
    suspend fun getMonPalmaresHorsMasqueParDate(): List<MonPalmaresItemUi> =
        horsMasqueDao?.getAllParDate().orEmpty().map { it.toItemUi() }

    /** Livres hors Masque pour un auteur donné (match sur auteur_nom). */
    suspend fun getHorsMasqueByAuteurNom(auteurNom: String): List<CalibreHorsMasqueUi> =
        horsMasqueDao?.getByAuteurNom(auteurNom).orEmpty().map { it.toCalibreUi() }

    /** Fusionne livres Masque + hors Masque en une liste unifiée triée par note. */
    suspend fun getMonPalmaresUnifieParNote(): List<MonPalmaresItemUi> {
        val masque = palmaresDao.getMonPalmares().map { it.toItemUi() }
        val horsMasque = horsMasqueDao?.getAll().orEmpty().map { it.toItemUi() }
        return (masque + horsMasque).sortedWith(
            compareBy(
                { if (it.calibreRating == null) 1 else 0 },
                { -(it.calibreRating ?: 0.0) },
                { it.titre }
            )
        )
    }

    /**
     * Retourne le nombre de jours de lecture pour un livre donné (null si non calculable :
     * 1er livre lu, pas de date_lecture, ou livreId inconnu).
     */
    suspend fun getJoursLecturePourLivre(livreId: String): Int? {
        val masque = palmaresDao.getMonPalmares().map { it.toItemUi() }
        val horsMasque = horsMasqueDao?.getAll().orEmpty().map { it.toItemUi() }
        val avecDate = (masque + horsMasque)
            .filter { it.dateLecture != null }
            .sortedBy { it.dateLecture }
        val idx = avecDate.indexOfFirst { it.id == livreId }
        if (idx <= 0) return null  // non trouvé ou 1er livre
        return calculerJoursEntre(avecDate[idx - 1].dateLecture!!, avecDate[idx].dateLecture!!)
    }

    /** Fusionne livres Masque + hors Masque et calcule la vitesse de lecture (jours entre livres consécutifs). */
    suspend fun getMonPalmaresUnifieParVitesse(ascendant: Boolean = true): List<MonPalmaresItemUi> {
        val masque = palmaresDao.getMonPalmares().map { it.toItemUi() }
        val horsMasque = horsMasqueDao?.getAll().orEmpty().map { it.toItemUi() }
        return calculerVitesse(masque + horsMasque, ascendant)
    }

    private fun calculerVitesse(items: List<MonPalmaresItemUi>, ascendant: Boolean): List<MonPalmaresItemUi> {
        val avecDate = items
            .filter { it.dateLecture != null }
            .sortedBy { it.dateLecture }
        val resultat = mutableListOf<MonPalmaresItemUi>()
        for (i in 1 until avecDate.size) {
            val prev = avecDate[i - 1]
            val curr = avecDate[i]
            val jours = calculerJoursEntre(prev.dateLecture!!, curr.dateLecture!!)
            resultat.add(curr.copy(joursLecture = jours))
        }
        return if (ascendant)
            resultat.sortedWith(compareBy({ it.joursLecture ?: Int.MAX_VALUE }, { it.titre }))
        else
            resultat.sortedWith(compareByDescending<MonPalmaresItemUi> { it.joursLecture ?: 0 }.thenBy { it.titre })
    }

    private fun calculerJoursEntre(date1: String, date2: String): Int {
        val d1 = java.time.LocalDate.parse(date1)
        val d2 = java.time.LocalDate.parse(date2)
        return java.time.temporal.ChronoUnit.DAYS.between(d1, d2).toInt()
    }

    /** Fusionne livres Masque + hors Masque en une liste unifiée triée par date. */
    suspend fun getMonPalmaresUnifieParDate(): List<MonPalmaresItemUi> {
        val masque = palmaresDao.getMonPalmaresParDate().map { it.toItemUi() }
        val horsMasque = horsMasqueDao?.getAllParDate().orEmpty().map { it.toItemUi() }
        return (masque + horsMasque).sortedWith(
            compareBy(
                { if (it.dateLecture == null) 1 else 0 },
                { it.dateLecture?.let { d -> -d.replace("-", "").toLong() } ?: 0L },
                { it.titre }
            )
        )
    }

    private fun PalmaresEntity.toUi() = PalmaresUi(
        rank = rank,
        livreId = livreId,
        titre = titre,
        auteurNom = auteurNom,
        noteMoyenne = noteMoyenne,
        nbAvis = nbAvis,
        nbCritiques = nbCritiques,
        calibreInLibrary = calibreInLibrary == 1,
        calibreLu = calibreLu == 1,
        calibreRating = calibreRating
    )

    private fun PalmaresFiltreAvecUrlRow.toUi() = PalmaresUi(
        rank = rank,
        livreId = livreId,
        titre = titre,
        auteurNom = auteurNom,
        noteMoyenne = noteMoyenne,
        nbAvis = nbAvis,
        nbCritiques = nbCritiques,
        calibreInLibrary = calibreInLibrary == 1,
        calibreLu = calibreLu == 1,
        calibreRating = calibreRating,
        urlCover = urlCover,
        dateLecture = dateLecture
    )

    private fun MonPalmaresRow.toUi() = PalmaresUi(
        rank = 0,
        livreId = livreId,
        titre = titre,
        auteurNom = auteurNom,
        noteMoyenne = noteMoyenne,
        nbAvis = nbAvis,
        nbCritiques = nbCritiques,
        calibreInLibrary = true,
        calibreLu = true,
        calibreRating = calibreRating,
        urlCover = urlCover,
        dateLecture = dateLecture
    )

    private fun MonPalmaresRow.toItemUi() = MonPalmaresItemUi(
        id = livreId,
        titre = titre,
        auteurNom = auteurNom,
        calibreRating = calibreRating,
        dateLecture = dateLecture,
        livreId = livreId,
        urlCover = urlCover
    )

    private fun CalibreHorsMasqueEntity.toItemUi() = MonPalmaresItemUi(
        id = id,
        titre = titre,
        auteurNom = auteurNom,
        calibreRating = calibreRating,
        dateLecture = dateLecture,
        livreId = null,
        urlCover = null
    )

    private fun CalibreHorsMasqueEntity.toCalibreUi() = CalibreHorsMasqueUi(
        id = id,
        titre = titre,
        auteurNom = auteurNom,
        calibreRating = calibreRating,
        dateLecture = dateLecture
    )
}
