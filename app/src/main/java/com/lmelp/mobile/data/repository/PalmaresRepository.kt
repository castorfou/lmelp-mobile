package com.lmelp.mobile.data.repository

import com.lmelp.mobile.data.db.PalmaresDao
import com.lmelp.mobile.data.model.PalmaresEntity
import com.lmelp.mobile.data.model.PalmaresUi

class PalmaresRepository(
    private val palmaresDao: PalmaresDao
) {

    suspend fun getAllPalmares(): List<PalmaresUi> {
        return palmaresDao.getAllPalmares().map { it.toUi() }
    }

    suspend fun getPalmaresFiltres(afficherLus: Boolean): List<PalmaresUi> {
        return palmaresDao.getPalmaresFiltres(afficherLus).map { it.toUi() }
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
}
