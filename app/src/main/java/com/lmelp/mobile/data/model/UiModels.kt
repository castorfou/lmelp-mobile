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
    val section: String? = null,
    val urlCover: String? = null,
    val calibreInLibrary: Boolean = false,
    val calibreLu: Boolean = false,
    val calibreRating: Double? = null
)

data class AvisUi(
    val id: String,
    val critiqueId: String?,
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
    val urlCover: String?,
    val noteMoyenne: Double?,
    val avisParEmission: List<AvisParEmissionUi>,
    val calibreInLibrary: Boolean = false,
    val calibreLu: Boolean = false,
    val calibreRating: Double? = null,
    val dateLecture: String? = null,
    val joursLecture: Int? = null
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
    val calibreRating: Double? = null,
    val urlCover: String? = null,
    val dateLecture: String? = null
)

data class CritiqueUi(
    val id: String,
    val nom: String,
    val animateur: Boolean,
    val nbAvis: Int
)

data class AvisParCritiqueUi(
    val livreId: String,
    val livreTitre: String?,
    val auteurNom: String?,
    val note: Double?,
    val emissionDate: String?
)

data class CritiqueDetailUi(
    val id: String,
    val nom: String,
    val animateur: Boolean,
    val nbAvis: Int,
    val noteMoyenne: Double?,
    val distribution: Map<Int, Int>,
    val coupsDeCoeur: List<AvisParCritiqueUi>
)

data class RecommendationUi(
    val rank: Int,
    val livreId: String,
    val titre: String,
    val auteurNom: String?,
    val scoreHybride: Double,
    val masqueMean: Double?,
    val urlCover: String? = null
)

data class SearchResultUi(
    val type: String,
    val refId: String,
    val content: String,
    val urlCover: String? = null
)

data class LivreParAuteurUi(
    val livreId: String,
    val titre: String,
    val noteMoyenne: Double?,
    val derniereEmissionDate: String?,
    val calibreInLibrary: Boolean = false,
    val calibreLu: Boolean = false,
    val calibreRating: Double? = null
)

data class AuteurDetailUi(
    val id: String,
    val nom: String,
    val livres: List<LivreParAuteurUi>,
    val livresHorsMasque: List<CalibreHorsMasqueUi> = emptyList()
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
    val discusseAuMasque: Boolean = noteMoyenne != null,
    val urlCover: String? = null,
    val scoreHybride: Double? = null,
    val isPinned: Boolean = false
)

data class CalibreHorsMasqueUi(
    val id: String,
    val titre: String,
    val auteurNom: String?,
    val calibreRating: Double?,
    val dateLecture: String?
)

/**
 * Item unifié pour Mon Palmarès : peut être un livre du Masque (livreId != null, cliquable)
 * ou un livre hors Masque (livreId == null, non cliquable, sans couverture).
 */
data class MonPalmaresItemUi(
    val id: String,
    val titre: String,
    val auteurNom: String?,
    val calibreRating: Double?,
    val dateLecture: String?,
    val livreId: String? = null,
    val urlCover: String? = null,
    val joursLecture: Int? = null
)

data class DbInfoUi(
    val exportDate: String,
    val version: String,
    val nbEmissions: String,
    val nbLivres: String,
    val nbAvis: String
)
