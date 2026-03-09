# Issue #25 — Changelog des commits dans l'écran About

Date : 2026-03-09
Branche : `25-about-ajouter-les-resumes-des-commits`

## Comportement

L'écran About affiche désormais la liste des 30 derniers commits (hors merges), triés par date décroissante, sous un titre "Historique".

Chaque ligne : hash court (coloré en primary), message, date au format `dd/MM/yy`.

## Architecture — Changelog embarqué au build time

### `app/build.gradle.kts`

Nouveau `val gitChangelog` généré au build via `ProcessBuilder` :

```kotlin
val gitChangelog: String by lazy {
    try {
        val process = ProcessBuilder(
            "git", "log",
            "--pretty=format:%h|%s|%ad",
            "--date=format:%d/%m/%y",
            "--no-merges", "-30"
        ).directory(rootDir).redirectErrorStream(true).start()
        process.inputStream.bufferedReader().readText().trim()
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")    // sauts de ligne → \n littéral pour BuildConfig Java
            .replace("\r", "")
    } catch (e: Exception) { "" }
}
// Dans defaultConfig :
buildConfigField("String", "CHANGELOG", "\"$gitChangelog\"")
```

**Point critique** : les `\n` dans le fichier Java source (`BuildConfig.java`) sont des séquences d'échappement Java → à l'exécution, la JVM les convertit en vrais sauts de ligne. Donc `parseChangelog` doit splitter sur `"\n"` (vrai newline), PAS sur `"\\n"` (backslash-n littéral).

### `app/src/main/java/com/lmelp/mobile/ui/about/AboutScreen.kt`

- `data class ChangelogEntry(val hash, val message, val date)`
- `fun parseChangelog(raw: String): List<ChangelogEntry>` — split sur `"\n"`, puis `"|"`, ignore lignes invalides
- `AboutScreen` passe `BuildConfig.CHANGELOG` à `AboutContent`
- `AboutContent` : `LazyColumn` avec header (titre + version), section "Historique", `ChangelogRow` + `HorizontalDivider`
- `ChangelogRow` : `Row` avec hash (labelSmall, coloré), message (bodySmall, weight=1f), date (labelSmall, gris)

### `app/src/test/java/com/lmelp/mobile/ChangelogParserTest.kt`

8 tests : chaîne vide, espaces, ligne valide, plusieurs lignes, ligne vide ignorée, ligne sans `|` ignorée, pipe dans le message, ordre préservé.

## Piège résolu

**Erreur** : utiliser `raw.split("\\n")` (littéral backslash-n) au lieu de `raw.split("\n")` (vrai newline).

Le `build.gradle.kts` remplace les vrais `\n` par `\\n` pour que le fichier Java source soit syntaxiquement correct (une string Java ne peut pas contenir de vrai newline). Mais à l'exécution, la JVM interprète `\n` en vrai newline → il faut splitter sur `"\n"`.

## Fichiers modifiés

- `app/build.gradle.kts` — ajout `gitChangelog` + `buildConfigField CHANGELOG`
- `app/src/main/java/com/lmelp/mobile/ui/about/AboutScreen.kt` — réécriture avec changelog
- `app/src/test/java/com/lmelp/mobile/ChangelogParserTest.kt` — nouveau, 8 tests
