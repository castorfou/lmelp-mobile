package com.lmelp.mobile.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

class UserPreferencesRepository(private val context: Context) {

    private val SHOW_HORS_MASQUE = booleanPreferencesKey("show_hors_masque")

    val showHorsMasque: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[SHOW_HORS_MASQUE] ?: true }

    suspend fun setShowHorsMasque(show: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[SHOW_HORS_MASQUE] = show
        }
    }
}
