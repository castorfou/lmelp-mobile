package com.lmelp.mobile.data.repository

import com.lmelp.mobile.data.db.RecommendationsDao
import com.lmelp.mobile.data.model.RecommendationUi

class RecommendationsRepository(
    private val recommendationsDao: RecommendationsDao
) {

    suspend fun getAllRecommendations(): List<RecommendationUi> {
        return recommendationsDao.getAllRecommendations().map {
            RecommendationUi(
                rank = it.rank,
                livreId = it.livreId,
                titre = it.titre,
                auteurNom = it.auteurNom,
                scoreHybride = it.scoreHybride,
                masqueMean = it.masqueMean
            )
        }
    }
}
