package com.lmelp.mobile.data.repository

import com.lmelp.mobile.data.db.AuteursDao
import com.lmelp.mobile.data.model.AuteurDetailUi
import com.lmelp.mobile.data.model.LivreParAuteurUi

class AuteursRepository(
    private val auteursDao: AuteursDao
) {

    suspend fun getAuteurDetail(auteurId: String): AuteurDetailUi? {
        val auteur = auteursDao.getAuteurById(auteurId) ?: return null
        val rows = auteursDao.getLivresParAuteur(auteurId)

        val livres = rows
            .sortedByDescending { it.derniereEmissionDate ?: "" }
            .map { row ->
                LivreParAuteurUi(
                    livreId = row.livreId,
                    titre = row.titre,
                    noteMoyenne = row.noteMoyenne,
                    derniereEmissionDate = row.derniereEmissionDate,
                    calibreInLibrary = row.calibreInLibrary == 1,
                    calibreLu = row.calibreLu == 1,
                    calibreRating = row.calibreRating
                )
            }

        return AuteurDetailUi(
            id = auteur.id,
            nom = auteur.nom,
            livres = livres
        )
    }
}
