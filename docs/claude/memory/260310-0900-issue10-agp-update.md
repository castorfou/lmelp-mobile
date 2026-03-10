# Issue #10 — Mise à jour Android Gradle Plugin (8.5.2 → 8.7.3)

Date : 2026-03-10
Branche : `10-tech-agp-update`

## Changements

- `gradle/libs.versions.toml` : `agp = "8.5.2"` → `"8.7.3"`
- `gradle.properties` : suppression de `android.suppressUnsupportedCompileSdk=35` (workaround devenu inutile)

## Compatibilité vérifiée

| Composant | Version | Compatible AGP 8.7.x |
|-----------|---------|----------------------|
| Gradle wrapper | 8.9 | ✅ |
| Kotlin | 2.0.21 | ✅ |
| KSP | 2.0.21-1.0.27 | ✅ |
| compileSdk | 35 | ✅ (supporté nativement) |

## Résultat

Build sans aucun warning AGP. `android.suppressUnsupportedCompileSdk=35` supprimé proprement.
