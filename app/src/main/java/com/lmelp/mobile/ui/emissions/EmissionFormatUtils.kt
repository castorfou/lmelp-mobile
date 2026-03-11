package com.lmelp.mobile.ui.emissions

private val MOIS_FR_LONG = listOf(
    "janvier", "février", "mars", "avril", "mai", "juin",
    "juillet", "août", "septembre", "octobre", "novembre", "décembre"
)

/**
 * Formate une date ISO (YYYY-MM-DD ou YYYY-MM-DDThh:mm:ssZ) en français long.
 * Ex: "2026-03-01T10:59:39Z" → "01 mars 2026"
 */
fun formatDateLong(dateIso: String): String {
    val parts = dateIso.take(10).split("-")
    val year = parts[0]
    val month = parts[1].toInt()
    val day = parts[2]
    return "$day ${MOIS_FR_LONG[month - 1]} $year"
}
