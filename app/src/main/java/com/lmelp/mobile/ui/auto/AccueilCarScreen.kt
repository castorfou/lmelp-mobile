package com.lmelp.mobile.ui.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template
import com.lmelp.mobile.LmelpApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AccueilCarScreen(
    carContext: CarContext,
    private val app: LmelpApp
) : Screen(carContext) {

    private var body: String = "Chargement…"

    init {
        CoroutineScope(Dispatchers.Main).launch {
            val info = app.metadataRepository.getDbInfo()
            body = CarScreenBuilder.buildAccueilBody(info.nbEmissions, info.nbLivres)
            invalidate()
        }
    }

    override fun onGetTemplate(): Template =
        MessageTemplate.Builder(body)
            .setTitle("Accueil")
            .setHeaderAction(Action.BACK)
            .build()
}
