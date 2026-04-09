package com.lmelp.mobile.data.repository

import com.lmelp.mobile.data.db.AuteursDao
import com.lmelp.mobile.data.db.CalibreHorsMasqueDao
import com.lmelp.mobile.data.model.AuteurDetailUi
import com.lmelp.mobile.data.model.CalibreHorsMasqueUi
import com.lmelp.mobile.data.model.LivreParAuteurUi

class AuteursRepository(
    private val auteursDao: AuteursDao,
    private val horsMasqueDao: CalibreHorsMasqueDao? = null
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

        val livresHorsMasque = horsMasqueDao
            ?.getByAuteurNom(auteur.nom)
            .orEmpty()
            .map { CalibreHorsMasqueUi(
                id = it.id,
                titre = it.titre,
                auteurNom = it.auteurNom,
                calibreRating = it.calibreRating,
                dateLecture = it.dateLecture
            )}

        return AuteurDetailUi(
            id = auteur.id,
            nom = auteur.nom,
            livres = livres,
            livresHorsMasque = livresHorsMasque
        )
    }
}
