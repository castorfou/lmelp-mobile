package com.lmelp.mobile.data.repository

import com.lmelp.mobile.data.db.CritiquesDao
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
}
