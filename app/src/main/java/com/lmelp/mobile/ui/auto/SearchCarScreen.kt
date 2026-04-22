package com.lmelp.mobile.ui.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.Row
import androidx.car.app.model.SearchTemplate
import androidx.car.app.model.Template
import com.lmelp.mobile.LmelpApp
import com.lmelp.mobile.data.model.SearchResultUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class SearchCarScreen(
    carContext: CarContext,
    private val app: LmelpApp
) : Screen(carContext), SearchTemplate.SearchCallback {

    private var results: List<SearchResultUi> = emptyList()
    private var searchJob: Job? = null

    override fun onGetTemplate(): Template {
        val listBuilder = ItemList.Builder()
        results.forEach { result ->
            listBuilder.addItem(
                Row.Builder()
                    .setTitle(result.content)
                    .addText(result.type)
                    .build()
            )
        }

        return SearchTemplate.Builder(this)
            .setHeaderAction(Action.BACK)
            .setShowKeyboardByDefault(false)
            .setItemList(listBuilder.build())
            .build()
    }

    override fun onSearchTextChanged(searchText: String) {
        searchJob?.cancel()
        if (searchText.length < 2) {
            results = emptyList()
            invalidate()
            return
        }
        searchJob = CoroutineScope(Dispatchers.Main).launch {
            results = app.searchRepository.search(searchText)
            invalidate()
        }
    }

    override fun onSearchSubmitted(searchText: String) {
        onSearchTextChanged(searchText)
    }
}
