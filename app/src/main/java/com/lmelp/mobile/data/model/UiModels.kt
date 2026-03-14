package com.lmelp.mobile.data.model

data class EmissionUi(
    val id: String,
    val titre: String,
    val date: String,
    val duree: Int?,
    val nbAvis: Int,
    val hasSummary: Boolean
)

data class EmissionDetailUi(
    val id: String,
    val titre: String,
    val date: String,
    val description: String?,
    val duree: Int?,
    val animateurNom: String?,
    val livres: List<LivreUi>,
    val summary: String?
)

data class LivreUi(
    val id: String,
    val titre: String,
    val auteurNom: String?,
    val editeur: String?,
    val noteMoyenne: Double? = null,
    val section: String? = null
)

data class AvisUi(
    val id: String,
    val critiqueNom: String?,
    val note: Double?,
    val commentaire: String?,
    val emissionId: String
)

data class AvisParEmissionUi(
    val emissionId: String,
    val emissionTitre: String?,
    val emissionDate: String?,
    val avis: List<AvisUi>
)

data class LivreDetailUi(
    val id: String,
    val titre: String,
    val auteurId: String?,
    val auteurNom: String?,
    val editeur: String?,
    val urlBabelio: String?,
    val noteMoyenne: Double?,
    val avisParEmission: List<AvisParEmissionUi>
)

data class PalmaresUi(
    val rank: Int,
    val livreId: String,
    val titre: String,
    val auteurNom: String?,
    val noteMoyenne: Double,
    val nbAvis: Int,
    val nbCritiques: Int,
    val calibreInLibrary: Boolean = false,
    val calibreLu: Boolean = false,
    val calibreRating: Double? = null
)

data class CritiqueUi(
    val id: String,
    val nom: String,
    val animateur: Boolean,
    val nbAvis: Int
)

data class RecommendationUi(
    val rank: Int,
    val livreId: String,
    val titre: String,
    val auteurNom: String?,
    val scoreHybride: Double,
    val masqueMean: Double?
)

data class SearchResultUi(
    val type: String,
    val refId: String,
    val content: String
)

data class LivreParAuteurUi(
    val livreId: String,
    val titre: String,
    val noteMoyenne: Double?,
    val derniereEmissionDate: String?
)

data class AuteurDetailUi(
    val id: String,
    val nom: String,
    val livres: List<LivreParAuteurUi>
)

data class DerniereEmissionUi(
    val titre: String,
    val date: String
)

data class SlideItem(
    val livreId: String,
    val titre: String,
    val sousTitre: String,
    val noteMoyenne: Double? = null,
    val date: String? = null,
    val urlBabelio: String?,
    val urlCouverture: String?
)

data class OnKindleUi(
    val livreId: String,
    val titre: String,
    val auteurNom: String?,
    val calibreLu: Boolean,
    val calibreRating: Double?,
    val noteMoyenne: Double?,
    val nbAvis: Int,
    val discusseAuMasque: Boolean = noteMoyenne != null
)

data class DbInfoUi(
    val exportDate: String,
    val version: String,
    val nbEmissions: String,
    val nbLivres: String,
    val nbAvis: String
)
