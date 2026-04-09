package com.lmelp.mobile.data.repository

import com.lmelp.mobile.data.db.LivresDao
import com.lmelp.mobile.data.model.AvisParEmissionUi
import com.lmelp.mobile.data.model.AvisUi
import com.lmelp.mobile.data.model.LivreDetailUi

class LivresRepository(
    private val livresDao: LivresDao
) {

    suspend fun getLivreDetail(livreId: String): LivreDetailUi? {
        val livre = livresDao.getLivreAvecCalibreById(livreId) ?: return null
        val rows = livresDao.getAvisAvecEmissionByLivre(livreId)

        val noteMoyenne = rows.mapNotNull { it.avis.note }
            .takeIf { it.isNotEmpty() }
            ?.average()

        // Grouper par émission en conservant l'ordre (date DESC)
        val avisParEmission = rows
            .groupBy { it.avis.emissionId }
            .entries
            .sortedByDescending { (_, groupRows) -> groupRows.first().emissionDate }
            .map { (emissionId, groupRows) ->
                val first = groupRows.first()
                AvisParEmissionUi(
                    emissionId = emissionId,
                    emissionTitre = first.emissionTitre,
                    emissionDate = first.emissionDate,
                    avis = groupRows
                        .sortedBy { it.avis.critiqueNom ?: "" }
                        .map { row ->
                            AvisUi(
                                id = row.avis.id,
                                critiqueNom = row.avis.critiqueNom,
                                note = row.avis.note,
                                commentaire = row.avis.commentaire,
                                emissionId = row.avis.emissionId
                            )
                        }
                )
            }

        return LivreDetailUi(
            id = livre.id,
            titre = livre.titre,
            auteurId = livre.auteurId,
            auteurNom = livre.auteurNom,
            editeur = livre.editeur,
            urlBabelio = livre.urlBabelio,
            urlCover = livre.urlCover,
            noteMoyenne = noteMoyenne,
            avisParEmission = avisParEmission,
            calibreInLibrary = livre.calibreInLibrary == 1,
            calibreLu = livre.calibreLu == 1,
            calibreRating = livre.calibreRating
        )
    }
}
