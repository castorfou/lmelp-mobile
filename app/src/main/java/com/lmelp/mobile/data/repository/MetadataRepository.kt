package com.lmelp.mobile.data.repository

import com.lmelp.mobile.data.db.MetadataDao
import com.lmelp.mobile.data.model.DbInfoUi

class MetadataRepository(
    private val metadataDao: MetadataDao
) {

    suspend fun getDbInfo(): DbInfoUi {
        val all = metadataDao.getAllMetadata().associate { it.key to it.value }
        return DbInfoUi(
            exportDate = all["export_date"] ?: "—",
            version = all["version"] ?: "—",
            nbEmissions = all["nb_emissions"] ?: "—",
            nbLivres = all["nb_livres"] ?: "—",
            nbAvis = all["nb_avis"] ?: "—"
        )
    }
}
