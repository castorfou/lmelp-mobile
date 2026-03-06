package com.lmelp.mobile.data.repository

import com.lmelp.mobile.data.db.LivresDao
import com.lmelp.mobile.data.model.AvisUi
import com.lmelp.mobile.data.model.LivreDetailUi

class LivresRepository(
    private val livresDao: LivresDao
) {

    suspend fun getLivreDetail(livreId: String): LivreDetailUi? {
        val livre = livresDao.getLivreById(livreId) ?: return null
        val avis = livresDao.getAvisByLivre(livreId).map {
            AvisUi(
                id = it.id,
                critiqueNom = it.critiqueNom,
                note = it.note,
                commentaire = it.commentaire,
                emissionId = it.emissionId
            )
        }
        return LivreDetailUi(
            id = livre.id,
            titre = livre.titre,
            auteurNom = livre.auteurNom,
            editeur = livre.editeur,
            urlBabelio = livre.urlBabelio,
            avis = avis
        )
    }
}
