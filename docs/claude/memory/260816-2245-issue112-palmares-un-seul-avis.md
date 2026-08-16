# Issue #112 — Livres à un seul avis absents de Mon Palmarès (sans couverture, non cliquables)

## Symptôme

Dans l'écran Mon Palmarès, deux livres lus et notés dans Calibre ("La Petite
fasciste" de Jérôme Leroy, "L'éternité n'est pas de trop" de François Cheng)
s'affichaient sans couverture, non cliquables, et disparaissaient de **tous**
les tris (Note, Date lecture, Vitesse) — alors qu'ils étaient bien lus et
notés côté Calibre.

## Cause racine : le seuil `nb_avis >= 2` de `compute_palmares()` propage un biais à deux endroits

Ces deux livres n'avaient chacun **qu'un seul avis critique noté** en base
MongoDB. `compute_palmares()`
(`scripts/export_mongo_to_sqlite.py:478-499`) filtrait
`HAVING COUNT(a.id) >= 2` — un livre à 1 seul avis, bien que présent dans
`livres` (donc discuté au Masque), n'entrait jamais dans la table `palmares`.

Conséquence en cascade :
1. `PalmaresDao.getMonPalmares()`/`getMonPalmaresParDate()`
   (`PalmaresDao.kt:93-117`) lisent uniquement `palmares` → invisibles dans
   Mon Palmarès quel que soit le tri, malgré `calibre_lu=1` côté Calibre.
2. `build_calibre_hors_masque_table()` (`export_mongo_to_sqlite.py:727-897`)
   n'exclut que les titres déjà présents dans `palmares` (pas `livres`) → ces
   livres pouvaient être classés à tort "hors Masque" → `livre_id = null`
   côté `MonPalmaresItemUi` → `PalmaresScreen.kt` (`MonPalmaresCard`,
   lignes 237-288) : `isClickable = item.livreId != null` → pas de clic, pas
   de couverture (le bloc `if (isClickable) { BookCoverThumbnail(...) }`
   saute l'affichage).

Ce mécanisme (Masque + hors-Masque fusionnés via `livreId != null`) avait déjà
été documenté lors de l'issue #107
(`260512-1400-issue107-note-masque-mon-palmares.md`), mais sans identifier
que le seuil `nb_avis >= 2` de `palmares` en était la cause profonde pour les
livres à un seul avis.

## Correction : retirer le HAVING plutôt que dupliquer le croisement Calibre

Option écartée : créer une nouvelle table dédiée à Mon Palmarès sans seuil.
Rejetée car `import_calibre_data()` (~150 lignes,
`export_mongo_to_sqlite.py:566-725`) fait déjà tout le croisement Calibre
(lecture/note/dates) en lisant `SELECT livre_id, titre FROM palmares` — dupliquer
cette logique dans une deuxième table aurait été source de bugs.

Solution retenue : **retirer `HAVING COUNT(a.id) >= 2`** de
`compute_palmares()`. Tout livre avec ≥ 1 avis noté entre désormais dans
`palmares`. Le classement **"Palmarès critiques"** reste protégé sans aucun
changement Kotlin car ses requêtes DAO filtrent déjà explicitement
`WHERE nb_avis >= 2` (`PalmaresDao.kt:50,53,58,71,84`) — vérifié avant
d'agir, ces filtres existaient déjà. `getMonPalmares()`/
`getMonPalmaresParDate()` n'ont pas ce filtre, comportement voulu.
`build_calibre_hors_masque_table()` n'a pas eu besoin d'être modifiée : une
fois ces livres dans `palmares`, le filtre existant `palmares_norms`
(ligne 819) les exclut automatiquement de `calibre_hors_masque`.

Effet de bord neutre/positif non testé explicitement : `compute_recommendations()`
(SVD, lit `palmares.calibre_rating`) gagne quelques notes Calibre
supplémentaires en entrée.

## Tests TDD

Nouveau fichier `tests/test_compute_palmares.py` (pattern : DB SQLite en
mémoire + import direct du module `export_mongo_to_sqlite`, sans mock
`sqlite3.connect` car `compute_palmares()` ne touche pas Calibre) :
- livre à 1 avis noté → entre dans `palmares`
- livre à 2 avis notés → entre toujours (non-régression)
- livre sans avis noté (`note IS NULL`) → toujours absent
- `rank` cohérent sur l'ensemble incluant les livres à 1 avis

## Piège opérationnel rencontré pendant le test terrain : `deploy.sh` ne force pas la recopie de la DB

Après régénération de `lmelp.db` et premier test avec `scripts/build.sh &&
scripts/deploy.sh`, les livres n'apparaissaient toujours pas comme lus sur
le téléphone. Cause : `scripts/deploy.sh` fait `adb install -r` (mise à jour
d'app déjà installée), qui **ne déclenche pas** la recopie de l'asset
`lmelp.db` par Room (`LmelpDatabase.kt:69`, `createFromAsset("lmelp.db")` ne
recopie que si le fichier local `/data/data/.../databases/lmelp.db` est
absent ou si la version Room change — `version = 7` inchangée ici). C'est le
même mécanisme que documenté dans `CLAUDE.md` ("Après régénération de
lmelp.db"). Il a fallu `adb uninstall com.lmelp.mobile` avant de relancer
`build.sh && deploy.sh` pour que le fix soit visible. **À retenir** :
`deploy.sh` seul ne suffit jamais pour valider un changement de
`lmelp.db` sur un device où l'app est déjà installée — toujours
désinstaller d'abord.

## À retenir pour de futures precalculations dans `export_mongo_to_sqlite.py`

Un seuil `HAVING`/`WHERE` mis dans une fonction `compute_*`/`build_*` qui
peuple une table **source** (comme `palmares`) peut affecter silencieusement
tous les écrans qui lisent cette table, même ceux qui n'ont pas besoin de ce
seuil. Préférer : filtrer au plus près du besoin (au niveau de la requête
DAO/écran qui a réellement besoin du seuil), et garder la table source aussi
complète que possible. Avant de changer un seuil de filtrage, `grep` toutes
les requêtes lisant la table concernée (`FROM <table>`/`JOIN <table>`) pour
vérifier qu'aucune ne dépend implicitement de l'ancien filtrage.
