package com.lmelp.mobile.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

class UserPreferencesRepository(private val context: Context) : UserPreferencesRepository.PinnedReadingStorage {

    /** Interface extraite pour permettre le test unitaire sans Context/DataStore. */
    interface PinnedReadingStorage {
        val pinnedReading: Flow<Set<String>>
        suspend fun togglePinnedReading(livreId: String)
        suspend fun removePinned(livreId: String)
    }

    private val SHOW_HORS_MASQUE = booleanPreferencesKey("show_hors_masque")
    private val PINNED_READING = stringSetPreferencesKey("pinned_reading")

    val showHorsMasque: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[SHOW_HORS_MASQUE] ?: true }

    override val pinnedReading: Flow<Set<String>> = context.dataStore.data
        .map { prefs -> prefs[PINNED_READING] ?: emptySet() }

    suspend fun setShowHorsMasque(show: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[SHOW_HORS_MASQUE] = show
        }
    }

    override suspend fun togglePinnedReading(livreId: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[PINNED_READING] ?: emptySet()
            prefs[PINNED_READING] = if (livreId in current) current - livreId else current + livreId
        }
    }

    override suspend fun removePinned(livreId: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[PINNED_READING] ?: emptySet()
            prefs[PINNED_READING] = current - livreId
        }
    }
}
