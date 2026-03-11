package com.lmelp.mobile.ui.emissions

import kotlin.math.roundToInt

/**
 * Convertit une fraction de drag (0..1) en index de mois dans monthIndices.
 *
 * Utilise (fraction * (totalMonths - 1)).roundToInt() pour que :
 *   - fraction=0 → premier mois (index 0)
 *   - fraction=1 → dernier mois (index totalMonths-1)
 *   - fraction=0.5 → mois central
 *
 * Aligné avec le calcul de position du pouce (thumbFraction = cur / (size-1)).
 * Issue #35 : correction de la désynchronisation date ↔ position de l'ascenseur.
 */
fun dragFractionToMonthIndex(fraction: Float, totalMonths: Int): Int {
    if (totalMonths <= 1) return 0
    return (fraction * (totalMonths - 1)).roundToInt()
        .coerceIn(0, totalMonths - 1)
}
