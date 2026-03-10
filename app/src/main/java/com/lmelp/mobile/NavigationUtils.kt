package com.lmelp.mobile

/**
 * Retourne true si les labels de la barre de navigation doivent être affichés.
 * En mode paysage, on masque les labels pour éviter qu'ils soient coupés (issue #14).
 */
fun shouldShowLabel(isLandscape: Boolean): Boolean = !isLandscape
