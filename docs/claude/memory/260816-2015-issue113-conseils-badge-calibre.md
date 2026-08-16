# Issue #113 — Badge Calibre sur la page Conseils (Recommandations)

## Contexte

La page Palmarès affichait déjà un petit rond (`✓` vert si lu, `◯` gris si présent mais non lu) pour indiquer qu'un livre est dans la bibliothèque Calibre. La page Conseils (recommandations) n'affichait rien de tel. L'utilisateur a demandé d'ajouter ce même indicateur sur Conseils.

## Découverte clé : composable déjà réutilisable, non utilisé partout

Un composable partagé `CalibreBadge` existait déjà dans `app/src/main/java/com/lmelp/mobile/ui/components/CommonComponents.kt:92-116`, utilisé par `EmissionDetailScreen`, `AuteurDetailScreen` et `LivreDetailScreen` (introduit lors de l'issue #84, voir `260409-1642-issue84-calibre-badge-fiche-auteur-emission.md`). Mais `PalmaresScreen.kt` ne l'utilisait pas : son `PalmaresCard` dupliquait le même rendu inline (lignes 207-224). Pour Conseils, on a réutilisé `CalibreBadge` directement plutôt que de dupliquer à nouveau le code — le refactor de `PalmaresCard` pour utiliser aussi `CalibreBadge` a été jugé hors scope de cette issue et laissé de côté.

## Contrainte découverte : la query SQL de Conseils filtre déjà les livres lus

`RecommendationsDao.getRecommandationsNonLuesAvecUrl()` (`app/src/main/java/com/lmelp/mobile/data/db/RecommendationsDao.kt:77-89`) exclut déjà les livres avec `calibre_in_library=1 ET calibre_lu=1`. Conséquence : un livre affiché dans Conseils ne peut être que "absent de Calibre" ou "présent mais non lu" — jamais "lu". Donc dans cet écran, le badge ne peut afficher que le rond gris `◯`, jamais le `✓` vert avec la note personnelle.

Décision validée avec l'utilisateur : ne pas ajouter `calibre_rating` à la requête SQL (il serait toujours `null` en pratique vu le filtre existant) — on passe `calibreRating = null` en dur depuis le repository. Pas de changement du DAO nécessaire : les colonnes `calibre_in_library`/`calibre_lu` étaient déjà exposées dans `RecommandationNonLueAvecUrlRow` mais simplement non mappées jusqu'ici vers `RecommendationUi`.

## Modifications

- `app/src/main/java/com/lmelp/mobile/data/model/UiModels.kt` — `RecommendationUi` : ajout de `calibreInLibrary: Boolean = false`, `calibreLu: Boolean = false`, `calibreRating: Double? = null` (même pattern que `LivreUi`, `PalmaresUi`, `LivreParAuteurUi`).
- `app/src/main/java/com/lmelp/mobile/data/repository/RecommendationsRepository.kt` — `getAllRecommendations()` mappe désormais `calibreInLibrary = it.calibreInLibrary == 1`, `calibreLu = it.calibreLu == 1`, `calibreRating = null`.
- `app/src/main/java/com/lmelp/mobile/ui/recommendations/RecommendationsScreen.kt` — `RecommendationCard` appelle `CalibreBadge(...)` entre la colonne titre/auteur et le `NoteBadge` final, import ajouté (`com.lmelp.mobile.ui.components.CalibreBadge`).
- Nouveau fichier de test `app/src/test/java/com/lmelp/mobile/RecommendationsCalibreBadgeTest.kt`, calqué sur `CalibreBadgeRepositoryTest.kt` de l'issue #84 : mock du DAO avec `mockito-kotlin`, vérifie le mapping Int→Boolean et que `calibreRating` reste `null`.

## TDD

RED confirmé par erreur de compilation (`Unresolved reference 'calibreInLibrary'` etc.) avant modification de `RecommendationUi`. GREEN après les 3 modifications ci-dessus — `./gradlew :app:testDebugUnitTest` et `./gradlew lint` passent sans erreur. APK debug buildé (`./gradlew assembleDebug`) et testé manuellement par l'utilisateur avec succès ("j'ai deployé et ça marche parfaitement").

## À retenir pour les prochaines features Calibre

Avant d'ajouter le badge Calibre sur un nouvel écran, vérifier si le DAO/query expose déjà `calibre_in_library`/`calibre_lu`/`calibre_rating` — souvent déjà présent dans les jointures existantes mais pas encore mappé côté UI model, comme c'était le cas ici. Toujours réutiliser `CalibreBadge` (`ui/components/CommonComponents.kt`) plutôt que dupliquer son rendu.
