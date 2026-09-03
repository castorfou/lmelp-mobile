package com.lmelp.mobile.data.remote

import org.json.JSONException
import org.json.JSONObject

/**
 * Miroir Kotlin de metadata.json publié sur la GitHub Release data-v{N}
 * (voir scripts/generate_data_release_metadata.py, issue #118).
 */
data class RemoteMetadata(
    val schemaVersion: Int,
    val exportDate: String,
    val exportDatetime: String,
    val exportVersion: String,
    val nbEmissions: Int,
    val nbLivres: Int,
    val nbAvis: Int,
    val fileSizeBytes: Long,
    val sha256: String,
    val filename: String
) {
    companion object {
        fun parse(json: String): RemoteMetadata {
            try {
                val obj = JSONObject(json)
                return RemoteMetadata(
                    schemaVersion = obj.getInt("schema_version"),
                    exportDate = obj.getString("export_date"),
                    exportDatetime = obj.getString("export_datetime"),
                    exportVersion = obj.getString("export_version"),
                    nbEmissions = obj.getInt("nb_emissions"),
                    nbLivres = obj.getInt("nb_livres"),
                    nbAvis = obj.getInt("nb_avis"),
                    fileSizeBytes = obj.getLong("file_size_bytes"),
                    sha256 = obj.getString("sha256"),
                    filename = obj.getString("filename")
                )
            } catch (e: JSONException) {
                throw IllegalArgumentException("metadata.json invalide : ${e.message}", e)
            }
        }
    }
}
