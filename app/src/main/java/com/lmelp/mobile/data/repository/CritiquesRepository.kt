package com.lmelp.mobile.data.repository

import com.lmelp.mobile.data.db.CritiquesDao
import com.lmelp.mobile.data.model.AvisParCritiqueUi
import com.lmelp.mobile.data.model.CritiqueDetailUi
import com.lmelp.mobile.data.model.CritiqueUi

class CritiquesRepository(
    private val critiquesDao: CritiquesDao
) {

    suspend fun getAllCritiques(): List<CritiqueUi> {
        return critiquesDao.getAllCritiques().map {
            CritiqueUi(
                id = it.id,
                nom = it.nom,
                animateur = it.animateur == 1,
                nbAvis = it.nbAvis
            )
        }
    }

    suspend fun getCritiqueDetail(critiqueId: String): CritiqueDetailUi? {
        val critique = critiquesDao.getCritiqueById(critiqueId) ?: return null
        val avisRows = critiquesDao.getAvisByCritique(critiqueId)

        val avisAvecNote = avisRows.filter { it.note != null }
        val noteMoyenne = if (avisAvecNote.isEmpty()) null
        else avisAvecNote.sumOf { it.note!! } / avisAvecNote.size

        val distribution = avisAvecNote
            .groupBy { it.note!!.toInt() }
            .mapValues { it.value.size }

        val coupsDeCoeur = avisRows
            .filter { it.note != null && it.note >= 9.0 }
            .sortedByDescending { it.note }
            .map {
                AvisParCritiqueUi(
                    livreId = it.livreId,
                    livreTitre = it.livreTitre,
                    auteurNom = it.auteurNom,
                    note = it.note,
                    emissionDate = it.emissionDate
                )
            }

        return CritiqueDetailUi(
            id = critique.id,
            nom = critique.nom,
            animateur = critique.animateur == 1,
            nbAvis = critique.nbAvis,
            noteMoyenne = noteMoyenne,
            distribution = distribution,
            coupsDeCoeur = coupsDeCoeur
        )
    }
}
