package com.lmelp.mobile.data.repository

import com.lmelp.mobile.data.db.AvisCritiquesDao
import com.lmelp.mobile.data.db.EmissionsDao
import com.lmelp.mobile.data.db.EpisodesDao
import com.lmelp.mobile.data.db.LivresDao
import com.lmelp.mobile.data.model.AvisUi
import com.lmelp.mobile.data.model.EmissionDetailUi
import com.lmelp.mobile.data.model.EmissionUi
import com.lmelp.mobile.data.model.LivreUi

class EmissionsRepository(
    private val emissionsDao: EmissionsDao,
    private val episodesDao: EpisodesDao,
    private val livresDao: LivresDao,
    private val avisCritiquesDao: AvisCritiquesDao
) {

    suspend fun getAllEmissions(): List<EmissionUi> {
        val emissions = emissionsDao.getAllEmissions()
        return emissions.map { emission ->
            val episode = episodesDao.getEpisodeById(emission.episodeId)
            EmissionUi(
                id = emission.id,
                titre = episode?.titre ?: emission.id,
                date = emission.date,
                duree = emission.duree,
                nbAvis = emission.nbAvis,
                hasSummary = emission.hasSummary == 1
            )
        }
    }

    suspend fun getEmissionDetail(emissionId: String): EmissionDetailUi? {
        val emission = emissionsDao.getEmissionById(emissionId) ?: return null
        val episode = episodesDao.getEpisodeById(emission.episodeId)
        val notesMap = livresDao.getNotesParLivreForEmission(emissionId)
            .associate { it.livreId to Pair(it.avgNote, it.section) }
        val livres = livresDao.getLivresByEmission(emissionId).map {
            val (note, section) = notesMap[it.id] ?: Pair(null, null)
            LivreUi(id = it.id, titre = it.titre, auteurNom = it.auteurNom,
                editeur = it.editeur, noteMoyenne = note, section = section,
                urlCover = it.urlCover)
        }
        val avisCritiques = avisCritiquesDao.getByEmissionId(emissionId)
        return EmissionDetailUi(
            id = emission.id,
            titre = episode?.titre ?: emission.id,
            date = emission.date,
            description = episode?.description,
            duree = emission.duree,
            animateurNom = null,
            livres = livres,
            summary = avisCritiques?.summary
        )
    }
}
