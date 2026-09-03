# Workflow de migration de schéma Room

Procédure à suivre chaque fois qu'un changement de code nécessite d'incrémenter le schéma Room (`ROOM_VERSION` / `version=N` dans `LmelpDatabase.kt`) — ajout, suppression ou modification d'un champ dans une `@Entity`.

!!! warning "Rare, pas systématique"
    La grande majorité des évolutions de l'app (nouvel écran, nouvelle logique métier, correction de bug) **ne touchent pas** au schéma Room et n'ont donc pas besoin de ce workflow. Il ne s'applique que lorsque la structure d'une table change réellement.

## Pourquoi cette procédure existe

Depuis l'issue [#132](https://github.com/castorfou/lmelp-mobile/issues/132), chaque build de l'app télécharge ses mises à jour de données depuis une GitHub Release taguée `data-v{N}`, où `N` est son propre `PRAGMA user_version` local (voir [ADR 0001](adr/0001-separation-maj-appli-donnees.md)). Le pipeline NAS publie toujours vers le tag dérivé du schéma courant du script d'export.

**Conséquence directe** : tant qu'une release `data-v{N}` (nouveau schéma) n'existe pas, une app buildée pour ce nouveau schéma ne trouve aucune donnée à télécharger (404 GitHub) — ce qui est le comportement voulu (pas de mise à jour proposée plutôt qu'un fichier incompatible), mais qui rend le test local impossible sans publier manuellement cette release au préalable.

## Procédure complète

### 1. Développer le changement de schéma

Suivre la procédure déjà documentée plus haut dans ce fichier CLAUDE.md ("Erreur fréquente : Room cannot verify the data integrity") : incrémenter `version` dans `LmelpDatabase.kt` **et** `ROOM_VERSION` dans `scripts/export_mongo_to_sqlite.py`, garder les deux synchronisés (`_check_room_version_consistency()` et le hook pre-commit "Room version consistency" bloquent sinon).

### 2. Régénérer la mini-DB embarquée (ADR 0002)

`app/src/main/assets/lmelp.db` doit rester un extrait minimal (voir ADR [0002](adr/0002-distribution-app-mini-db-embarquee.md)) mais au **nouveau** schéma :

```bash
source /home/vscode/.venv/bin/activate
python scripts/export_mongo_to_sqlite.py --output /tmp/lmelp_complete.db --force
python scripts/build_mini_db.py --source /tmp/lmelp_complete.db --output app/src/main/assets/lmelp.db --nb-emissions 3
```

Vérifier : `sqlite3 app/src/main/assets/lmelp.db "PRAGMA user_version;"` doit afficher le nouveau `ROOM_VERSION`.

### 3. Publier manuellement une GitHub Release `data-v{N}` pour tester

En conditions réelles (avant que le pipeline NAS ne republie automatiquement après merge sur `main`), publier soi-même la release au nouveau tag pour pouvoir tester le mécanisme de mise à jour de bout en bout :

```bash
source /home/vscode/.venv/bin/activate

# 1. Récupérer le nouveau ROOM_VERSION
ROOM_VERSION=$(python scripts/export_mongo_to_sqlite.py --print-room-version)

# 2. Générer une base complète au nouveau schéma (nécessite MongoDB + Calibre configurés,
#    voir scripts/.env) — IMPORTANT : nommer le fichier local exactement "lmelp.db",
#    pas un nom arbitraire (voir piège ci-dessous)
python scripts/export_mongo_to_sqlite.py --output /tmp/lmelp.db --force

# 3. Générer metadata.json
python scripts/generate_data_release_metadata.py --db /tmp/lmelp.db --output /tmp/metadata.json

# 4. Publier la release
gh release create "data-v${ROOM_VERSION}" \
  --repo castorfou/lmelp-mobile \
  --title "lmelp-mobile — données (schéma v${ROOM_VERSION})" \
  --notes "Publication manuelle pour test du nouveau schéma." \
  /tmp/lmelp.db /tmp/metadata.json
```

⚠️ **Piège déjà rencontré (issue #132)** : `gh release upload data-v8 fichier_local.db#Label` — le suffixe `#Label` après `gh release upload` définit le **label affiché** dans l'UI GitHub, pas le `name` de l'asset. `OkHttpGitHubReleaseApi.fetchAssetUrls()` recherche l'asset par son `name` exact (`lmelp.db`), qui suit le **nom du fichier local uploadé**, pas le label. Un fichier local nommé différemment (ex. `lmelp_complete.db`) sera publié sous ce nom et l'app affichera "Erreur : lmelp.db absent des assets de la release" même si l'asset existe bel et bien. **Toujours renommer le fichier local en `lmelp.db` avant `gh release upload`.**

### 4. Builder et installer l'app avec la mini-DB au nouveau schéma

```bash
adb uninstall com.lmelp.mobile
scripts/build.sh && scripts/deploy.sh
```

(Désinstallation obligatoire pour forcer la recopie de l'asset par Room, voir plus haut dans ce fichier — un simple `adb install -r` ne suffit pas.)

### 5. Déclencher la mise à jour et vérifier

Dans l'écran **À propos** de l'app : bouton **Vérifier les mises à jour** puis **Mettre à jour**. Si tout fonctionne, l'app télécharge, vérifie le SHA-256 et remplace `lmelp.db`, puis se ferme (redémarrage manuel requis, voir plus haut).

### 6. Après merge sur `main`

Une fois la PR mergée, l'image `ghcr.io/castorfou/lmelp-mobile-export` (buildée automatiquement depuis `Dockerfile.export` de ce repo) intègre le nouveau `ROOM_VERSION`. Au prochain rebuild/pull de cette image par le pipeline NAS, `docker_export_and_publish_release.sh` publiera naturellement vers `data-v{N}` (résolu dynamiquement via `--print-room-version`), écrasant/rafraîchissant la release publiée manuellement à l'étape 3 avec des données réelles à jour. Aucune action manuelle supplémentaire n'est nécessaire côté `docker-lmelp` — le tag suit le code de ce repo.

## Résumé visuel

```
Code : version Room N → N+1
  │
  ├─→ Régénérer mini-DB embarquée (schéma N+1)
  │
  ├─→ Publier manuellement data-v{N+1} sur GitHub (pour tester AVANT le merge)
  │
  ├─→ Build + install app (mini-DB N+1) sur device de test
  │
  ├─→ Déclencher "Vérifier les mises à jour" → doit télécharger data-v{N+1}
  │
  └─→ Merge sur main → pipeline NAS republie data-v{N+1} automatiquement
       au prochain export réel (écrase la publication manuelle de test)
```
