# Issue #42 — Palmarès : le tag "lu" n'apparaît plus

## Résumé

Le filtre "Lus" du palmarès retournait 0 résultats et aucun ✓ n'était affiché,
comme si l'utilisateur n'avait rien lu dans Calibre.

## Cause racine

Lors de l'issue #37 (ajout du champ `section` dans `avis`), `lmelp.db` a été
regénéré via le script d'export **sans l'option `--calibre-db`**. Résultat :
`calibre_in_library = 0` et `calibre_lu = 0` pour les 871 livres du palmarès.

Diagnostic rapide :
```bash
sqlite3 app/src/main/assets/lmelp.db \
  "SELECT COUNT(*), SUM(calibre_in_library), SUM(calibre_lu) FROM palmares;"
# Attendu : 871|163|116
# Symptôme bug : 871|0|0
```

## Fix

### 1. Regénérer lmelp.db avec les données Calibre

```bash
python scripts/export_mongo_to_sqlite.py \
  --mongo-uri mongodb://localhost:27018 \
  --output app/src/main/assets/lmelp.db \
  --calibre-db "/home/vscode/Calibre Library/metadata.db" \
  --force
```

### 2. Réinstaller l'app (Room met en cache la base locale)

Room copie `lmelp.db` depuis les assets **une seule fois** au premier lancement.
Si la base a changé sans changement de version Room, il faut :
- Désinstaller l'app : `adb uninstall com.lmelp.mobile`
- Réinstaller : `./gradlew installDebug`

## Détrompeur CI/CD (TDD)

Fichier : `tests/test_lmelp_db_integrity.py`

6 tests pytest qui vérifient l'intégrité de `lmelp.db` embarqué :
- Table `palmares` présente et non vide
- Au moins 1 livre avec `calibre_in_library = 1`
- Au moins 1 livre avec `calibre_lu = 1`
- Pas de livre avec `calibre_lu=1` et `calibre_in_library=0` (incohérence)
- Ratio Calibre ≥ 5 % (seuil minimal de cohérence)

Ces tests tournent automatiquement via `pytest tests/` dans la CI (`ci.yml`).
Ils **échouent** dès qu'un export sans `--calibre-db` est commité → PR bloquée.

Le chemin de la base est configurable via `LMELP_DB_PATH` (env var).

## Environnement local

- MongoDB : port **27018** (pas 27017)
- Calibre : `/home/vscode/Calibre Library/metadata.db` (975 livres)
- Résultat après fix : 163 livres dans Calibre, 116 marqués lus

## Leçon retenue

Toujours utiliser la commande d'export complète (voir CLAUDE.md section
"Export données — règle critique"). Ne jamais omettre `--calibre-db`.
