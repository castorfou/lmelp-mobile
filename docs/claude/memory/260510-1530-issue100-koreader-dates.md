---
name: Issue #100 — KOReader dates dans Calibre
description: Utiliser ko_finish/ko_start KOReader pour date_lecture et date_debut_lecture, avec conversion UTC→Europe/Paris
type: project
---

## Contexte

Issue #100 : utiliser les colonnes KOReader dans Calibre pour les dates de lecture, plutôt que le parsing regex du champ Commentaire.

## Colonnes KOReader dans Calibre

KOReader (plugin Calibre) ajoute des colonnes custom :
- `custom_column_7` : `ko_start` (Date KOReader Started)
- `custom_column_8` : `ko_finish` (Date KOReader Finished)

Les IDs des colonnes sont récupérés dynamiquement depuis `custom_columns` (label = `ko_start` / `ko_finish`).

## Bug UTC → heure locale

**Problème critique** : KOReader stocke les dates en UTC. Les dates arrondies à minuit heure locale sont stockées à **22h00 UTC** (été, UTC+2) ou **23h00 UTC** (hiver, UTC+1). Extraire naïvement la partie date donne le jour précédent.

**Fix** : `extract_date_koreader()` convertit le datetime UTC en `Europe/Paris` via `zoneinfo.ZoneInfo` avant d'extraire la date. Utilise `datetime.fromisoformat()` + `dt.astimezone()`.

**Exemple concret** :
- `ko_finish = "2026-05-08 22:00:00+00:00"` → date locale = **2026-05-09** ✓
- Extraction naïve → `2026-05-08` ✗

## Logique d'import dans le script

Dans `import_calibre_data()` et `build_calibre_hors_masque_table()` :
1. `date_lecture` = `extract_date_koreader(ko_finish)` si disponible, sinon `extract_date_lecture(commentaire)`
2. `date_debut_lecture` = `extract_date_koreader(ko_start)` si disponible, sinon `None`

## Vitesse de lecture (PalmaresRepository)

`calculerVitesse()` et `getJoursLecturePourLivre()` :
- Si `curr.dateDebutLecture` disponible → `jours = calculerJoursEntre(dateDebutLecture, dateLecture)`
- Sinon fallback → `jours = calculerJoursEntre(prev.dateLecture, curr.dateLecture)`

**Why:** `date_debut_lecture` donne le vrai début de lecture (KOReader), plus précis que la date de fin du livre précédent.

## Schéma Android (Room version 6 → 7)

Champs ajoutés :
- `PalmaresEntity` : `date_debut_lecture TEXT`
- `CalibreHorsMasqueEntity` : `date_debut_lecture TEXT`
- `MonPalmaresRow`, `PalmaresFiltreAvecUrlRow` : `dateDebutLecture`
- `LivreAvecCalibreRow` : `dateDebutLecture`
- `LivreDetailUi`, `MonPalmaresItemUi` : `dateDebutLecture`

Requêtes SQL mises à jour : `getMonPalmares()`, `getMonPalmaresParDate()`, `getPalmaresFiltresAvecUrl()`, `getLivreAvecCalibreById()`.

## Protection contre désynchronisation Room version

**Bug rencontré** : `LmelpDatabase.kt version=7` mais `PRAGMA user_version = 6` dans le script → Room détruit les tables → app vide.

**Protections ajoutées** :

1. `ROOM_VERSION = 7` comme constante dans `scripts/export_mongo_to_sqlite.py`
2. `_check_room_version_consistency()` appelée au début de `main()` : lit `LmelpDatabase.kt` via regex et échoue si discordance → **bloque l'export avant de toucher à la DB**
3. `TestVersionConsistance` dans `tests/test_lmelp_db_integrity.py` : vérifie `ROOM_VERSION == LmelpDatabase.kt version == lmelp.db user_version`
4. Hook pre-commit `room-version-consistency` dans `.pre-commit-config.yaml`

**Règle** : quand on incrémente `version=N` dans `LmelpDatabase.kt`, toujours mettre à jour `ROOM_VERSION` dans le script **avant** de regénérer `lmelp.db`.

## Autres corrections dans cette session

- `test_export_date_posterieure_a_derniere_emission_mongo` : comparaison par `.date()` (pas datetime) pour éviter l'échec quand export et émission sont le même jour mais à des heures différentes
- `.gitignore` : ajout de `*.db-shm` et `*.db-wal` (fichiers WAL SQLite non versionnés)
- `getLivresAvecCalibreByEmission()` : `@SuppressWarnings(RoomWarnings.CURSOR_MISMATCH)` car la requête ne retourne pas `date_lecture`/`date_debut_lecture` (non nécessaire pour l'affichage d'une émission)
