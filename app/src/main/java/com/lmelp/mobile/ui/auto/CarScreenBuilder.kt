package com.lmelp.mobile.ui.auto

import com.lmelp.mobile.data.model.EmissionUi
import com.lmelp.mobile.data.model.PalmaresUi
import com.lmelp.mobile.data.model.RecommendationUi

private const val MAX_ITEMS = 6

data class CarListItem(
    val title: String,
    val text: String,
    val id: String = ""
)

data class CarMenuItem(
    val title: String,
    val route: String
)

object CarScreenBuilder {

    fun buildMainMenuItems(): List<CarMenuItem> = listOf(
        CarMenuItem("Accueil", "accueil"),
        CarMenuItem("Émissions", "emissions"),
        CarMenuItem("Palmarès", "palmares"),
        CarMenuItem("Conseils", "conseils"),
        CarMenuItem("Recherche", "recherche")
    )

    fun buildEmissionsItems(emissions: List<EmissionUi>): List<CarListItem> =
        emissions.take(MAX_ITEMS).map { e ->
            CarListItem(
                title = e.titre,
                text = e.date,
                id = e.id
            )
        }

    fun buildPalmaresItems(palmares: List<PalmaresUi>): List<CarListItem> =
        palmares.take(MAX_ITEMS).map { p ->
            val note = "%.1f/10".format(p.noteMoyenne)
            CarListItem(
                title = p.titre,
                text = "${p.auteurNom ?: ""} — $note",
                id = p.livreId
            )
        }

    fun buildRecommendationsItems(recos: List<RecommendationUi>): List<CarListItem> =
        recos.take(MAX_ITEMS).map { r ->
            CarListItem(
                title = r.titre,
                text = r.auteurNom ?: "",
                id = r.livreId
            )
        }

    fun buildAccueilBody(nbEmissions: String, nbLivres: String): String =
        "Le Masque et la Plume\n$nbEmissions émissions · $nbLivres livres"
}
