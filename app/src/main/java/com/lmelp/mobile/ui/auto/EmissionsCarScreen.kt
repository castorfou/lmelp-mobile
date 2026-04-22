package com.lmelp.mobile.ui.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import com.lmelp.mobile.LmelpApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class EmissionsCarScreen(
    carContext: CarContext,
    private val app: LmelpApp
) : Screen(carContext) {

    private var items: List<CarListItem> = emptyList()
    private var isLoading = true

    init {
        CoroutineScope(Dispatchers.Main).launch {
            val emissions = app.emissionsRepository.getAllEmissions()
            items = CarScreenBuilder.buildEmissionsItems(emissions)
            isLoading = false
            invalidate()
        }
    }

    override fun onGetTemplate(): Template {
        if (isLoading) {
            return ListTemplate.Builder()
                .setTitle("Émissions")
                .setHeaderAction(Action.BACK)
                .setLoading(true)
                .build()
        }

        val listBuilder = ItemList.Builder()
        items.forEach { item ->
            listBuilder.addItem(
                Row.Builder()
                    .setTitle(item.title)
                    .addText(item.text)
                    .build()
            )
        }

        return ListTemplate.Builder()
            .setTitle("Émissions récentes")
            .setHeaderAction(Action.BACK)
            .setSingleList(listBuilder.build())
            .build()
    }
}
