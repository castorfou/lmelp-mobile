/**
 * Version de l'APK (issue #161), dérivée du tag `vX.Y.Z` passé par release.yml
 * (`./gradlew assembleRelease -PversionName=X.Y.Z`).
 *
 * `versionCode = major*10000 + minor*100 + patch` : strictement croissant d'une
 * version à la suivante tant que minor et patch restent ≤ 99, et recalculable
 * depuis le nom d'une release (utile pour détecter une nouvelle version, #162).
 */
data class AppVersion(val name: String, val code: Int) {
    companion object {
        /** Limite Android du versionCode. */
        const val MAX_VERSION_CODE = 2_100_000_000

        private val TAG_RE = Regex("""^v?((\d+)\.(\d+)\.(\d+)(-[0-9A-Za-z.-]+)?)$""")

        /** `1.2.3`, `v1.2.3`, `v1.2.3-rc.1` → name sans le `v`, code calculé sur X.Y.Z. */
        fun fromTag(raw: String): AppVersion {
            val match = requireNotNull(TAG_RE.matchEntire(raw.trim())) {
                "Version « $raw » invalide : attendu X.Y.Z ou vX.Y.Z (suffixe -xxx optionnel)"
            }
            val (name, major, minor, patch) = match.destructured
            val (ma, mi, pa) = listOf(major, minor, patch).map { it.toLong() }
            require(mi <= 99 && pa <= 99) {
                "Version « $raw » : minor et patch doivent rester ≤ 99 (versionCode = major*10000 + minor*100 + patch)"
            }
            val code = ma * 10_000 + mi * 100 + pa
            require(code <= MAX_VERSION_CODE) {
                "Version « $raw » : versionCode $code dépasse la limite Android $MAX_VERSION_CODE"
            }
            return AppVersion(name, code.toInt().coerceAtLeast(1))
        }

        /** Build local, non publié : reconnaissable à `-dev`. */
        fun dev(gitSha: String): AppVersion = AppVersion("0.0.0-dev+$gitSha", 1)

        /** Propriété Gradle `versionName` si fournie, sinon version de dev. */
        fun resolve(versionNameProperty: String?, gitSha: String): AppVersion =
            if (versionNameProperty.isNullOrBlank()) dev(gitSha) else fromTag(versionNameProperty)
    }
}
