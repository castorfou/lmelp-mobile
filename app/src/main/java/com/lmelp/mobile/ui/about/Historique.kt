package com.lmelp.mobile.ui.about

import com.lmelp.mobile.data.update.appVersionCodeFromTag

/*
 * Historique de l'écran À propos regroupé par version de l'appli (issue #170).
 * Vocabulaire : CONTEXT.md (Historique, Version de l'appli, Contenu d'une version, Pas encore publié).
 */

/** Asset généré au build par la tâche `generate<Variant>Historique` (app/build.gradle.kts). */
const val HISTORIQUE_ASSET = "historique.txt"

/** Séparateur de champs de l'asset (U+001F, absent des sujets de commit). */
const val HISTORIQUE_FIELD_SEPARATOR = "\u001F"

/** Commit de l'historique ; [versionName] = version de l'appli taguée sur ce commit, s'il y en a une. */
data class HistoriqueCommit(
    val hash: String,
    val message: String,
    val date: String,
    val versionName: String? = null
)

/**
 * Contenu d'une version de l'appli, ou les commits « Pas encore publié » quand
 * [versionName] est null. [date] est celle du commit tagué.
 */
data class ContenuVersion(
    val versionName: String?,
    val date: String?,
    val installed: Boolean,
    val commits: List<HistoriqueCommit>
)

/**
 * Parse l'asset historique : une ligne par commit, `hash␟sujet␟date␟refs`, où refs est
 * le `%D` de git log restreint aux tags (ex. "tag: v1.1.0, tag: data-v9").
 */
fun parseHistorique(raw: String): List<HistoriqueCommit> =
    raw.lines()
        .filter { it.isNotBlank() }
        .mapNotNull { line ->
            val parts = line.split(HISTORIQUE_FIELD_SEPARATOR)
            if (parts.size < 3) return@mapNotNull null
            HistoriqueCommit(
                hash = parts[0],
                message = parts[1],
                date = parts[2],
                versionName = parts.getOrNull(3)?.let(::versionNameFromRefs)
            )
        }

/**
 * Regroupe l'historique (ordre topologique, du plus récent au plus ancien) par version :
 * chaque version contient son commit tagué et les commits en dessous, jusqu'au tag précédent.
 * Les commits au-dessus du dernier tag forment « Pas encore publié ».
 */
fun groupHistoriqueByVersion(
    commits: List<HistoriqueCommit>,
    installedVersionName: String
): List<ContenuVersion> {
    val contenus = mutableListOf<ContenuVersion>()
    for (commit in commits) {
        val current = contenus.lastOrNull()
        if (current == null || commit.versionName != null) {
            contenus += ContenuVersion(
                versionName = commit.versionName,
                date = commit.versionName?.let { commit.date },
                installed = commit.versionName == installedVersionName,
                commits = listOf(commit)
            )
        } else {
            contenus[contenus.lastIndex] = current.copy(commits = current.commits + commit)
        }
    }
    return contenus
}

/** En-tête : « Version X.Y.Z · jj/mm/aa » ou « Pas encore publié ». */
fun formatContenuVersionHeader(contenu: ContenuVersion): String =
    if (contenu.versionName == null) "Pas encore publié"
    else "Version ${contenu.versionName} · ${contenu.date}"

/**
 * Version de l'appli portée par les refs d'un commit. Même règle que la détection de
 * nouvelle version (#162, [appVersionCodeFromTag]) : seuls les tags stables `vX.Y.Z` comptent ;
 * la plus grande l'emporte s'il y en a plusieurs.
 */
private fun versionNameFromRefs(refs: String): String? =
    refs.split(",")
        .map { it.trim().removePrefix("tag: ") }
        .mapNotNull { tag -> appVersionCodeFromTag(tag)?.let { code -> tag to code } }
        .maxByOrNull { (_, code) -> code }
        ?.first
        ?.removePrefix("v")
