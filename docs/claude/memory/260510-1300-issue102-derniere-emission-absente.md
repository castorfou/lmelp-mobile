# Issue #102 — Dernière émission absente de l'app

## Contexte

L'émission du 12 avril 2026 (« Le Masque au Quai du Polar à Lyon ») n'apparaissait pas dans l'app, alors que la semaine précédente l'utilisateur avait lancé `lmelp-update-mobile` pour mettre à jour le téléphone.

## Diagnostic

- `app/src/main/assets/lmelp.db` dans le repo git était périmé (export du 2026-04-09)
- L'émission d'avril avait été ajoutée dans MongoDB le 2026-04-13
- Le container Docker `lmelp-export` se connecte à `mongo:27017` (nom de service Docker), mappé sur `localhost:27018` depuis l'hôte — **c'est la même instance MongoDB**, pas deux bases distinctes

## Cause racine — hypothèse non confirmée

Le script `lmelp-update-mobile` pousse la DB directement dans le répertoire privé de l'app via ADB. Room valide le `PRAGMA user_version` avant d'accepter la nouvelle DB. Si la `user_version` était identique à celle déjà en place, Room ignorait la nouvelle DB et conservait l'ancienne en cache.

`user_version` = timestamp Unix de l'export → elle change à chaque export. La semaine dernière, le container a peut-être utilisé une DB en cache (non regénérée), donc même `user_version` → Room l'a ignorée.

**Piste à creuser** : vérifier si Room rejette une DB poussée via ADB quand `user_version` est identique.

## Ce qui a été fait

### 1. Regénération de `lmelp.db`
Lancé manuellement dans le devcontainer :
```bash
python scripts/export_mongo_to_sqlite.py --force
```
Résultat : 175 émissions (était 174), 230 épisodes, export 2026-05-10.

### 2. Nouveaux tests de fraîcheur — `tests/test_lmelp_db_integrity.py`

Nouvelle classe `TestFraicheurDB` avec 2 tests qui nécessitent MongoDB local (skip automatique si indisponible) :

- `test_toutes_emissions_mongo_dans_sqlite` : toutes les émissions MongoDB doivent être dans SQLite
- `test_export_date_posterieure_a_derniere_emission_mongo` : `export_date >= MAX(emissions.date)` dans MongoDB

**Pattern fixture MongoDB dans les tests** :
```python
@pytest.fixture(scope="class")
def mongo_db(self):
    pymongo = pytest.importorskip("pymongo")
    mongo_uri = os.environ.get("LMELP_MONGO_URI", "mongodb://localhost:27018")
    try:
        client = pymongo.MongoClient(mongo_uri, serverSelectionTimeoutMS=2000)
        client.server_info()
        yield client["masque_et_la_plume"]
        client.close()
    except Exception:
        pytest.skip(f"MongoDB inaccessible : {mongo_uri}")
```

### 3. Tests SVD robustes — `tests/test_svd_recommendations.py`

`test_lievre_svd_proche_desktop` remplacé par 3 tests génériques car le livre "Le lièvre" avait été lu et noté dans Calibre → il sortait légitimement des candidats SVD (déjà vu = exclu des recommandations) :

- `test_svd_produit_des_candidats` : au moins 50 candidats
- `test_svd_scores_dans_echelle_notes` : scores dans [1, 10]
- `test_svd_scores_plausibles` : ≥ 50% des scores >= 5.0

**Règle générale** : ne jamais écrire un test SVD qui référence un livre spécifique — un livre peut entrer dans Calibre à tout moment et sortir des candidats.

### 4. Ce qui a été revertï

La modification de `scripts/lmelp-update-mobile.sh` (copier la DB depuis le container vers le repo git) a été **revertée** — elle était basée sur une mauvaise analyse (on pensait que le container et le MongoDB local étaient des instances distinctes).

## Leçons

- Le `PRAGMA user_version` dans `lmelp.db` est un timestamp Unix → il change à chaque export → Room accepte toujours la nouvelle DB si elle a été fraîchement générée
- Le container `lmelp-export` génère la DB dans `/tmp/lmelp.db` (visible dans les logs)
- Les tests MongoDB doivent toujours utiliser `pytest.importorskip` + `serverSelectionTimeoutMS=2000` + `pytest.skip` sur exception pour être robustes en CI
- Ne pas fixer un test sur un livre spécifique qui peut entrer dans Calibre
