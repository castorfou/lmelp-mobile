package com.lmelp.mobile.data.repository

import com.lmelp.mobile.data.db.OnKindleAvecConseilRow
import com.lmelp.mobile.data.db.OnKindleDao
import com.lmelp.mobile.data.model.OnKindleUi
import com.lmelp.mobile.viewmodel.TriMode
import java.text.Collator
import java.util.Locale

class OnKindleRepository(private val dao: OnKindleDao) {

    private val collator = Collator.getInstance(Locale.FRENCH).apply { strength = Collator.PRIMARY }

    suspend fun getOnKindle(
        afficherLus: Boolean,
        afficherNonLus: Boolean,
        triMode: TriMode
    ): List<OnKindleUi> {
        val rows = dao.getOnKindleAvecConseil(
            afficherLus = if (afficherLus) 1 else 0,
            afficherNonLus = if (afficherNonLus) 1 else 0
        )
        val sorted = when (triMode) {
            TriMode.ALPHA -> rows.sortedWith(
                Comparator { a, b -> collator.compare(a.titre, b.titre) }
            )
            TriMode.NOTE_MASQUE -> rows.sortedWith(
                compareBy<OnKindleAvecConseilRow> { if (it.noteMoyenne == null) 1 else 0 }
                    .thenByDescending { it.noteMoyenne ?: 0.0 }
                    .thenWith(collator) { it.titre }
            )
            TriMode.NOTE_CONSEIL -> rows.sortedWith(
                compareBy<OnKindleAvecConseilRow> { if (it.scoreHybride == null) 1 else 0 }
                    .thenByDescending { it.scoreHybride ?: 0.0 }
                    .thenWith(collator) { it.titre }
            )
        }
        return sorted.map { it.toUi() }
    }

    private fun <T> Comparator<T>.thenWith(collator: Collator, key: (T) -> String): Comparator<T> =
        thenComparator { a, b -> collator.compare(key(a), key(b)) }

    private fun OnKindleAvecConseilRow.toUi() = OnKindleUi(
        livreId = livreId,
        titre = titre,
        auteurNom = auteurNom,
        calibreLu = calibreLu == 1,
        calibreRating = calibreRating,
        noteMoyenne = noteMoyenne,
        nbAvis = nbAvis,
        discusseAuMasque = noteMoyenne != null,
        urlCover = urlCover,
        scoreHybride = scoreHybride
    )
}
