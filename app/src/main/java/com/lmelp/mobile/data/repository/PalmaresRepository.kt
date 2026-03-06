package com.lmelp.mobile.data.repository

import com.lmelp.mobile.data.db.PalmaresDao
import com.lmelp.mobile.data.model.PalmaresUi

class PalmaresRepository(
    private val palmaresDao: PalmaresDao
) {

    suspend fun getAllPalmares(): List<PalmaresUi> {
        return palmaresDao.getAllPalmares().map {
            PalmaresUi(
                rank = it.rank,
                livreId = it.livreId,
                titre = it.titre,
                auteurNom = it.auteurNom,
                noteMoyenne = it.noteMoyenne,
                nbAvis = it.nbAvis,
                nbCritiques = it.nbCritiques
            )
        }
    }
}
