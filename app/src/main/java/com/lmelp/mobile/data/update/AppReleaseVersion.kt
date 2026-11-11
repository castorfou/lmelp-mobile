package com.lmelp.mobile.data.update

import com.lmelp.mobile.data.model.AppRelease

/**
 * Comparaison de versions de l'appli (issues #162 et #173).
 *
 * Même formule que `buildSrc/src/main/kotlin/AppVersion.kt`, dupliquée ici car buildSrc
 * n'existe qu'au build : `versionCode = (major*10000 + minor*100 + patch)*1000 + N`,
 * N = 0 pour une version, N commits depuis la version X.Y.Z pour une préversion `X.Y.Z+N`.
 * Seuls les tags stables `vX.Y.Z` sont des versions : `v0.0.0-test` (pré-release de test
 * de release.yml) et `data-v{N}` (données) n'en donnent pas. La préversion est publiée sur
 * la pré-release tournante [APP_PREVIEW_TAG], dont le nom porte `X.Y.Z+N`.
 */
private val STABLE_APP_TAG = Regex("""^v(\d+)\.(\d+)\.(\d+)$""")
private val PREVIEW_NAME = Regex("""^(\d+)\.(\d+)\.(\d+)\+(\d+)$""")

/** Préversion installée : versionName `X.Y.Z+N`, suffixe `-debug` toléré. */
private val INSTALLED_PREVIEW = Regex("""^\d+\.\d+\.\d+\+\d+(-debug)?$""")

/** Tag fixe de la pré-release tournante qui porte la dernière préversion (issue #173). */
const val APP_PREVIEW_TAG = "app-preview"

private const val MAX_PREVIEW_COMMITS = 999

private fun versionCode(major: String, minor: String, patch: String, commits: Long): Int? {
    val ma = major.toLongOrNull() ?: return null
    val mi = minor.toLongOrNull() ?: return null
    val pa = patch.toLongOrNull() ?: return null
    if (mi > 99 || pa > 99 || commits > MAX_PREVIEW_COMMITS) return null
    val code = (ma * 10_000 + mi * 100 + pa) * 1_000 + commits
    return if (code > Int.MAX_VALUE) null else code.toInt()
}

/** versionCode d'une version `vX.Y.Z`, null pour tout autre tag. */
fun appVersionCodeFromTag(tag: String): Int? {
    val (major, minor, patch) = STABLE_APP_TAG.matchEntire(tag)?.destructured ?: return null
    return versionCode(major, minor, patch, commits = 0)
}

/** versionCode d'une préversion d'après le nom de sa release (`X.Y.Z+N`), null sinon. */
fun appVersionCodeFromPreviewName(name: String): Int? {
    val (major, minor, patch, commits) = PREVIEW_NAME.matchEntire(name)?.destructured ?: return null
    return versionCode(major, minor, patch, commits.toLongOrNull() ?: return null)
}

/** Build local non publié (`0.0.0-dev+<sha>`, versionCode 1) : jamais de check de version. */
fun isDevVersion(versionName: String): Boolean = "-dev" in versionName

/** Préversion installée (`X.Y.Z+N`) : elle vérifie les mises à jour comme une version. */
fun isPreviewVersion(versionName: String): Boolean = INSTALLED_PREVIEW.matches(versionName)

/**
 * Release d'appli la plus récente strictement au-dessus de la version installée, ou null :
 * version `vX.Y.Z` ou préversion [APP_PREVIEW_TAG]. Les autres pré-releases sont ignorées.
 */
fun selectLatestAppRelease(releases: List<AppRelease>, installedVersionCode: Int): AppRelease? =
    releases
        .filter { !it.draft && (!it.prerelease || it.isPreview) && it.apkUrl != null }
        .mapNotNull { release -> release.versionCode?.let { code -> release to code } }
        .filter { (_, code) -> code > installedVersionCode }
        .maxByOrNull { (_, code) -> code }
        ?.first
