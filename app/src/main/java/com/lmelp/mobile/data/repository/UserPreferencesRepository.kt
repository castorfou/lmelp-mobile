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

        /** Livres dont l'auto-épinglage (progression Calibre/KOReader en cours) a été
         * explicitement refusé par l'utilisateur — voir issue #131. */
        val autoPinDismissed: Flow<Set<String>>
        suspend fun dismissAutoPin(livreId: String)
        suspend fun clearAutoPinDismissed(livreId: String)
    }

    private val SHOW_HORS_MASQUE = booleanPreferencesKey("show_hors_masque")
    private val PINNED_READING = stringSetPreferencesKey("pinned_reading")
    private val AUTO_PIN_DISMISSED = stringSetPreferencesKey("auto_pin_dismissed")

    val showHorsMasque: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[SHOW_HORS_MASQUE] ?: true }

    override val pinnedReading: Flow<Set<String>> = context.dataStore.data
        .map { prefs -> prefs[PINNED_READING] ?: emptySet() }

    override val autoPinDismissed: Flow<Set<String>> = context.dataStore.data
        .map { prefs -> prefs[AUTO_PIN_DISMISSED] ?: emptySet() }

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

    override suspend fun dismissAutoPin(livreId: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[AUTO_PIN_DISMISSED] ?: emptySet()
            prefs[AUTO_PIN_DISMISSED] = current + livreId
        }
    }

    override suspend fun clearAutoPinDismissed(livreId: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[AUTO_PIN_DISMISSED] ?: emptySet()
            prefs[AUTO_PIN_DISMISSED] = current - livreId
        }
    }
}
