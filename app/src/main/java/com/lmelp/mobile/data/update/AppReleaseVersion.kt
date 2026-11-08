package com.lmelp.mobile.data.update

import com.lmelp.mobile.data.model.AppRelease

/**
 * Comparaison de versions de l'appli (issue #162).
 *
 * Même formule que `buildSrc/src/main/kotlin/AppVersion.kt` (issue #161), dupliquée ici
 * car buildSrc n'existe qu'au build : `versionCode = major*10000 + minor*100 + patch`.
 * Seuls les tags stables `vX.Y.Z` sont des releases d'appli : `v0.0.0-test` (pré-release
 * de test de release.yml) et `data-v{N}` (données) ne donnent pas de version.
 */
private val STABLE_APP_TAG = Regex("""^v(\d+)\.(\d+)\.(\d+)$""")

fun appVersionCodeFromTag(tag: String): Int? {
    val (major, minor, patch) = STABLE_APP_TAG.matchEntire(tag)?.destructured ?: return null
    val ma = major.toLongOrNull() ?: return null
    val mi = minor.toLongOrNull() ?: return null
    val pa = patch.toLongOrNull() ?: return null
    if (mi > 99 || pa > 99) return null
    val code = ma * 10_000 + mi * 100 + pa
    return if (code > Int.MAX_VALUE) null else code.toInt()
}

/** Build local non publié (`0.0.0-dev+<sha>`, versionCode 1) : jamais de check de version. */
fun isDevVersion(versionName: String): Boolean = "-dev" in versionName

/** Release d'appli stable la plus récente strictement au-dessus de la version installée, ou null. */
fun selectLatestAppRelease(releases: List<AppRelease>, installedVersionCode: Int): AppRelease? =
    releases
        .filter { !it.draft && !it.prerelease && it.apkUrl != null }
        .mapNotNull { release -> release.versionCode?.let { code -> release to code } }
        .filter { (_, code) -> code > installedVersionCode }
        .maxByOrNull { (_, code) -> code }
        ?.first
