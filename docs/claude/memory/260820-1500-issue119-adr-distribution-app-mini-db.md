# Issue #119 — ADR 0002 : distribution de l'app et mini base de données embarquée

## Contexte

Issue de suivi de #116/ADR 0001, initialement scopée sur la faisabilité de trois pistes de distribution APK (Google Play Store, F-Droid, ADB sans fil) suite à la migration `docker-lmelp` vers un NAS. Le scope a été reformulé en cours d'analyse : l'app se met à jour rarement (contrairement aux données, ~1x/semaine), et le laptop reste disponible en USB au moment du déploiement — donc pas de besoin réel de distribution automatisée à court terme.

## Décisions actées (ADR 0002)

Documentées dans `docs/dev/adr/0002-distribution-app-mini-db-embarquee.md` :

1. **Distribution app : USB reste la solution retenue.** Aucune des 3 pistes explorées n'est adoptée immédiatement :
   - **Google Play Store** : direction cible à long terme si multi-utilisateurs, mais bloqué par la question **non tranchée** des droits sur le contenu diffusé (métadonnées Le Masque et la Plume/France Inter). Coût 25$, vérification d'identité obligatoire dès septembre 2026 pour les comptes personnels, test fermé 14 jours/12 testeurs avant publication.
   - **F-Droid** : alternative gratuite viable en théorie (pas de blocage sur les droits pour la soumission elle-même), mais exige des dépendances 100% FOSS — non audité sur ce projet. Prématuré tant que l'app reste à usage personnel.
   - **ADB sans fil** : confirmé fragile (pairing ne survit pas à un reboot, port TCP dynamique) — reste conclusion de l'ADR 0001 : dev-only, jamais un mécanisme de distribution grand public.

2. **Mini base de données embarquée** (conséquence non anticipée de l'ADR 0001) : remplacer la `lmelp.db` complète (4.4 Mo) committée dans `app/src/main/assets/` par un extrait réel minimal (3 émissions réelles, les plus anciennes). Objectif : rendre visible immédiatement toute régression du mécanisme de téléchargement au lancement (issue #118) — une base complète mais périmée masquerait silencieusement une panne du téléchargement puisque l'app resterait fonctionnelle avec d'anciennes données. **Implémentée dans cette même session** (pas d'issue de suivi séparée, contrairement à ce qui avait été annoncé initialement — l'utilisateur a demandé de tout traiter dans #119).

3. **Contenu diffusé — état des lieux, non tranché** : l'app n'expose que des métadonnées dérivées (titres/dates/descriptions, notes/avis, liens RadioFrance, couvertures Babelio/Amazon), pas d'audio ni de texte intégral protégé. Ça s'apparente à un usage raisonnable mais reste un jugement, pas un fait juridique établi — la clarification des droits reste un prérequis bloquant pour Play Store, à la charge de l'auteur, hors scope technique.

## Implémentation de la mini-DB (TDD)

**`scripts/build_mini_db.py`** (nouveau) : dérive une mini-DB depuis une `lmelp.db` complète déjà exportée (pas de connexion MongoDB). Garde les N émissions les plus anciennes + tables liées par filtrage FK (`episodes`, `avis`, `emission_livres`, `avis_critiques`, `livres`, `auteurs`), `critiques` conservée entière (25 lignes). Réutilise `compute_palmares`/`build_search_index`/`update_critique_stats` du script d'export existant. `recommendations`/`onkindle`/`calibre_hors_masque` laissées vides (indépendantes, sans sens sur un sous-ensemble).

**Bug découvert en test device (important)** : la première version réutilisait `export_mongo_to_sqlite.write_metadata()`, qui écrit `db_metadata.version = int(time.time())` — le moment de génération du fichier. Résultat observé sur device réel : l'écran À propos affichait "Base à jour" alors que la mini-DB n'avait que 3 émissions, parce que `DataUpdateRepository.checkForUpdate()` (`app/src/main/java/com/lmelp/mobile/data/repository/DataUpdateRepository.kt:38-53`) compare `db_metadata.version` (timestamp local) à `RemoteMetadata.exportVersion` (timestamp distant) — et une mini-DB régénérée aujourd'hui a un timestamp plus récent que n'importe quelle release déjà publiée. **Fix** : `_write_mini_db_metadata()` dans `build_mini_db.py` date `version` sur `MAX(date)` des émissions réellement gardées (donc une date ancienne, ex. 2015-08-30 pour les 3 plus anciennes émissions), garantissant que la mini-DB paraît toujours périmée face à une vraie release. Testé en conditions réelles sur device (Pixel 9 Pro) après ce fix : le bouton "Vérifier les mises à jour" détecte correctement une mise à jour disponible.

**Tests** : `tests/test_build_mini_db.py` (15 tests, TDD complet y compris le bug de version ci-dessus). `tests/test_lmelp_db_integrity.py` : `TestFraicheurDB`/`TestDonneesCalibr` supprimées (n'ont plus de sens pour un asset volontairement minimal/périmé — l'hypothèse "asset committé = DB complète et à jour" ne tient plus depuis l'ADR 0001), remplacées par `TestTailleMinimale` (garde-fou : nb_emissions ≤ seuil, recommendations vide). `tests/test_fts5_accent_search.py` et `tests/test_svd_recommendations.py` : les tests dépendant du volume/contenu réel (recherche du mot "Aliène", pipeline SVD ≥50 candidats) skip automatiquement si la DB pointée a moins de 10 émissions (mini-DB) — sinon ils auraient cassé la CI en permanence puisque `.github/workflows/ci.yml` ne positionne jamais `LMELP_DB_PATH` et utilise donc l'asset embarqué par défaut.

## Effet de bord de test à noter

Pour forcer la recopie de l'asset `lmelp.db` par Room (pas de bump de version Room ici), il faut `adb uninstall` avant réinstall (règle déjà documentée dans CLAUDE.md). **Mais `adb uninstall` efface tout le stockage privé de l'app**, pas seulement `lmelp.db` — y compris le DataStore Preferences (`user_prefs`, clé `pinned_reading` dans `UserPreferencesRepository.kt`) qui stocke le livre "en cours de lecture" épinglé par l'utilisateur. Perdu par erreur pendant ce test sur le device de l'auteur. Le vrai mécanisme de mise à jour en usage normal (bouton "Vérifier les mises à jour" → `applyUpdate` → `DatabaseFileReplacer`) ne fait pas `adb uninstall` et ne touche que `lmelp.db` — il n'efface pas ce statut. Seul le geste de test développeur (désinstall forcé) cause la perte.

## Nature de ce travail — pas de TDD au départ, puis TDD complet

L'ADR (partie recherche/décision) n'appelait pas de TDD. Mais l'utilisateur a ensuite demandé d'implémenter la mini-DB dans la même issue plutôt que de la renvoyer à une issue de suivi — cette seconde partie a suivi un TDD classique (RED tests d'abord, y compris pour le bug de version découvert en test device).

## Point d'apprentissage (déjà noté en mémoire long terme séparément)

`awesome-nav` (plugin mkdocs) n'auto-découvre pas les nouvelles pages une fois qu'un `.nav.yml` existe déjà dans un dossier avec des entrées manuelles — il faut ajouter l'entrée explicitement. Correction faite par l'utilisateur sur `docs/dev/.nav.yml` (ajout de `adr/0002-distribution-app-mini-db-embarquee.md`). Mémoire long terme : `mkdocs_nav_yml_explicit.md` dans le système de mémoire globale Claude.

## Fichiers modifiés (non encore commités au moment de cette note)

- `docs/dev/adr/0002-distribution-app-mini-db-embarquee.md` (nouveau)
- `docs/dev/adr/README.md` (ajout entrée liste)
- `docs/dev/.nav.yml` (ajout entrée nav explicite)
- `scripts/build_mini_db.py` (nouveau)
- `tests/test_build_mini_db.py` (nouveau, 15 tests)
- `tests/test_lmelp_db_integrity.py` (TestFraicheurDB/TestDonneesCalibr supprimées, TestTailleMinimale ajoutée)
- `tests/test_fts5_accent_search.py`, `tests/test_svd_recommendations.py` (skip auto si mini-DB)
- `app/src/main/assets/lmelp.db` régénérée en mini-DB (4.4 Mo → ~208 Ko, 3 émissions)
- Deux commentaires postés sur l'issue #119 (spec initiale + spec technique mini-DB).

## Suite

- Commit, push, vérif CI/CD, PR — pas encore faits à ce stade de la session.
