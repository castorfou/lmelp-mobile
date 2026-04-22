package com.lmelp.mobile.ui.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import com.lmelp.mobile.LmelpApp

class MainCarScreen(
    carContext: CarContext,
    private val app: LmelpApp
) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val menuItems = CarScreenBuilder.buildMainMenuItems()

        val listBuilder = ItemList.Builder()
        menuItems.forEach { item ->
            listBuilder.addItem(
                Row.Builder()
                    .setTitle(item.title)
                    .setOnClickListener { navigateTo(item.route) }
                    .build()
            )
        }

        return ListTemplate.Builder()
            .setSingleList(listBuilder.build())
            .setTitle("Le Masque et la Plume")
            .setHeaderAction(Action.APP_ICON)
            .build()
    }

    private fun navigateTo(route: String) {
        when (route) {
            "accueil" -> screenManager.push(AccueilCarScreen(carContext, app))
            "emissions" -> screenManager.push(EmissionsCarScreen(carContext, app))
            "palmares" -> screenManager.push(PalmaresCarScreen(carContext, app))
            "conseils" -> screenManager.push(RecommendationsCarScreen(carContext, app))
            "recherche" -> screenManager.push(SearchCarScreen(carContext, app))
        }
    }
}
