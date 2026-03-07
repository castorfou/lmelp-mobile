package com.lmelp.mobile.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "episodes")
data class EpisodeEntity(
    @PrimaryKey val id: String,
    val titre: String,
    val date: String?,
    val description: String?,
    val url: String?,
    val duree: Int?,
    @ColumnInfo(defaultValue = "0") val masked: Int = 0
)

@Entity(
    tableName = "emissions",
    foreignKeys = [ForeignKey(
        entity = EpisodeEntity::class,
        parentColumns = ["id"],
        childColumns = ["episode_id"]
    )]
)
data class EmissionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "episode_id") val episodeId: String,
    val date: String,
    val duree: Int?,
    @ColumnInfo(name = "animateur_id") val animateurId: String?,
    @ColumnInfo(name = "nb_avis", defaultValue = "0") val nbAvis: Int = 0,
    @ColumnInfo(name = "has_summary", defaultValue = "0") val hasSummary: Int = 0,
    @ColumnInfo(name = "created_at") val createdAt: String?,
    @ColumnInfo(name = "updated_at") val updatedAt: String?
)

@Entity(tableName = "auteurs")
data class AuteurEntity(
    @PrimaryKey val id: String,
    val nom: String,
    @ColumnInfo(name = "url_babelio") val urlBabelio: String?
)

@Entity(
    tableName = "livres",
    foreignKeys = [ForeignKey(
        entity = AuteurEntity::class,
        parentColumns = ["id"],
        childColumns = ["auteur_id"]
    )]
)
data class LivreEntity(
    @PrimaryKey val id: String,
    val titre: String,
    @ColumnInfo(name = "auteur_id") val auteurId: String?,
    @ColumnInfo(name = "auteur_nom") val auteurNom: String?,
    val editeur: String?,
    @ColumnInfo(name = "url_babelio") val urlBabelio: String?,
    @ColumnInfo(name = "created_at") val createdAt: String?,
    @ColumnInfo(name = "updated_at") val updatedAt: String?
)

@Entity(tableName = "critiques")
data class CritiqueEntity(
    @PrimaryKey val id: String,
    val nom: String,
    @ColumnInfo(defaultValue = "0") val animateur: Int = 0,
    @ColumnInfo(name = "nb_avis", defaultValue = "0") val nbAvis: Int = 0
)

@Entity(
    tableName = "avis",
    foreignKeys = [
        ForeignKey(entity = EmissionEntity::class, parentColumns = ["id"], childColumns = ["emission_id"]),
        ForeignKey(entity = LivreEntity::class, parentColumns = ["id"], childColumns = ["livre_id"]),
        ForeignKey(entity = CritiqueEntity::class, parentColumns = ["id"], childColumns = ["critique_id"])
    ]
)
data class AvisEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "emission_id") val emissionId: String,
    @ColumnInfo(name = "livre_id") val livreId: String,
    @ColumnInfo(name = "critique_id") val critiqueId: String,
    val note: Double?,
    val commentaire: String?,
    @ColumnInfo(name = "livre_titre") val livreTitre: String?,
    @ColumnInfo(name = "auteur_nom") val auteurNom: String?,
    @ColumnInfo(name = "critique_nom") val critiqueNom: String?,
    @ColumnInfo(name = "match_phase") val matchPhase: Int?,
    @ColumnInfo(name = "created_at") val createdAt: String?
)

@Entity(tableName = "palmares")
data class PalmaresEntity(
    val rank: Int,
    @PrimaryKey @ColumnInfo(name = "livre_id") val livreId: String,
    val titre: String,
    @ColumnInfo(name = "auteur_nom") val auteurNom: String?,
    @ColumnInfo(name = "note_moyenne") val noteMoyenne: Double,
    @ColumnInfo(name = "nb_avis") val nbAvis: Int,
    @ColumnInfo(name = "nb_critiques") val nbCritiques: Int,
    @ColumnInfo(name = "calibre_in_library", defaultValue = "0") val calibreInLibrary: Int = 0,
    @ColumnInfo(name = "calibre_lu", defaultValue = "0") val calibreLu: Int = 0,
    @ColumnInfo(name = "calibre_rating") val calibreRating: Double? = null
)

@Entity(tableName = "recommendations")
data class RecommendationEntity(
    val rank: Int,
    @PrimaryKey @ColumnInfo(name = "livre_id") val livreId: String,
    val titre: String,
    @ColumnInfo(name = "auteur_nom") val auteurNom: String?,
    @ColumnInfo(name = "score_hybride") val scoreHybride: Double,
    @ColumnInfo(name = "svd_predict") val svdPredict: Double?,
    @ColumnInfo(name = "masque_mean") val masqueMean: Double?,
    @ColumnInfo(name = "masque_count") val masqueCount: Int?
)

@Entity(tableName = "avis_critiques")
data class AvisCritiquesEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "emission_id") val emissionId: String?,
    @ColumnInfo(name = "episode_title") val episodeTitle: String?,
    @ColumnInfo(name = "episode_date") val episodeDate: String?,
    val summary: String?,
    val animateur: String?,
    @ColumnInfo(name = "critiques_json") val critiquesJson: String?
)

@Entity(
    tableName = "emission_livres",
    primaryKeys = ["emission_id", "livre_id"],
    foreignKeys = [
        ForeignKey(entity = EmissionEntity::class, parentColumns = ["id"], childColumns = ["emission_id"]),
        ForeignKey(entity = LivreEntity::class, parentColumns = ["id"], childColumns = ["livre_id"])
    ],
    indices = [Index("livre_id")]
)
data class EmissionLivreEntity(
    @ColumnInfo(name = "emission_id") val emissionId: String,
    @ColumnInfo(name = "livre_id") val livreId: String
)

@Entity(tableName = "db_metadata")
data class DbMetadataEntity(
    @PrimaryKey val key: String,
    val value: String
)
