package com.lmelp.mobile.ui.auto

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.Session

class LmelpSession : Session() {

    override fun onCreateScreen(intent: Intent): Screen {
        val app = carContext.applicationContext as com.lmelp.mobile.LmelpApp
        return MainCarScreen(carContext, app)
    }
}
