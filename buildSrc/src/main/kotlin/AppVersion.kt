/**
 * Version de l'APK (issue #161), dérivée du tag `vX.Y.Z` passé par release.yml
 * (`./gradlew assembleRelease -PversionName=X.Y.Z`), ou de la préversion `X.Y.Z+N`
 * passée par app-preview.yml (issue #173) : N commits depuis la version X.Y.Z.
 *
 * `versionCode = (major*10000 + minor*100 + patch)*1000 + N` (N = 0 pour une version) :
 * strictement croissant tant que minor et patch restent ≤ 99 et N ≤ 999, une version passe
 * toujours devant les préversions qui la précèdent, et le code est recalculable depuis le
 * nom d'une release (détection de nouvelle version, #162 : `appVersionCodeFromTag`).
 */
data class AppVersion(val name: String, val code: Int) {
    companion object {
        /** Limite Android du versionCode. */
        const val MAX_VERSION_CODE = 2_100_000_000

        /** Nombre maximal de commits d'une préversion `X.Y.Z+N`. */
        const val MAX_PREVIEW_COMMITS = 999

        private val TAG_RE = Regex("""^v?((\d+)\.(\d+)\.(\d+)(-[0-9A-Za-z.-]+)?(?:\+(\d+))?)$""")

        /**
         * `1.2.3`, `v1.2.3`, `v1.2.3-rc.1`, `1.2.3+4` → name sans le `v`,
         * code calculé sur X.Y.Z (et N pour une préversion).
         */
        fun fromTag(raw: String): AppVersion {
            val match = requireNotNull(TAG_RE.matchEntire(raw.trim())) {
                "Version « $raw » invalide : attendu X.Y.Z, vX.Y.Z (suffixe -xxx optionnel) ou X.Y.Z+N"
            }
            val (name, major, minor, patch, _, commits) = match.destructured
            val (ma, mi, pa) = listOf(major, minor, patch).map { it.toLong() }
            require(mi <= 99 && pa <= 99) {
                "Version « $raw » : minor et patch doivent rester ≤ 99 " +
                    "(versionCode = (major*10000 + minor*100 + patch)*1000 + N)"
            }
            val n = if (commits.isEmpty()) 0L else commits.toLong()
            require(n <= MAX_PREVIEW_COMMITS) {
                "Préversion « $raw » : N doit rester ≤ $MAX_PREVIEW_COMMITS, publier une version"
            }
            val code = (ma * 10_000 + mi * 100 + pa) * 1_000 + n
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
